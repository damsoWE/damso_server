package damso.account.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.account.domain.AccountStatus;
import damso.account.domain.AuthSession;

interface SpringDataAuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	/**
	 * version도 올린다 — 일괄 UPDATE는 @Version을 거치지 않으므로, 올리지 않으면 폐기 전에 세션을 읽어 둔 갱신이 저장하면서
	 * 폐기를 되돌린다(revoked_at = null). 올려 두면 그 저장은 버전 충돌로 실패한다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update AuthSession s set s.revokedAt = :now, s.revokeReason = :reason, s.version = s.version + 1 "
			+ "where s.userId = :userId and s.revokedAt is null")
	int revokeAllByUser(@Param("userId") UUID userId, @Param("reason") AuthSession.RevokeReason reason,
			@Param("now") Instant now);

	@Query("select count(s) > 0 from AuthSession s, UserAccount u "
			+ "where s.id = :sessionId and u.id = s.userId and s.revokedAt is null "
			+ "and s.expiresAt > :now and u.status = :active")
	boolean isActive(@Param("sessionId") UUID sessionId, @Param("now") Instant now,
			@Param("active") AccountStatus active);

}
