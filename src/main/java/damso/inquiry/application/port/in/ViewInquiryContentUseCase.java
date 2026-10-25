package damso.inquiry.application.port.in;

import java.util.UUID;

public interface ViewInquiryContentUseCase {

	record InquiryContentView(UUID inquiryId, String content) {

		/** 본문이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "InquiryContentView[inquiryId=" + inquiryId + "]";
		}

	}

	/**
	 * 담당 학급 문의의 원문을 연다(F2는 긴급만). 감사 기록과 같은 트랜잭션이며, 기록이 실패하면 본문은 나가지 않는다.
	 * 처음 열람하면 처리 단계가 TEACHER_CONFIRMED로 바뀐다.
	 * 현재 담임이 아니면 NOT_FOUND, 일반 문의면 INQUIRY_CONTENT_UNAVAILABLE.
	 */
	InquiryContentView view(UUID teacherId, UUID inquiryId);

}
