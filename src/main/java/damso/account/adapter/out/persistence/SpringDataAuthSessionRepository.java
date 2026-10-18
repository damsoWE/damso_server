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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update AuthSession s set s.revokedAt = :now, s.revokeReason = :reason "
			+ "where s.userId = :userId and s.revokedAt is null")
	int revokeAllByUser(@Param("userId") UUID userId, @Param("reason") AuthSession.RevokeReason reason,
			@Param("now") Instant now);

	@Query("select count(s) > 0 from AuthSession s, UserAccount u "
			+ "where s.id = :sessionId and u.id = s.userId and s.revokedAt is null "
			+ "and s.expiresAt > :now and u.status = :active")
	boolean isActive(@Param("sessionId") UUID sessionId, @Param("now") Instant now,
			@Param("active") AccountStatus active);

}
