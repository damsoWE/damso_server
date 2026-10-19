package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.common.crypto.CodeHasher;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.TooManyAttemptsException;
import damso.inquiry.application.port.in.SubmitInquiryUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.application.port.out.UrgentExpressionSource;
import damso.inquiry.domain.GuidancePolicy;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryContentPolicy;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;
import damso.inquiry.domain.UrgentRule;
import damso.notification.application.port.in.NotifyInquiryUseCase;
import damso.school.application.port.in.InquiryTargetQuery;
import damso.school.application.port.in.InquiryTargetQuery.ChildTarget;

/**
 * 문의 접수 (research R6). 순서:
 * ① 입력 검증 ② 중복 제출 확인(재시도는 같은 결과, 같은 키·다른 내용은 409) ③ 대상 확인(자녀·담임)
 * ④ 긴급 판정(규칙, AI 이전) ⑤ 일반이면 1분 제한 ⑥ 저장.
 * 긴급 문의는 1분 제한을 확인하지 않는다. 제한 저장소(Redis)를 쓸 수 없으면 일반 문의도 제한만 건너뛰고 접수한다 —
 * 도배 방지 장치가 학부모의 문의권을 막으면 안 된다. 본문·제목은 어떤 로그에도 남기지 않는다.
 */
@Service
public class SubmitInquiryService implements SubmitInquiryUseCase {

	private static final Logger log = LoggerFactory.getLogger(SubmitInquiryService.class);

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	private final AttemptLimitUseCase attempts;

	private final NotifyInquiryUseCase notifications;

	private final CodeHasher hasher;

	private final Clock clock;

	private final TransactionTemplate transaction;

	private final UrgentRule urgentRule;

	public SubmitInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets,
			AttemptLimitUseCase attempts, NotifyInquiryUseCase notifications, CodeHasher hasher, Clock clock,
			PlatformTransactionManager transactionManager, UrgentExpressionSource urgentExpressions) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
		this.attempts = attempts;
		this.notifications = notifications;
		this.hasher = hasher;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
		this.urgentRule = new UrgentRule(urgentExpressions.expressions());
	}

	@Override
	public InquirySubmittedResult submit(UUID parentId, UUID studentId, String content, UUID idempotencyKey) {
		InquiryContentPolicy.validate(content);
		String requestHash = hasher.hash(studentId + ":" + content);

		// 재시도는 대상·제한을 다시 보지 않는다. 처음 접수된 뒤 담임이 바뀌어도 같은 결과를 돌려줘야 한다.
		Optional<Inquiry> existing = inquiries.findByParentAndKey(parentId, idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), requestHash);
		}

		ChildTarget target = targets.resolveChild(parentId, studentId);
		if (!target.hasHomeroomTeacher()) {
			throw new BusinessException(ErrorCode.NO_HOMEROOM_TEACHER);
		}

		boolean urgent = urgentRule.isUrgent(content);
		if (!urgent) {
			try {
				enforceSubmitLimit(parentId);
			}
			catch (TooManyAttemptsException ex) {
				// 같은 키의 다른 요청이 방금 접수됐다면 거부가 아니라 그 결과를 돌려준다(동시 재시도)
				Optional<Inquiry> raced = inquiries.findByParentAndKey(parentId, idempotencyKey);
				if (raced.isPresent()) {
					return replay(raced.get(), requestHash);
				}
				throw ex;
			}
		}

		try {
			return transaction.execute(status -> persist(parentId, target, content, urgent, idempotencyKey, requestHash));
		}
		catch (DataIntegrityViolationException ex) {
			// 같은 키가 동시에 들어와 다른 요청이 먼저 저장했다
			Inquiry winner = inquiries.findByParentAndKey(parentId, idempotencyKey).orElseThrow(() -> ex);
			return replay(winner, requestHash);
		}
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
			log.warn("문의 제출 제한 저장소를 쓸 수 없어 제한 확인을 건너뜁니다 (kind=INQUIRY_SUBMIT)");
			return;
		}
		if (waitSeconds > 0) {
			throw new TooManyAttemptsException(waitSeconds);
		}
	}

	private InquirySubmittedResult persist(UUID parentId, ChildTarget target, String content, boolean urgent,
			UUID idempotencyKey, String requestHash) {
		// DB(timestamptz)는 마이크로초까지만 저장한다. 첫 응답과 재전송 응답의 시각이 같도록 처음부터 맞춘다.
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Inquiry received = Inquiry.receive(parentId, target.studentId(), target.classroomId(),
				target.homeroomTeacherId(), content, urgent, idempotencyKey, requestHash, now);
		if (urgent) {
			// 긴급은 정제를 건너뛰고 접수와 같은 트랜잭션에서 교사에게 전달된 상태가 된다 (FR-009·010)
			received.moveTo(InquiryStage.DELIVERED);
		}
		if (!urgent) {
			// 일반 문의는 교사의 알림 설정에 따라 즉시·지연·없음. 지연이면 응답에 지연 안내가 붙는다 (FR-027~030)
			NotifyInquiryUseCase.NotificationDecision decision = notifications.requestGeneral(received.getId(),
					received.getTeacherId());
			if (decision == NotifyInquiryUseCase.NotificationDecision.DEFERRED) {
				received.markNotificationDeferred();
			}
		}
		Inquiry inquiry = inquiries.saveInquiry(received);
		histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.RECEIVED, now));
		if (urgent) {
			histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.DELIVERED, now));
			// 알림 요청은 문의와 같은 트랜잭션에 저장하고(아웃박스), 발송은 커밋 뒤에 한다. Redis·FCM을 호출하지 않는다.
			notifications.requestUrgent(inquiry.getId(), inquiry.getTeacherId());
		}
		return resultOf(inquiry);
	}

	private InquirySubmittedResult replay(Inquiry inquiry, String requestHash) {
		if (!inquiry.getRequestHash().equals(requestHash)) {
			throw new BusinessException(ErrorCode.CONFLICT);
		}
		return resultOf(inquiry);
	}

	private static InquirySubmittedResult resultOf(Inquiry inquiry) {
		// 재전송도 처음 응답과 같도록, 이후 단계가 아니라 접수 시점의 단계를 돌려준다
		InquiryStage initialStage = inquiry.isUrgent() ? InquiryStage.DELIVERED : InquiryStage.RECEIVED;
		return new InquirySubmittedResult(inquiry.getId(), inquiry.isUrgent(), initialStage,
				inquiry.getSubmittedAt(), GuidancePolicy.noticesFor(inquiry.isUrgent(), inquiry.isNotificationDeferred()),
				GuidancePolicy.hotlinesFor(inquiry.isUrgent(), inquiry.getCrisisType()));
	}

}
