package damso.audit.application.port.out;

import java.util.List;
import java.util.UUID;

import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;

/** 감사 기록은 추가와 조회만 있다. 수정·삭제 메서드를 만들지 않는다. */
public interface AuditLogPort {

	AuditLog append(AuditLog log);

	List<AuditLog> findByActor(UUID actorId);

	List<AuditLog> findByTarget(AuditTargetType targetType, UUID targetId);

}
