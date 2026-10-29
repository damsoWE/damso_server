package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.RefinementStatus;
import damso.inquiry.domain.ThreadAnswerStatus;

/**
 * 교사가 담당 학급의 문의를 보는 조회. 본문·번역본은 어디에도 없다
 * (원문은 ViewInquiryContentUseCase로만 감사 기록과 함께, 번역본은 ViewRefinementUseCase로만).
 */
public interface TeacherInquiryQuery {

	/**
	 * refinementStatus는 일반 문의의 정리 상태, 긴급 문의는 null(F3).
	 * threatFlagged는 접수 때 정한 위협·폭언 표현 표시, 긴급은 항상 false(spec 005).
	 * threadThreatFlagged는 최초 문의나 재문의 가운데 하나라도 표시됐는지 — 재문의에만 위협 표현이 있어도 목록에서 보인다.
	 * lastAnsweredAt은 스레드 전체의 마지막 답변 전송 시각, 답변이 없으면 null(F4 US6). 답변 여부는 hasAnswer()로 이 값에서 얻는다.
	 * lastFollowUpAt은 스레드의 마지막 재문의 접수 시각, 재문의가 없으면 null(이슈 #28). 답변 대기 여부는 needsAnswer()로 얻는다.
	 */
	record InquirySummary(UUID inquiryId, UUID studentId, String studentName, boolean urgent, String title,
			InquiryStage stage, RefinementStatus refinementStatus, boolean threatFlagged, boolean threadThreatFlagged,
			Instant submittedAt, Instant lastAnsweredAt, Instant lastFollowUpAt) {

		public boolean hasAnswer() {
			return lastAnsweredAt != null;
		}

		/**
		 * 답변이 없거나 마지막 답변 뒤에 재문의가 왔으면 true. 목록의 answerStatus=WAITING 조건과 같은 기준이다
		 * (SpringDataInquiryRepository.ANSWERED_THREAD의 반대).
		 */
		public boolean needsAnswer() {
			return !hasAnswer() || (lastFollowUpAt != null && lastFollowUpAt.isAfter(lastAnsweredAt));
		}

		/** 학생 이름이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "InquirySummary[inquiryId=" + inquiryId + "]";
		}

	}

	record InquiryPage(List<InquirySummary> items, int page, int size, long totalElements) {

		/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
		public InquiryPage {
			items = items == null ? null : List.copyOf(items);
		}

	}

	/**
	 * 스레드 타임라인에 포함되는 재문의 하나 — 목록·상세와 같은 규칙으로 본문은 없고, 긴급만 /content로 열람한다(F4).
	 * threatFlagged는 재문의를 접수할 때 정한 위협·폭언 표현 표시, 긴급은 항상 false(spec 005).
	 */
	record FollowUpView(UUID inquiryId, boolean urgent, String title, InquiryStage stage, boolean threatFlagged,
			Instant submittedAt) {
	}

	/**
	 * retryAvailable: 원문 확인 필요(NEEDS_ORIGINAL)이고 아직 교사 재시도를 쓰지 않았으면 true(F3).
	 * threatFlagged는 접수 때 정한 위협·폭언 표현 표시, 긴급은 항상 false(spec 005).
	 */
	record InquiryDetail(UUID inquiryId, UUID studentId, String studentName, boolean urgent, String title,
			InquiryStage stage, RefinementStatus refinementStatus, boolean retryAvailable, boolean threatFlagged,
			Instant submittedAt, List<StageEventView> timeline, List<FollowUpView> followUps,
			List<AnswerView> answers) {

		/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
		public InquiryDetail {
			timeline = timeline == null ? null : List.copyOf(timeline);
			followUps = followUps == null ? null : List.copyOf(followUps);
			answers = answers == null ? null : List.copyOf(answers);
		}

		@Override
		public String toString() {
			return "InquiryDetail[inquiryId=" + inquiryId + "]";
		}

	}

	/**
	 * 현재 담임인 학급의 문의, 긴급이 먼저 그다음 최신순. 담임이 아니면 빈 목록.
	 * answerStatus가 null이 아니면 그 답변 상태의 스레드만 돌려주고 totalElements도 거른 결과 기준이다(이슈 #28).
	 * page는 0 이상, size는 1~50이어야 하며 아니면 InvalidInputException.
	 */
	InquiryPage list(UUID teacherId, ThreadAnswerStatus answerStatus, int page, int size);

	/** 현재 담임인 학급의 문의가 아니면 NOT_FOUND(존재 여부를 드러내지 않는다). */
	InquiryDetail get(UUID teacherId, UUID inquiryId);

}
