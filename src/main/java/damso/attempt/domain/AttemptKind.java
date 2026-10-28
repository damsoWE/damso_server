package damso.attempt.domain;

/** 반복 실패 제한 대상 종류. Redis 키 이름에 쓰인다. */
public enum AttemptKind {

	LOGIN,
	INVITE_CODE,
	SIGNUP_CODE,
	RESET_CODE,
	MAIL_SEND,
	INQUIRY_SUBMIT,
	/** 비밀번호 재설정 메일 — 요청 위치(IP) 하나의 모든 이메일 합산. */
	RESET_MAIL_CLIENT,
	/** 비밀번호 재설정 메일 — 이메일 + 요청 위치(IP) 쌍. */
	RESET_MAIL_PAIR,
	/** 비밀번호 재설정 메일 — 이메일 하나의 모든 요청 위치 합산. */
	RESET_MAIL_EMAIL

}
