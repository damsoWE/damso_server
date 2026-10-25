package damso.inquiry.application.port.in;

import java.util.UUID;

import damso.inquiry.domain.AnswerReviewResult;

public interface RequestAnswerReviewUseCase {

	/**
	 * 전송 전 답변 점검(선택). 현재 담임이 아니면 NOT_FOUND. 점검이 실패하거나 예외가 나도 이 메서드는
	 * 예외를 던지지 않고 {@link AnswerReviewResult#UNAVAILABLE}을 돌려준다(fail-open, FR-012).
	 */
	AnswerReviewResult request(UUID teacherId, UUID inquiryId, String draftContent);

}
