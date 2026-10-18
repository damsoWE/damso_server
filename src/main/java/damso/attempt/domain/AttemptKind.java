package damso.attempt.domain;

/** 반복 실패 제한 대상 종류. Redis 키 이름에 쓰인다. */
public enum AttemptKind {

	LOGIN,
	INVITE_CODE,
	SIGNUP_CODE,
	RESET_CODE,
	MAIL_SEND

}
