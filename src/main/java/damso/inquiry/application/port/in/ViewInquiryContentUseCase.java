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
	 * 담당 학급 문의의 원문을 연다(일반·긴급, F3부터 일반 문의도). 열람 시도 기록이 먼저 남고, 기록이 실패하면 본문은 나가지 않는다.
	 * 긴급 문의와 원문 확인 필요 일반 문의는 처음 열람하면 처리 단계가 TEACHER_CONFIRMED로 바뀐다. 번역본이 있는 일반 문의는
	 * 번역본 첫 열람이 기준이라 원문 열람으로는 단계가 바뀌지 않는다. 현재 담임이 아니면 NOT_FOUND.
	 */
	InquiryContentView view(UUID teacherId, UUID inquiryId);

}
