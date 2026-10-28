package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import damso.gateway.application.port.in.ClassifyCrisisGateway;
import damso.gateway.application.port.in.ClassifyCrisisGateway.CrisisOutcome;
import damso.gateway.application.port.in.RefineInquiryGateway;
import damso.gateway.application.port.in.RefineInquiryGateway.RefineOutcome;
import damso.gateway.application.port.in.RefineInquiryGateway.Refined;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;
import damso.inquiry.application.port.in.CrisisClassificationUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryRefinementPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.application.port.out.RefinementAttemptPort;
import damso.inquiry.application.port.out.RefinementJobPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryRefinement;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;
import damso.inquiry.domain.RefinementAttempt;
import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.RefinementJobKind;
import damso.inquiry.domain.RefinementOutcomeCode;
import damso.inquiry.domain.RefinementStatus;
import damso.inquiry.domain.RefinementTrigger;
import damso.notification.application.port.in.NotifyInquiryUseCase;
import damso.notification.application.port.in.NotifyInquiryUseCase.NotificationDecision;

/**
 * 정리·분류 작업을 실행한다.
 * - 커밋 직후 {@link RefinementDispatchTrigger}가 {@link #dispatchOne}으로 바로 실행하고, {@link #dispatchDue}가 10초마다
 *   못 끝낸 작업을 실행하는 안전망이다. 작업은 하나씩 짧은 트랜잭션에서 임대(RUNNING, 60초)하고 외부 AI는 트랜잭션 밖에서 부른다
 *   — 여러 개를 한꺼번에 임대하면 앞의 작업을 처리하는 동안 뒤의 임대가 끝난다(자체 코드 리뷰).
 * - 결과를 반영하기 전에 아직 내가 잡은 작업인지(임대가 그대로인지) 확인한다. 임대가 끝나 다른 실행기가 다시 잡았으면 반영하지 않는다.
 * - 반영이 낙관적 락 충돌로 실패하면 한 번 다시 반영한다. 같은 작업이 {@value #MAX_CLAIMS}번을 넘게 확보되면(반영이 계속 실패)
 *   외부 AI를 다시 부르지 않고 실패로 끝낸다 — 끝없이 다시 실행되며 외부로 글을 보내지 않게.
 * - 일반 정리: 실패하면 같은 실행 안에서 곧바로 1회 자동 재시도, 그래도 실패면 "원문 확인 필요". 교사 재시도로 다시 연 작업은
 *   자동 재시도 없이 1회. 결과(정리 결과·단계·시도 기록·알림 요청)는 한 트랜잭션으로 반영한다.
 * - 일반 문의 알림은 정리가 확정될 때(성공·원문 확인 필요) 한 번만 요청한다. 교사 재시도의 결과로는 요청하지 않는다.
 * - 위기 분류: 한 번만 시도하고, 성공하면 F2 연결 지점(CrisisClassificationUseCase)으로 넘긴다. 실패하면 기본 안내가 그대로다.
 * - 로그에는 작업 UUID와 결과 코드만 남긴다. 본문·정리 결과·이름은 남기지 않는다.
 */
@Service
public class RefinementRunner {

	private static final Logger log = LoggerFactory.getLogger(RefinementRunner.class);

	static final int BATCH_SIZE = 20;

	/** 같은 작업을 이보다 많이 확보하면 반영이 계속 실패하는 것으로 보고 포기한다. */
	static final int MAX_CLAIMS = 5;

	private final RefinementJobPort jobs;

	private final RefinementAttemptPort attempts;

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryRefinementPort refinements;

	private final RosterAssembler rosters;

	private final RefineInquiryGateway refineGateway;

	private final ClassifyCrisisGateway crisisGateway;

	private final CrisisClassificationUseCase crisisClassification;

	private final NotifyInquiryUseCase notifications;

	private final Clock clock;

	private final TransactionTemplate transaction;

	private final Duration lease;

	public RefinementRunner(RefinementJobPort jobs, RefinementAttemptPort attempts, InquiryPort inquiries,
			InquiryStageHistoryPort histories, InquiryRefinementPort refinements, RosterAssembler rosters,
			RefineInquiryGateway refineGateway, ClassifyCrisisGateway crisisGateway,
			CrisisClassificationUseCase crisisClassification, NotifyInquiryUseCase notifications, Clock clock,
			PlatformTransactionManager transactionManager, @Value("${damso.refinement.lease:60s}") Duration lease) {
		this.jobs = jobs;
		this.attempts = attempts;
		this.inquiries = inquiries;
		this.histories = histories;
		this.refinements = refinements;
		this.rosters = rosters;
		this.refineGateway = refineGateway;
		this.crisisGateway = crisisGateway;
		this.crisisClassification = crisisClassification;
		this.notifications = notifications;
		this.clock = clock;
		// 커밋 직후 콜백(AFTER_COMMIT)에서 불려도 이미 끝난 트랜잭션에 얹히지 않도록 항상 새 트랜잭션으로 읽고 반영한다
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.lease = lease;
	}

	/** 안전망. 실행할 때가 된 작업을 하나씩 확보해 처리하고(최대 {@value #BATCH_SIZE}개) 처리한 개수를 돌려준다. */
	@Scheduled(fixedDelayString = "${damso.refinement.dispatch-interval:10s}",
			initialDelayString = "${damso.refinement.dispatch-interval:10s}")
	public int dispatchDue() {
		int processed = 0;
		while (processed < BATCH_SIZE) {
			Optional<RefinementJob> claimed;
			try {
				claimed = jobs.claimNextDue(clock.instant(), lease);
			}
			catch (RuntimeException ex) {
				log.warn("정리 작업 조회 실패 (type={})", ex.getClass().getSimpleName());
				break;
			}
			if (claimed.isEmpty()) {
				break;
			}
			process(claimed.get());
			processed++;
		}
		return processed;
	}

	/** 커밋 직후 즉시 실행. 이미 처리됐거나 다른 곳이 잡은 작업이면 아무것도 하지 않는다. */
	public void dispatchOne(UUID jobId) {
		jobs.claimOne(jobId, clock.instant(), lease).ifPresent(this::process);
	}

	private void process(RefinementJob job) {
		try {
			if (job.getClaimCount() > MAX_CLAIMS) {
				giveUp(job);
			}
			else if (job.getKind() == RefinementJobKind.REFINE) {
				refine(job);
			}
			else {
				classifyCrisis(job);
			}
		}
		catch (RuntimeException ex) {
			// 임대가 만료되면 다시 가져가 실행한다(작업은 DB에 그대로 남는다). 반복되면 MAX_CLAIMS에서 멈춘다
			log.warn("정리 작업 처리 중 오류 (job={}, type={})", job.getId(), ex.getClass().getSimpleName());
		}
	}

	/** 외부 AI에 넘길 재료. 문의 본문과 명단 — 이 실행 동안만 메모리에 있다. */
	private record Input(String content, Roster roster) {

		@Override
		public String toString() {
			return "Input[" + roster + "]";
		}

	}

	private Input read(RefinementJob job) {
		return transaction.execute(status -> {
			Inquiry inquiry = inquiries.findInquiryById(job.getInquiryId()).orElseThrow();
			return new Input(inquiry.getContent().reveal(), rosters.assemble(inquiry.getClassroomId(),
					Set.of(inquiry.getParentId(), inquiry.getTeacherId())));
		});
	}

	private void refine(RefinementJob job) {
		Input input = read(job);
		boolean teacherRun = job.isTeacherRetryUsed();
		List<Attempt> tried = new ArrayList<>();

		RefineOutcome outcome = attempt(input);
		tried.add(new Attempt(teacherRun ? RefinementTrigger.TEACHER : RefinementTrigger.AUTO, outcome));
		if (!outcome.succeeded() && !teacherRun) {
			outcome = attempt(input);
			tried.add(new Attempt(RefinementTrigger.AUTO_RETRY, outcome));
		}
		RefineOutcome last = outcome;
		if (applyWhileHeld(job, held -> confirm(held, last, tried, !teacherRun))) {
			log.info("정리 작업 완료 (job={}, result={})", job.getId(), last.succeeded() ? "REFINED" : "NEEDS_ORIGINAL");
		}
	}

	private record Attempt(RefinementTrigger trigger, RefineOutcome outcome) {
	}

	private RefineOutcome attempt(Input input) {
		try {
			return refineGateway.refine(input.content(), input.roster());
		}
		catch (RuntimeException ex) {
			log.warn("게이트웨이 오류 (type={})", ex.getClass().getSimpleName());
			return RefineOutcome.failed(GatewayFailure.NO_RESPONSE);
		}
	}

	/**
	 * 반영을 한 트랜잭션으로 한다. 반영 직전에 아직 이 임대로 잡은 작업인지 확인하고, 아니면(임대가 끝나 다른 실행기가 잡음) 아무것도
	 * 바꾸지 않는다. 낙관적 락 충돌이면 한 번 다시 반영한다.
	 * @return 반영했으면 true
	 */
	private boolean applyWhileHeld(RefinementJob claimed, Consumer<RefinementJob> apply) {
		for (int round = 0; round < 2; round++) {
			try {
				Boolean applied = transaction.execute(status -> {
					RefinementJob held = jobs.findById(claimed.getId()).orElseThrow();
					if (!held.isHeldBy(claimed.getLockedUntil())) {
						return false;
					}
					apply.accept(held);
					return true;
				});
				if (!Boolean.TRUE.equals(applied)) {
					log.warn("임대가 끝나 다른 실행기가 잡은 작업이라 반영하지 않음 (job={})", claimed.getId());
				}
				return Boolean.TRUE.equals(applied);
			}
			catch (OptimisticLockingFailureException ex) {
				log.info("정리 결과 반영 충돌, 다시 반영 (job={}, round={})", claimed.getId(), round + 1);
			}
		}
		throw new IllegalStateException("refinement result could not be applied");
	}

	/** 정리 결과를 반영한다: 결과·제목·정리 상태·단계·작업 상태·시도 기록, 그리고 (첫 확정이면) 일반 알림 요청. */
	private void confirm(RefinementJob job, RefineOutcome outcome, List<Attempt> tried, boolean notify) {
		Instant now = clock.instant();
		Inquiry inquiry = inquiries.findInquiryById(job.getInquiryId()).orElseThrow();
		for (Attempt attempt : tried) {
			job.countAttempt();
			attempts.append(RefinementAttempt.of(job.getId(), attempt.trigger(), codeOf(attempt.outcome().failure()),
					now));
		}
		if (outcome.succeeded()) {
			Refined refined = outcome.result();
			if (refinements.findByInquiry(inquiry.getId()).isEmpty()) {
				refinements.save(InquiryRefinement.of(inquiry.getId(), refined.facts(), refined.requests(),
						refined.emotionLevel(), refined.emotionLabels(), now));
			}
			if (inquiry.applyRefinement(refined.title())) {
				histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.REFINING, now));
			}
			job.markDone();
		}
		else {
			if (inquiry.getRefinementStatus() == RefinementStatus.PENDING) {
				inquiry.markNeedsOriginal();
			}
			job.markFailed();
		}
		if (notify) {
			NotificationDecision decision = notifications.requestGeneral(inquiry.getId(), inquiry.getTeacherId());
			if (decision == NotificationDecision.DEFERRED) {
				inquiry.markNotificationDeferred();
			}
		}
		inquiries.saveInquiry(inquiry);
		jobs.save(job);
	}

	/** 결과 코드. null(실패 없음)이면 성공이다. */
	private static RefinementOutcomeCode codeOf(GatewayFailure failure) {
		if (failure == null) {
			return RefinementOutcomeCode.SUCCESS;
		}
		return switch (failure) {
			case MASKING_FAILED -> RefinementOutcomeCode.MASKING_FAILED;
			case NO_RESPONSE -> RefinementOutcomeCode.NO_RESPONSE;
			case INVALID_OUTPUT -> RefinementOutcomeCode.INVALID_OUTPUT;
		};
	}

	private void classifyCrisis(RefinementJob job) {
		Input input = read(job);
		CrisisOutcome outcome;
		try {
			outcome = crisisGateway.classify(input.content(), input.roster());
		}
		catch (RuntimeException ex) {
			log.warn("게이트웨이 오류 (type={})", ex.getClass().getSimpleName());
			outcome = CrisisOutcome.failed(GatewayFailure.NO_RESPONSE);
		}
		CrisisOutcome last = outcome;
		boolean applied = applyWhileHeld(job, held -> {
			held.countAttempt();
			attempts.append(RefinementAttempt.of(held.getId(), RefinementTrigger.AUTO, codeOf(last.failure()),
					clock.instant()));
			if (last.succeeded()) {
				crisisClassification.apply(held.getInquiryId(), last.crisisType());
				held.markDone();
			}
			else {
				held.markFailed();
			}
			jobs.save(held);
		});
		if (applied) {
			log.info("위기 분류 작업 완료 (job={}, result={})", job.getId(),
					last.succeeded() ? "CLASSIFIED" : "UNCLASSIFIED");
		}
	}

	/**
	 * 반영이 거듭 실패해 확보 횟수 상한을 넘은 작업을 외부 AI 없이 실패로 끝낸다. 일반 정리는 "원문 확인 필요"로 확정하고(첫 확정이면
	 * 교사에게 알린다), 위기 분류는 기본 안내로 남긴다.
	 */
	private void giveUp(RefinementJob job) {
		boolean applied;
		if (job.getKind() == RefinementJobKind.REFINE) {
			boolean teacherRun = job.isTeacherRetryUsed();
			applied = applyWhileHeld(job,
					held -> confirm(held, RefineOutcome.failed(GatewayFailure.NO_RESPONSE), List.of(), !teacherRun));
		}
		else {
			applied = applyWhileHeld(job, held -> {
				held.markFailed();
				jobs.save(held);
			});
		}
		if (applied) {
			log.warn("정리 작업 포기, 반영이 거듭 실패함 (job={}, claims={})", job.getId(), job.getClaimCount());
		}
	}

}
