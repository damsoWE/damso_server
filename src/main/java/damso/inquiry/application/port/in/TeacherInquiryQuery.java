package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.RefinementStatus;

/**
 * 교사가 담당 학급의 문의를 보는 조회. 본문·번역본은 어디에도 없다
 * (원문은 ViewInquiryContentUseCase로만 감사 기록과 함께, 번역본은 ViewRefinementUseCase로만).
 */
public interface TeacherInquiryQuery {

	/**
	 * refinementStatus는 일반 문의의 정리 상태, 긴급 문의는 null(F3).
	 * threatFlagged는 접수 때 정한 위협·폭언 표현 표시, 긴급은 항상 false(spec 004).
	 */
	record InquirySummary(UUID inquiryId, UUID studentId, String studentName, boolean urgent, String title,
			InquiryStage stage, RefinementStatus refinementStatus, boolean threatFlagged, Instant submittedAt) {

		/** 학생 이름이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "InquirySummary[inquiryId=" + inquiryId + "]";
		}

	}

	record InquiryPage(List<InquirySummary> items, int page, int size, long totalElements) {
	}

	/**
	 * retryAvailable: 원문 확인 필요(NEEDS_ORIGINAL)이고 아직 교사 재시도를 쓰지 않았으면 true(F3).
	 * threatFlagged는 접수 때 정한 위협·폭언 표현 표시, 긴급은 항상 false(spec 004).
	 */
	record InquiryDetail(UUID inquiryId, UUID studentId, String studentName, boolean urgent, String title,
			InquiryStage stage, RefinementStatus refinementStatus, boolean retryAvailable, boolean threatFlagged,
			Instant submittedAt, List<StageEventView> timeline) {

		@Override
		public String toString() {
			return "InquiryDetail[inquiryId=" + inquiryId + "]";
		}

	}

	/**
	 * 현재 담임인 학급의 문의, 긴급이 먼저 그다음 최신순. 담임이 아니면 빈 목록.
	 * page는 0 이상, size는 1~50이어야 하며 아니면 InvalidInputException.
	 */
	InquiryPage list(UUID teacherId, int page, int size);

	/** 현재 담임인 학급의 문의가 아니면 NOT_FOUND(존재 여부를 드러내지 않는다). */
	InquiryDetail get(UUID teacherId, UUID inquiryId);

}
