package damso.audit.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.audit.application.port.in.AuditLogUseCase;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;

@Service
@Transactional
public class AuditLogService implements AuditLogUseCase {

	private final AuditLogPort logs;

	private final Clock clock;

	public AuditLogService(AuditLogPort logs, Clock clock) {
		this.logs = logs;
		this.clock = clock;
	}

	@Override
	public void record(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId) {
		logs.append(AuditLog.of(actorId, action, targetType, targetId, clock.instant()));
	}

}
