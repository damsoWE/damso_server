package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface SendAnswerUseCase {

	record AnswerSentResult(UUID answerId, UUID inquiryId, Instant sentAt) {

	}

	/**
	 * 담당 학급 문의에 답변을 전송한다. 점검 여부와 무관하게 동작한다.
	 * 현재 담임이 아니면 NOT_FOUND. {@code inquiryId}는 항상 스레드 루트를 가리킨다.
	 * {@code lastSeenFollowUpId}는 교사가 화면에서 본 마지막 재문의 id(재문의가 없었으면 null)이며, 스레드의 최신 재문의와
	 * 다르면 NEW_FOLLOW_UP — 답변을 쓰는 동안 온 재문의가 그 답변에 묻혀 "답변 완료"로 넘어가지 않게 한다(이슈 #28).
	 */
	AnswerSentResult send(UUID teacherId, UUID inquiryId, String content, boolean reviewed, UUID lastSeenFollowUpId);

}
