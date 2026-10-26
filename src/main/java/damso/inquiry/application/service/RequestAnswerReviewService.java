package damso.inquiry.application.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 답변 점검 요청 (FR-008·008a·009~013). pseudonymizedContext는 F3 전까지 항상 null이다(FR-008a).
 * 점검 포트가 실패하거나 예외를 던져도 이 서비스는 예외를 전파하지 않는다(fail-open, FR-012).
 */
@Service
@Transactional(readOnly = true)
public class RequestAnswerReviewService implements RequestAnswerReviewUseCase {

	private static final Logger log = LoggerFactory.getLogger(RequestAnswerReviewService.class);

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	private final AnswerReviewPort review;

	public RequestAnswerReviewService(InquiryPort inquiries, InquiryTargetQuery targets, AnswerReviewPort review) {
		this.inquiries = inquiries;
		this.targets = targets;
		this.review = review;
	}

	@Override
	public AnswerReviewResult request(UUID teacherId, UUID inquiryId, String draftContent) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 점검을 요청할 수 없다(research R1).
				.filter(found -> found.getThreadRootId() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		InquiryContentPolicy.validate(draftContent);
		try {
			// pseudonymizedContext는 F3가 만드는 3요소 분해 결과가 들어가는 자리다. 지금은 항상 null이다(FR-008a).
			return review.review(draftContent, null).orElse(AnswerReviewResult.UNAVAILABLE);
		}
		catch (RuntimeException ex) {
			log.warn("답변 점검 실패 (inquiry={}, type={})", inquiry.getId(), ex.getClass().getSimpleName());
			return AnswerReviewResult.UNAVAILABLE;
		}
	}

}
