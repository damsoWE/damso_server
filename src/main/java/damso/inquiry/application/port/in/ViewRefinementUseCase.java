package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 교사의 번역본 열람 (F3 US2). 번역본은 교사에게만 보인다. */
public interface ViewRefinementUseCase {

	/** 이름·학급이 원래 값으로 되돌려진 정리 결과. 문의 내용이므로 toString은 가린다. */
	record RefinementView(UUID inquiryId, String title, String facts, String requests, String emotionLevel,
			List<String> emotionLabels, Instant refinedAt) {

		public RefinementView {
			emotionLabels = List.copyOf(emotionLabels);
		}

		@Override
		public String toString() {
			return "RefinementView[inquiryId=" + inquiryId + "]";
		}

	}

	/**
	 * 담당 학급 일반 문의의 번역본을 연다. 처음 열람하면(단계 REFINING) 처리 단계가 TEACHER_CONFIRMED가 된다. 감사 기록 대상은 아니다.
	 * 현재 담임이 아니면 NOT_FOUND, 번역본이 없으면(정리 전·원문 확인 필요·긴급) REFINEMENT_NOT_READY.
	 */
	RefinementView view(UUID teacherId, UUID inquiryId);

}
