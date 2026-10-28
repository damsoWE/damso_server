package damso.account.application.service;

/** 재설정 요청이 발송 제한을 통과했다. 계정 조회·코드 발급은 응답 뒤에 한다(PasswordResetIssuer, 이슈 #22). */
record PasswordResetRequested(String email) {

	/** 이메일이 로그·예외에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "PasswordResetRequested[email=***]";
	}

}
