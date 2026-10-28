package damso.inquiry.domain;

/** 정리 시도가 왜 일어났는지. 자동 1회 + 자동 재시도 1회 + 교사 재시도 1회 (팀 결정). */
public enum RefinementTrigger {

	AUTO,
	AUTO_RETRY,
	TEACHER

}
