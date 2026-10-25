package damso.audit.application.port.in;

import java.util.UUID;

import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditTargetType;

public interface AuditLogUseCase {

	/**
	 * 감사 기록을 남긴다. 호출한 쪽에 트랜잭션이 있으면 거기에 참여하고, 없으면 자기 트랜잭션으로 바로 커밋한다.
	 * 원문을 돌려주는 흐름은 이 호출이 성공한 뒤에만 본문을 내보낸다.
	 */
	void record(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId);

}
