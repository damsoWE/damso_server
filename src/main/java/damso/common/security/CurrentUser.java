package damso.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 인증 주체. 사용자·역할·세션은 토큰에서만 꺼낸다 — 요청 본문·파라미터의 값은 권한 판단에 쓰지 않는다 (원칙 VII).
 */
public record CurrentUser(UUID userId, String role, UUID sessionId) {

	public static CurrentUser from(Jwt jwt) {
		return new CurrentUser(
				UUID.fromString(jwt.getSubject()),
				jwt.getClaimAsString(JwtDecoderConfig.ROLE_CLAIM),
				UUID.fromString(jwt.getClaimAsString(JwtDecoderConfig.SESSION_CLAIM)));
	}

	@Override
	public String toString() {
		return "CurrentUser[userId=" + userId + "]";
	}

}
