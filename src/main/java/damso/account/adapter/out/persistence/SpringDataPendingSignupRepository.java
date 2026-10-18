package damso.account.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.account.domain.PendingSignup;

interface SpringDataPendingSignupRepository extends JpaRepository<PendingSignup, UUID> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from PendingSignup p where p.email = :email and p.completedAt is null")
	int deleteIncompleteByEmail(@Param("email") String email);

	/** 확정되었거나 코드가 만료된 뒤 before 이전인 건. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from PendingSignup p where (p.completedAt is not null and p.completedAt < :before) "
			+ "or (p.completedAt is null and p.codeExpiresAt < :before)")
	int deleteStale(@Param("before") Instant before);

}
