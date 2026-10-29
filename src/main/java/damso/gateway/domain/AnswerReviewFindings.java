package damso.gateway.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 답변 점검 결과의 서버 재검증과 복원 (F4 research R17, FR-012a). 외부 AI의 지적은 이 검증을 모두 통과해야만 교사에게 보인다 —
 * 하나라도 어긋나면 전체를 버린다(일부만 남기면 "문제 없음"처럼 보일 수 있다).
 * <ul>
 * <li>지적은 0~{@value #HIGHLIGHTS_MAX}개, 세 값 모두 공백만은 안 된다</li>
 * <li>세 값 모두 이번 대응표에 있는 토큰만 쓴다(지어낸 토큰 금지)</li>
 * <li>excerpt는 가린 답변 초안에 그대로 있고, 복원한 excerpt도 원래 초안에 그대로 있어야 한다 — 맥락에만 있거나 지어낸 문장은 안 된다</li>
 * <li>복원한 뒤 excerpt는 {@value #EXCERPT_MAX}자, reason·suggestion은 {@value #TEXT_MAX}자 이하(코드 포인트)</li>
 * </ul>
 * 복원은 원래 표기 그대로 한다(연락처 포함) — 교사 본인의 초안이고 결과는 저장하지 않는다(FR-013). 맥락(정리 결과)의 연락처는 이미 고정 문구다.
 */
public final class AnswerReviewFindings {

	public static final int HIGHLIGHTS_MAX = 5;

	public static final int EXCERPT_MAX = 200;

	public static final int TEXT_MAX = 300;

	/** 외부 AI가 낸 지적 하나(가린 상태, 검증 전). 로그에 남기지 않는다 — toString은 가린다. */
	public record Masked(String excerpt, String reason, String suggestion) {

		@Override
		public String toString() {
			return "Masked[]";
		}

	}

	/** 검증과 복원이 끝난 지적 하나. 답변·문의 내용이므로 toString은 가린다. */
	public record Valid(String excerpt, String reason, String suggestion) {

		@Override
		public String toString() {
			return "Valid[]";
		}

	}

	private AnswerReviewFindings() {
	}

	/**
	 * @param maskedDraft 외부로 보낸 가린 답변 초안
	 * @param originalDraft 가리기 전 답변 초안
	 * @return 모두 통과하면 복원한 지적 목록(빈 목록 가능), 하나라도 어긋나면 비어 있다
	 */
	public static Optional<List<Valid>> validate(List<Masked> findings, String maskedDraft, String originalDraft,
			PseudonymMap map) {
		if (findings == null || findings.size() > HIGHLIGHTS_MAX || maskedDraft == null || originalDraft == null) {
			return Optional.empty();
		}
		List<Valid> valid = new ArrayList<>();
		for (Masked finding : findings) {
			if (finding == null || blank(finding.excerpt()) || blank(finding.reason()) || blank(finding.suggestion())) {
				return Optional.empty();
			}
			if (!RefinementResult.onlyKnownTokens(finding.excerpt(), map)
					|| !RefinementResult.onlyKnownTokens(finding.reason(), map)
					|| !RefinementResult.onlyKnownTokens(finding.suggestion(), map)) {
				return Optional.empty();
			}
			if (!maskedDraft.contains(finding.excerpt())) {
				return Optional.empty();
			}
			String excerpt = map.restore(finding.excerpt());
			String reason = map.restore(finding.reason()).strip();
			String suggestion = map.restore(finding.suggestion()).strip();
			if (!originalDraft.contains(excerpt) || length(excerpt) > EXCERPT_MAX || length(reason) > TEXT_MAX
					|| length(suggestion) > TEXT_MAX) {
				return Optional.empty();
			}
			valid.add(new Valid(excerpt, reason, suggestion));
		}
		return Optional.of(List.copyOf(valid));
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	private static int length(String text) {
		return text.codePointCount(0, text.length());
	}

}
