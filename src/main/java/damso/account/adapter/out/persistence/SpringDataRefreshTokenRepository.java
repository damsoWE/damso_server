package damso.account.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.account.domain.RefreshToken;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 조건부 회전: used_at이 비어 있을 때만 사용 처리한다. 갱신된 행 수(0 또는 1)를 돌려준다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
	int markUsed(@Param("id") UUID id, @Param("now") Instant now);

}
