package damso.inquiry.domain;

/**
 * 긴급 문의의 위기 종류. 접수 때는 UNCLASSIFIED이고, F3 RefinementRunner가 가명화 게이트웨이를 거친 분류 결과를 연결 지점으로
 * 넘기면 정해진다. 분류가 실패하면 UNCLASSIFIED(기본 안내 112·109)로 남는다.
 */
public enum CrisisType {

	UNCLASSIFIED,
	SELF_HARM,
	ABUSE,
	OTHER_SAFETY

}
