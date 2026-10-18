package damso.account.adapter.in.controller.dto.response;

import damso.account.application.service.TokenResult;
import damso.account.domain.Role;

/** 토큰 원문은 이 응답에서만 나간다. toString에서 가린다. */
public record TokenResponseDTO(String accessToken, long accessTokenExpiresInSeconds, String refreshToken,
		long refreshTokenExpiresInSeconds, Role role) {

	public static TokenResponseDTO from(TokenResult result) {
		return new TokenResponseDTO(result.accessToken(), result.accessTokenExpiresInSeconds(),
				result.refreshToken(), result.refreshTokenExpiresInSeconds(), result.role());
	}

	@Override
	public String toString() {
		return "TokenResponseDTO[role=" + role + "]";
	}

}
