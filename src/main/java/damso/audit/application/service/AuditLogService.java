package damso.audit.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

	/**
	 * 호출한 쪽이 정한 시각이 지금보다 이만큼 넘게 오래됐으면 받지 않는다. 커밋 직후 기록하는 경로(재시도·종료)는 이 범위를
	 * 넘을 일이 없다. 공개 포트로 과거로 되돌린 시각의 기록이 남는 것을 막는다.
	 */
	static final Duration MAX_OCCURRED_AT_AGE = Duration.ofMinutes(5);

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

	@Override
	public void record(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId,
			Instant occurredAt) {
		Instant now = clock.instant();
		if (occurredAt.isAfter(now) || occurredAt.isBefore(now.minus(MAX_OCCURRED_AT_AGE))) {
			throw new IllegalArgumentException("감사 기록 시각이 허용 범위를 벗어났다");
		}
		logs.append(AuditLog.of(actorId, action, targetType, targetId, occurredAt));
	}

}
