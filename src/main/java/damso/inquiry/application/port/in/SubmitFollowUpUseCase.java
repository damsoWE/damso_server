package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface SubmitFollowUpUseCase {

	/** 재문의 자신의 접수 결과(F2 InquirySubmittedResult와 같은 모양, 안내 코드는 없다). */
	record FollowUpSubmittedResult(UUID inquiryId, UUID threadRootId, boolean urgent, Instant submittedAt) {
	}

	/**
	 * 같은 스레드에 재문의를 제출한다. rootInquiryId는 항상 스레드 루트의 id다.
	 * 같은 (학부모, idempotencyKey)가 이미 접수됐으면 새로 만들지 않고 그 결과를 돌려준다.
	 * 같은 키로 다른 내용이면 CONFLICT. 본인 소유가 아니거나 스레드 루트가 아니면 NOT_FOUND.
	 * 종료된 스레드면 THREAD_CLOSED, 담임 없는 학급이면 NO_HOMEROOM_TEACHER,
	 * 일반 재문의가 1분 제한에 걸리면 TooManyAttemptsException.
	 */
	FollowUpSubmittedResult submit(UUID parentId, UUID rootInquiryId, String content, UUID idempotencyKey);

}
