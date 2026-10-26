package damso.inquiry.adapter.out.review;

import java.util.Optional;

import org.springframework.stereotype.Component;

import damso.inquiry.application.port.out.AnswerReviewPort;
import damso.inquiry.domain.AnswerReviewResult;

/**
 * 가명화 게이트웨이(F3)가 아직 없다 — 사용자 확인(2026-10-25)에 따라 항상 "점검 실패"를 반환하는 스텁이다.
 * 외부 호출을 하지 않으므로 가명화 전 원문이 어디로도 나가지 않는다(원칙 II).
 * F3가 완성되면 이 클래스를 실제 가명화 게이트웨이 호출 어댑터로 교체한다.
 */
@Component
class UnavailableAnswerReviewAdapter implements AnswerReviewPort {

	@Override
	public Optional<AnswerReviewResult> review(String draftContent, String pseudonymizedContext) {
		return Optional.empty();
	}

}
