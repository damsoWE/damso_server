package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.NoticeCode;

/** 학부모가 본인이 쓴 문의를 보는 조회. 남의 문의는 존재 여부를 드러내지 않고 NOT_FOUND다. */
public interface ParentInquiryQuery {

	record InquirySummary(UUID inquiryId, UUID studentId, String studentName, boolean urgent, InquiryStage stage,
			Instant submittedAt) {

		/** 학생 이름이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "InquirySummary[inquiryId=" + inquiryId + "]";
		}

	}

	/**
	 * 스레드 타임라인에 포함되는 재문의 하나 — 학부모 본인 글이라 본문이 평문으로 보인다(F4). 긴급 재문의도
	 * 최초 문의와 같은 안내 코드를 담는다(FR-015·016, PR #10 리뷰).
	 */
	record FollowUpView(UUID inquiryId, String content, boolean urgent, InquiryStage stage, Instant submittedAt,
			List<NoticeCode> notices, List<HotlineCode> hotlines) {

		/** 본문이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "FollowUpView[inquiryId=" + inquiryId + "]";
		}

	}

	record InquiryDetail(UUID inquiryId, UUID studentId, String studentName, String content, boolean urgent,
			InquiryStage stage, Instant submittedAt, List<StageEventView> timeline, List<NoticeCode> notices,
			List<HotlineCode> hotlines, List<FollowUpView> followUps, List<AnswerView> answers) {

		/** 본문·학생 이름이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "InquiryDetail[inquiryId=" + inquiryId + "]";
		}

	}

	/** 본인이 작성한 문의, 최신순. */
	List<InquirySummary> list(UUID parentId);

	/** 본인이 작성한 문의가 아니면 NOT_FOUND. */
	InquiryDetail get(UUID parentId, UUID inquiryId);

}
