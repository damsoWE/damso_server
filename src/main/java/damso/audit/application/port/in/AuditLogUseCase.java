package damso.audit.application.port.in;

import java.time.Instant;
import java.util.UUID;

import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditTargetType;

public interface AuditLogUseCase {

	/**
	 * 감사 기록을 남긴다. 호출한 쪽에 트랜잭션이 있으면 거기에 참여하고, 없으면 자기 트랜잭션으로 바로 커밋한다.
	 * 원문을 돌려주는 흐름은 이 호출이 성공한 뒤에만 본문을 내보낸다.
	 */
	void record(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId);

	/**
	 * 행위가 일어난 시각을 호출한 쪽이 정해 남긴다. 처리 트랜잭션이 커밋된 뒤에 기록하는 경로(재시도·종료)가
	 * 처리 시각을 그대로 남기려고 쓴다 — 기록 시각이 늦어지면 운영자 조회문에서 결과가 요청보다 앞에 나온다.
	 * 시각이 지금보다 미래이거나 5분보다 오래됐으면 {@link IllegalArgumentException}이고 기록하지 않는다.
	 */
	void record(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId, Instant occurredAt);

}
