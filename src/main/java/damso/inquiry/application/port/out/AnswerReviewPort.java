package damso.inquiry.application.port.out;

import java.util.Optional;

import damso.inquiry.domain.AnswerReviewResult;

/**
 * 답변 점검(가명화 → 점검 → 복원) 게이트웨이. 구현은 반드시 가명화 게이트웨이를 거쳐야 한다(원칙 II).
 * {@code pseudonymizedContext}는 F3의 3요소 분해 결과(가명화된 문자열)가 들어가는 자리이며,
 * F3 전까지는 호출하는 쪽이 항상 null을 넘긴다(FR-008a).
 * 결과를 낼 수 없으면(장애·형식 오류 등) 예외 없이 {@link Optional#empty()}를 반환한다(fail-open, FR-012).
 */
public interface AnswerReviewPort {

	Optional<AnswerReviewResult> review(String draftContent, String pseudonymizedContext);

}
