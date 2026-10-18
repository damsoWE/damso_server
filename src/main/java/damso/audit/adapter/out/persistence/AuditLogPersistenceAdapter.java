package damso.audit.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;

@Component
class AuditLogPersistenceAdapter implements AuditLogPort {

	private final AuditLogRepository logs;

	AuditLogPersistenceAdapter(AuditLogRepository logs) {
		this.logs = logs;
	}

	@Override
	public AuditLog append(AuditLog log) {
		return logs.save(log);
	}

	@Override
	public List<AuditLog> findByActor(UUID actorId) {
		return logs.findByActorIdOrderByOccurredAt(actorId);
	}

	@Override
	public List<AuditLog> findByTarget(AuditTargetType targetType, UUID targetId) {
		return logs.findByTargetTypeAndTargetIdOrderByOccurredAt(targetType, targetId);
	}

}
