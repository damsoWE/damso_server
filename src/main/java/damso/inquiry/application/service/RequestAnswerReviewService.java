package damso.inquiry.application.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import damso.audit.application.port.in.AuditLogUseCase;
import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditTargetType;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.RequestAnswerReviewUseCase;
import damso.inquiry.application.port.out.AnswerReviewPort;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryContentPolicy;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 답변 점검 요청. pseudonymizedContext는 F3 전까지 항상 null이다.
 * 점검 포트가 실패하거나 예외를 던져도 이 서비스는 예외를 전파하지 않는다(fail-open).
 * 점검 요청은 감사 기록을 남긴다. 기록이 실패해도 점검 요청은 그대로 처리하고 로그만 남긴다(팀 결정 2026-10-28).
 * 바깥 트랜잭션을 두지 않는다 — 감사 기록은 자기 트랜잭션으로 커밋되고, 실패해도 이 요청을 롤백 전용으로 만들지 않는다.
 */
@Service
public class RequestAnswerReviewService implements RequestAnswerReviewUseCase {

	private static final Logger log = LoggerFactory.getLogger(RequestAnswerReviewService.class);

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	private final AnswerReviewPort review;

	private final AuditLogUseCase audit;

	public RequestAnswerReviewService(InquiryPort inquiries, InquiryTargetQuery targets, AnswerReviewPort review,
			AuditLogUseCase audit) {
		this.inquiries = inquiries;
		this.targets = targets;
		this.review = review;
		this.audit = audit;
	}

	@Override
	public AnswerReviewResult request(UUID teacherId, UUID inquiryId, String draftContent) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 점검을 요청할 수 없다.
				.filter(found -> found.getThreadRootId() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		InquiryContentPolicy.validate(draftContent);
		recordOrLog(teacherId, inquiry.getId());
		try {
			// pseudonymizedContext는 F3가 만드는 3요소 분해 결과가 들어가는 자리다. 지금은 항상 null이다.
			return review.review(draftContent, null).orElse(AnswerReviewResult.UNAVAILABLE);
		}
		catch (RuntimeException ex) {
			log.warn("답변 점검 실패 (inquiry={}, type={})", inquiry.getId(), ex.getClass().getSimpleName());
			return AnswerReviewResult.UNAVAILABLE;
		}
	}

	/** 기록 실패로 점검 요청을 막지 않는다. 로그에는 식별자·행위·오류 종류만 남긴다(예외 메시지에 값이 섞일 수 있다). */
	private void recordOrLog(UUID teacherId, UUID inquiryId) {
		try {
			audit.record(teacherId, AuditAction.ANSWER_REVIEW_REQUESTED, AuditTargetType.INQUIRY, inquiryId);
		}
		catch (RuntimeException ex) {
			log.warn("답변 점검 요청 감사 기록 실패 (inquiry={}, action={}, type={})", inquiryId,
					AuditAction.ANSWER_REVIEW_REQUESTED, ex.getClass().getSimpleName());
		}
	}

}
