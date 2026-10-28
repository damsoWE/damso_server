package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.NoticeCode;

public interface SubmitFollowUpUseCase {

	/**
	 * 재문의 자신의 접수 결과(F2 InquirySubmittedResult와 같은 모양). 긴급 재문의도 최초 문의와 같은 안내
	 * 코드(notices·hotlines)를 받는다 — 재문의도 "즉시 전달 경로를 그대로 따른다"(FR-015·016, PR #10 리뷰).
	 */
	record FollowUpSubmittedResult(UUID inquiryId, UUID threadRootId, boolean urgent, Instant submittedAt,
			List<NoticeCode> notices, List<HotlineCode> hotlines) {

		/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
		public FollowUpSubmittedResult {
			notices = notices == null ? null : List.copyOf(notices);
			hotlines = hotlines == null ? null : List.copyOf(hotlines);
		}

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
