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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update InviteCode c set c.status = :revoked, c.activeTargetKey = null "
			+ "where c.activeTargetKey = :key")
	int revokeActive(@Param("key") String activeTargetKey, @Param("revoked") InviteStatus revoked);

	List<InviteCode> findByClassroomIdOrderByCreatedAtAsc(UUID classroomId);

	List<InviteCode> findByStudentIdInOrderByCreatedAtAsc(Collection<UUID> studentIds);

}
