package damso.account.application.service;

import damso.account.domain.Role;

/** 로그인·가입·갱신의 결과. 토큰 원문은 이 응답에서만 나간다. */
public record TokenResult(
		String accessToken,
		long accessTokenExpiresInSeconds,
		String refreshToken,
		long refreshTokenExpiresInSeconds,
		Role role) {

	@Override
	public String toString() {
		return "TokenResult[role=" + role + "]";
	}

}
