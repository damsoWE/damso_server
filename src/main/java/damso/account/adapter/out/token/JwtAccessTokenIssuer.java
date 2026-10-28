package damso.account.adapter.out.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import damso.account.application.port.out.AccessTokenIssuerPort;
import damso.account.domain.Role;

/**
 * 접근 토큰(JWT, HS256)을 발급한다. 서명 키는 검증 쪽(JwtDecoderConfig)과 같은 damso.jwt.secret이고 알고리즘은 HS256으로 고정이다.
 * 클레임: sub(사용자 UUID), role, sid(세션 UUID), iat, exp.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuerPort {

	private final JwtEncoder encoder;

	private final Duration ttl;

	JwtAccessTokenIssuer(@Value("${damso.jwt.secret}") String base64Secret,
			@Value("${damso.auth.access-token-ttl}") Duration ttl) {
		SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.ttl = ttl;
	}

	@Override
	public String issue(UUID userId, Role role, UUID sessionId, Instant issuedAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId.toString())
				.claim("role", role.name())
				.claim("sid", sessionId.toString())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(ttl))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	@Override
	public long ttlSeconds() {
		return ttl.toSeconds();
	}

}
