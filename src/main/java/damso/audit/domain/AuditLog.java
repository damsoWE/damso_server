package damso.audit.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * 누가 언제 무엇을 열람했는지 남기는 감사 기록. append-only다 — 수정·삭제 경로가 없다 (원칙 III, research R11).
 * - BaseEntity를 상속하지 않는다(수정 시각·버전이 의미 없음). @Immutable이고 setter가 없다.
 * - 행위자·대상은 UUID뿐이다. 본문·이메일·이름 같은 개인정보 컬럼이 없다 (원칙 VI).
 * - actor_id는 FK를 걸지 않는다. 계정이 비식별화된 뒤에도 기록은 그대로 남는다.
 * F2는 기록만 하고 조회 API는 두지 않는다(F5가 서버에서만 조회).
 */
@Entity
@Immutable
@Table(name = "audit_log", indexes = {
		@Index(name = "idx_audit_actor", columnList = "actor_id, occurred_at"),
		@Index(name = "idx_audit_target", columnList = "target_type, target_id, occurred_at") })
public class AuditLog implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "actor_id", nullable = false, updatable = false)
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 20)
	private AuditTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Transient
	private boolean isNew = true;

	protected AuditLog() {
	}

	private AuditLog(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.occurredAt = occurredAt;
	}

	public static AuditLog of(UUID actorId, AuditAction action, AuditTargetType targetType, UUID targetId,
			Instant occurredAt) {
		return new AuditLog(actorId, action, targetType, targetId, occurredAt);
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		this.isNew = false;
	}

	public UUID getActorId() {
		return actorId;
	}

	public AuditAction getAction() {
		return action;
	}

	public AuditTargetType getTargetType() {
		return targetType;
	}

	public UUID getTargetId() {
		return targetId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
