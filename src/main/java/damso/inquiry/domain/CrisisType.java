package damso.inquiry.domain;

/** 긴급 문의의 위기 종류. F2에서는 UNCLASSIFIED만 쓰고, 분류는 F3가 연결 지점으로 넘긴다 (FR-018·019). */
public enum CrisisType {

	UNCLASSIFIED,
	SELF_HARM,
	ABUSE,
	OTHER_SAFETY

}
