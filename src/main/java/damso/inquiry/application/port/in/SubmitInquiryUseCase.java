package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.NoticeCode;

public interface SubmitInquiryUseCase {

	/** 접수 결과. notices·hotlines는 코드뿐이며 고정 문구는 API 계약에 있다. */
	record InquirySubmittedResult(UUID inquiryId, boolean urgent, InquiryStage stage, Instant submittedAt,
			List<NoticeCode> notices, List<HotlineCode> hotlines) {

		/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
		public InquirySubmittedResult {
			notices = notices == null ? null : List.copyOf(notices);
			hotlines = hotlines == null ? null : List.copyOf(hotlines);
		}

	}

	/**
	 * 문의를 접수한다. 같은 (학부모, idempotencyKey)가 이미 접수됐으면 새로 만들지 않고 그 문의의 결과를 돌려준다.
	 * 단, 같은 키로 자녀나 본문이 다르면 CONFLICT.
	 * 없는 학생·남의 자녀는 NOT_FOUND, 담임 없는 학급은 NO_HOMEROOM_TEACHER,
	 * 일반 문의가 1분 제한에 걸리면 TooManyAttemptsException.
	 */
	InquirySubmittedResult submit(UUID parentId, UUID studentId, String content, UUID idempotencyKey);

}
