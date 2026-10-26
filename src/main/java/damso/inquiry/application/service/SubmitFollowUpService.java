package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.common.crypto.CodeHasher;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.TooManyAttemptsException;
import damso.inquiry.application.port.in.SubmitFollowUpUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.application.port.out.RefinementJobPort;
import damso.inquiry.application.port.out.UrgentExpressionSource;
import damso.inquiry.domain.GuidancePolicy;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryContentPolicy;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;
import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.UrgentRule;
import damso.notification.application.port.in.NotifyInquiryUseCase;
import damso.school.application.port.in.InquiryTargetQuery;
import damso.school.application.port.in.InquiryTargetQuery.ChildTarget;

/**
 * 재문의 제출 (F4, research R5). SubmitInquiryService를 복제하지 않고 같은 순서를 따르는 자매 서비스다:
 * ① 입력 검증 ② 중복 제출 확인 ③ 대상 재확인(스레드 시작 시점으로 고정하지 않고 매번 현재 담임을 다시 본다)
 * ④ 긴급 판정 ⑤ 일반이면 1분 제한(최초 문의와 같은 버킷 공유) ⑥ 일반이면 위협·폭언 판정(규칙, 실패하면 표시 없음, spec 005) ⑦ 저장.
 * rootInquiryId는 경로 파라미터로 항상 스레드 루트의 id다(재문의 자신의 id로는 호출할 수 없다).
 * F3 머지 뒤로는 정리·위기 분류도 SubmitInquiryService와 같은 방식으로 작업(아웃박스)만 남긴다(PR #10 리뷰 6번) —
 * 재문의도 그냥 또 다른 Inquiry 행이라 RefinementRunner·가명화 쪽은 고칠 필요가 없다.
 */
@Service
public class SubmitFollowUpService implements SubmitFollowUpUseCase {

	private static final Logger log = LoggerFactory.getLogger(SubmitFollowUpService.class);

	private static final long LOCK_BUSY_RETRY_SECONDS = 1;

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	private final AttemptLimitUseCase attempts;

	private final NotifyInquiryUseCase notifications;

	private final CodeHasher hasher;

	private final Clock clock;

	private final TransactionTemplate transaction;

	private final UrgentRule urgentRule;

	private final ThreatFlagScreener threatScreener;

	private final RefinementJobPort refinementJobs;

	private final ApplicationEventPublisher events;

	public SubmitFollowUpService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets,
			AttemptLimitUseCase attempts, NotifyInquiryUseCase notifications, CodeHasher hasher, Clock clock,
			PlatformTransactionManager transactionManager, UrgentExpressionSource urgentExpressions,
			RefinementJobPort refinementJobs, ApplicationEventPublisher events, ThreatFlagScreener threatScreener) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
		this.attempts = attempts;
		this.notifications = notifications;
		this.refinementJobs = refinementJobs;
		this.events = events;
		this.hasher = hasher;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
		this.urgentRule = new UrgentRule(urgentExpressions.expressions());
		this.threatScreener = threatScreener;
	}

	@Override
	public FollowUpSubmittedResult submit(UUID parentId, UUID rootInquiryId, String content, UUID idempotencyKey) {
		InquiryContentPolicy.validate(content);
		// 같은 스레드에 붙는 메시지라는 것까지 request hash에 담아, 다른 스레드에 같은 키를 재사용하면 CONFLICT가 난다.
		String requestHash = hasher.hash(rootInquiryId + ":" + content);

		return transaction.execute(status -> {
			try {
				inquiries.lockSubmission(parentId, idempotencyKey);
			}
			catch (PessimisticLockingFailureException ex) {
				throw new TooManyAttemptsException(LOCK_BUSY_RETRY_SECONDS);
			}

			Optional<Inquiry> existing = inquiries.findByParentAndKey(parentId, idempotencyKey);
			if (existing.isPresent()) {
				return replay(existing.get(), requestHash);
			}

			// 쓰기 잠금으로 조회한다 — 종료(CloseInquiryService)가 같은 행을 쓰는 동안 이 트랜잭션이 기다리게 해,
			// "검사(stage 확인) 뒤 사용(재문의 저장)" 사이에 종료가 끼어드는 TOCTOU를 막는다(PR #10 리뷰).
			Inquiry root = inquiries.findInquiryByIdForUpdate(rootInquiryId)
					.filter(found -> parentId.equals(found.getParentId()))
					// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 이 경로를 쓸 수 없다(research R1).
					.filter(found -> found.getThreadRootId() == null)
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
			if (root.getStage() == InquiryStage.ANSWERED) {
				throw new BusinessException(ErrorCode.THREAD_CLOSED);
			}

			ChildTarget target = targets.resolveChild(parentId, root.getStudentId());
			if (!target.hasHomeroomTeacher()) {
				throw new BusinessException(ErrorCode.NO_HOMEROOM_TEACHER);
			}

			boolean urgent = urgentRule.isUrgent(content);
			boolean threatFlagged = false;
			if (!urgent) {
				enforceSubmitLimit(parentId);
				// 긴급이 먼저다 — 긴급 재문의는 판정하지 않는다(spec 005 FR-005a). 제한에 걸린 요청도 판정하지 않는다.
				threatFlagged = threatScreener.screenSafely(content);
			}
			return persist(root.getId(), parentId, target, content, urgent, threatFlagged, idempotencyKey,
					requestHash);
		});
	}

	private void enforceSubmitLimit(UUID parentId) {
		long waitSeconds;
		try {
			waitSeconds = attempts.reserveInquirySubmit(parentId);
		}
		catch (BusinessException ex) {
			if (ex.errorCode() != ErrorCode.INTERNAL_ERROR) {
				throw ex;
			}
			log.warn("재문의 제출 제한 저장소를 쓸 수 없어 제한 확인을 건너뜁니다 (kind=INQUIRY_SUBMIT)");
			return;
		}
		if (waitSeconds > 0) {
			throw new TooManyAttemptsException(waitSeconds);
		}
	}

	private FollowUpSubmittedResult persist(UUID threadRootId, UUID parentId, ChildTarget target, String content,
			boolean urgent, boolean threatFlagged, UUID idempotencyKey, String requestHash) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Inquiry followUp = Inquiry.followUp(threadRootId, parentId, target.studentId(), target.classroomId(),
				target.homeroomTeacherId(), content, urgent, threatFlagged, idempotencyKey, requestHash, now);
		if (urgent) {
			followUp.moveTo(InquiryStage.DELIVERED);
		}
		if (!urgent
				&& notifications.previewGeneral(followUp.getTeacherId()) == NotifyInquiryUseCase.NotificationDecision.DEFERRED) {
			// 일반 재문의 알림도 정리가 확정된 뒤에 요청한다(F3 FR-032와 같은 규칙). 여기서는 접수 시각 기준으로
			// 학부모에게 보일 지연 안내만 정해 둔다.
			followUp.markDeferredNotice();
		}
		Inquiry inquiry = inquiries.saveInquiry(followUp);
		histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.RECEIVED, now));
		if (urgent) {
			histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.DELIVERED, now));
			notifications.requestUrgent(inquiry.getId(), inquiry.getTeacherId());
			// 위기 종류 분류(F3)도 재문의마다 작업으로 남긴다 — 분류가 실패해도 긴급 전달·알림은 이미 끝나 있다.
			RefinementJob classify = refinementJobs.save(RefinementJob.classifyCrisis(inquiry.getId(), now));
			events.publishEvent(new RefinementRequested(classify.getId()));
		}
		else {
			// 정리 작업도 같은 트랜잭션에 저장하고(아웃박스), 외부 AI 호출은 커밋 뒤에 한다(F3 research R7과 같은 규칙).
			RefinementJob job = refinementJobs.save(RefinementJob.refine(inquiry.getId(), now));
			events.publishEvent(new RefinementRequested(job.getId()));
		}
		return resultOf(inquiry, threadRootId);
	}

	private FollowUpSubmittedResult replay(Inquiry inquiry, String requestHash) {
		if (!inquiry.getRequestHash().equals(requestHash)) {
			throw new BusinessException(ErrorCode.CONFLICT);
		}
		return resultOf(inquiry, inquiry.getThreadRootId());
	}

	private static FollowUpSubmittedResult resultOf(Inquiry inquiry, UUID threadRootId) {
		return new FollowUpSubmittedResult(inquiry.getId(), threadRootId, inquiry.isUrgent(),
				inquiry.getSubmittedAt(), GuidancePolicy.noticesFor(inquiry.isUrgent(), inquiry.isDeferredNotice()),
				GuidancePolicy.hotlinesFor(inquiry.isUrgent(), inquiry.getCrisisType()));
	}

}
