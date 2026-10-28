package damso.audit.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;

/**
 * 감사 기록 저장소. CrudRepository가 아니라 Repository를 상속해 save·조회만 선언한다 — delete·update 메서드가 없다
 *. 메서드를 추가할 때도 수정·삭제는 넣지 않는다(AuditLogAppendOnlyTest가 검사한다).
 */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

	AuditLog save(AuditLog log);

	List<AuditLog> findByActorIdOrderByOccurredAt(UUID actorId);

	List<AuditLog> findByTargetTypeAndTargetIdOrderByOccurredAt(AuditTargetType targetType, UUID targetId);

}
