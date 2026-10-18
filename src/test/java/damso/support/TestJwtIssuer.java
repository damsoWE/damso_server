package damso.support;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/** 테스트 전용 토큰 발급기. 운영 발급은 F1에서 구현한다. */
public final class TestJwtIssuer {

	private TestJwtIssuer() {
	}

	/** 유효한 서명·만료이고 sid가 가리키는 세션이 살아 있으면 통과하는 토큰. */
	public static String valid(String role, UUID sessionId) {
		return signed(TestSecrets.jwtKey(), role, Instant.now().plusSeconds(600), true, sessionId);
	}

	public static String expired(String role) {
		return signed(TestSecrets.jwtKey(), role, Instant.now().minusSeconds(600), true);
	}

	public static String forged(String role) {
		return signed(TestSecrets.randomBytes(32), role, Instant.now().plusSeconds(600), true);
	}

	public static String withoutExp(String role) {
		return signed(TestSecrets.jwtKey(), role, null, true);
	}

	public static String unsigned(String role) {
		return new PlainJWT(claims(role, Instant.now().plusSeconds(600), true, UUID.randomUUID())).serialize();
	}

	private static String signed(byte[] key, String role, Instant exp, boolean withIat) {
		return signed(key, role, exp, withIat, UUID.randomUUID());
	}

	private static String signed(byte[] key, String role, Instant exp, boolean withIat, UUID sessionId) {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(role, exp, withIat, sessionId));
			jwt.sign(new MACSigner(key));
			return jwt.serialize();
		}
		catch (JOSEException e) {
			throw new IllegalStateException("test token signing failed", e);
		}
	}

	private static JWTClaimsSet claims(String role, Instant exp, boolean withIat, UUID sessionId) {
		JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.claim("role", role)
				.claim("sid", sessionId.toString());
		if (exp != null) {
			builder.expirationTime(Date.from(exp));
		}
		if (withIat) {
			builder.issueTime(Date.from(Instant.now().minusSeconds(1200)));
		}
		return builder.build();
	}

}
