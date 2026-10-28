package damso.school.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.school.domain.InviteCode;
import damso.school.domain.InviteStatus;

interface SpringDataInviteCodeRepository extends JpaRepository<InviteCode, UUID> {

	Optional<InviteCode> findByCodeHash(String codeHash);

	boolean existsByCodeHash(String codeHash);

	/** version도 올린다 — 폐기 전에 코드를 읽어 둔 사용 처리가 저장하면서 REVOKED를 USED로 덮지 못하고 버전 충돌로 실패한다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update InviteCode c set c.status = :revoked, c.activeTargetKey = null, c.version = c.version + 1 "
			+ "where c.activeTargetKey = :key")
	int revokeActive(@Param("key") String activeTargetKey, @Param("revoked") InviteStatus revoked);

	List<InviteCode> findByClassroomIdOrderByCreatedAtAsc(UUID classroomId);

	List<InviteCode> findByStudentIdInOrderByCreatedAtAsc(Collection<UUID> studentIds);

}
