package damso.audit.application.port.in;

import java.util.UUID;

import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditTargetType;

public interface AuditLogUseCase {

	/**
	 * 감사 기록을 남긴다. 호출한 쪽의 트랜잭션에 참여하므로, 기록이 실패하면 그 트랜잭션 전체가 실패한다.
	 * 원문을 돌려주는 흐름은 이 호출이 성공한 뒤에만 본문을 내보낸다.
	 */
	void record(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId);

}
