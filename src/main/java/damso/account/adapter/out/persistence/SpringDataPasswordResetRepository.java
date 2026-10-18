package damso.account.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.account.domain.PasswordReset;

interface SpringDataPasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

	Optional<PasswordReset> findByUserId(UUID userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from PasswordReset r where r.userId = :userId")
	int deleteByUserId(@Param("userId") UUID userId);

	/** 사용되었거나 만료된 뒤 before 이전인 건. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from PasswordReset r where (r.usedAt is not null and r.usedAt < :before) "
			+ "or (r.usedAt is null and r.expiresAt < :before)")
	int deleteStale(@Param("before") Instant before);

}
