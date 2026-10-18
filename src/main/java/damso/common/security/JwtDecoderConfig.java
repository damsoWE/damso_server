package damso.common.security;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 헤더 Bearer 토큰 검증. 발급은 F1 범위.
 * - 서명 알고리즘을 HS256 하나로 고정 → alg=none·알고리즘 혼동 차단 (research R1)
 * - exp·iat 필수
 * - sid(세션 UUID) 필수, 그 세션이 살아 있어야 함 (F1, research R4)
 * - role 클레임은 허용 목록에 있을 때만 ROLE_{role} 권한으로 변환
 */
@Configuration
public class JwtDecoderConfig {

	static final String ROLE_CLAIM = "role";
	static final String SESSION_CLAIM = "sid";
	static final Set<String> ALLOWED_ROLES = Set.of("PARENT", "TEACHER", "SUPER_ADMIN");

	@Bean
	JwtDecoder jwtDecoder(@Value("${damso.jwt.secret}") String base64Secret, SessionStatusChecker sessionStatusChecker) {
		SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(),
				new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
				new JwtClaimValidator<Instant>(JwtClaimNames.IAT, Objects::nonNull),
				new JwtClaimValidator<String>(SESSION_CLAIM, sid -> isActiveSession(sessionStatusChecker, sid)));
		decoder.setJwtValidator(validator);
		return decoder;
	}

	private static boolean isActiveSession(SessionStatusChecker checker, String sid) {
		if (sid == null) {
			return false;
		}
		try {
			return checker.isActive(UUID.fromString(sid));
		}
		catch (IllegalArgumentException notAUuid) {
			return false;
		}
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
		return jwt -> {
			String role = jwt.getClaimAsString(ROLE_CLAIM);
			List<SimpleGrantedAuthority> authorities = (role != null && ALLOWED_ROLES.contains(role))
					? List.of(new SimpleGrantedAuthority("ROLE_" + role))
					: List.of();
			return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
		};
	}

}
