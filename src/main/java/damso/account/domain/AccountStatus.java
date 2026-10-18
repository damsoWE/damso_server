package damso.account.domain;

/** ACTIVE → WITHDRAWN 단방향. 가입 인증 전 상태는 계정 테이블에 없다(pending_signup). */
public enum AccountStatus {

	ACTIVE,
	WITHDRAWN

}
