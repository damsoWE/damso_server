package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface SendAnswerUseCase {

	record AnswerSentResult(UUID answerId, UUID inquiryId, Instant sentAt) {

	}

	/**
	 * 담당 학급 문의에 답변을 전송한다. 점검 여부와 무관하게 동작한다(FR-002·007).
	 * 현재 담임이 아니면 NOT_FOUND. {@code inquiryId}는 항상 스레드 루트를 가리킨다.
	 */
	AnswerSentResult send(UUID teacherId, UUID inquiryId, String content, boolean reviewed);

}
