package damso.inquiry.domain;

/**
 * 일반 문의의 정리 상태 (F3 data-model). 긴급 문의는 정리 대상이 아니라 null이다.
 * 처리 단계(InquiryStage)와 별개다 — NEEDS_ORIGINAL은 단계를 앞으로 보내지 않는다.
 */
public enum RefinementStatus {

	/** 정리 중 */
	PENDING,
	/** 번역본 있음 */
	REFINED,
	/** 자동 정리에 실패했다. 교사는 원문으로 확인하고, 1회 재시도할 수 있다 */
	NEEDS_ORIGINAL

}
