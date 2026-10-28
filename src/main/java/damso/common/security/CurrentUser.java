package damso.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

/**
 * 인증 주체. 사용자·역할·세션은 토큰에서만 꺼낸다 — 요청 본문·파라미터의 값은 권한 판단에 쓰지 않는다.
 */
public record CurrentUser(UUID userId, String role, UUID sessionId) {

	public static CurrentUser from(Jwt jwt) {
		// 디코더가 sub·sid를 UUID로 검증한다(JwtDecoderConfig). 그래도 없으면 사용자를 정할 수 없으므로 401로 끝낸다
		String subject = jwt.getSubject();
		String sessionId = jwt.getClaimAsString(JwtDecoderConfig.SESSION_CLAIM);
		if (subject == null || sessionId == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return new CurrentUser(UUID.fromString(subject), jwt.getClaimAsString(JwtDecoderConfig.ROLE_CLAIM),
				UUID.fromString(sessionId));
	}

	@Override
	public String toString() {
		return "CurrentUser[userId=" + userId + "]";
	}

}
