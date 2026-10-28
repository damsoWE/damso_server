package damso.inquiry.application.port.in;

import java.util.UUID;

import damso.inquiry.domain.RefinementStatus;

/** 자동 정리에 실패한 문의를 교사가 한 번 더 정리시킨다. */
public interface RetryRefinementUseCase {

	record RetryAccepted(UUID inquiryId, RefinementStatus refinementStatus) {
	}

	/**
	 * 담당 학급의 원문 확인 필요(NEEDS_ORIGINAL) 일반 문의이고 아직 교사 재시도를 쓰지 않았을 때만 받는다. 정리는 뒤에서 진행된다.
	 * 현재 담임이 아니면 NOT_FOUND, 그 밖(정리 중·완료·긴급·이미 재시도함·동시 요청)은 REFINEMENT_RETRY_UNAVAILABLE.
	 */
	RetryAccepted retry(UUID teacherId, UUID inquiryId);

}
