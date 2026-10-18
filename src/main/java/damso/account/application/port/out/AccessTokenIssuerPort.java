package damso.account.application.port.out;

import java.time.Instant;
import java.util.UUID;

import damso.account.domain.Role;

/** 접근 토큰 발급. 클레임: sub(사용자 UUID), role, sid(세션 UUID), iat, exp. */
public interface AccessTokenIssuerPort {

	String issue(UUID userId, Role role, UUID sessionId, Instant issuedAt);

	long ttlSeconds();

}
