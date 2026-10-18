package damso.school.domain;

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
 * 보호자·담임 연결 이력과 탈퇴 사실. append-only다 — 수정·삭제 경로가 없다 (원칙 III, FR-038a, research R10).
 * - BaseEntity를 상속하지 않는다(수정 시각·버전이 의미 없음). id는 같은 방식(UUID v4)으로 만든다.
 * - @Immutable이라 Hibernate가 UPDATE를 내보내지 않고, setter가 없다.
 * - 개인정보(이메일·이름) 컬럼이 없다 — 계정 UUID와 대상 UUID, 종류, 시각만 남는다(원칙 VI).
 * - account_id는 FK를 걸지 않는다. 계정이 비식별화된 뒤에도 이력은 그대로 남는다.
 */
@Entity
@Immutable
@Table(name = "link_history", indexes = @Index(name = "idx_link_history_account", columnList = "account_id"))
public class LinkHistory implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 10)
	private TargetType targetType;

	@Column(name = "target_id", updatable = false)
	private UUID targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private Event event;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Transient
	private boolean isNew = true;

	protected LinkHistory() {
	}

	private LinkHistory(UUID accountId, TargetType targetType, UUID targetId, Event event, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.accountId = accountId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.event = event;
		this.occurredAt = occurredAt;
	}

	public static LinkHistory linkedToStudent(UUID accountId, UUID studentId, Instant now) {
		return new LinkHistory(accountId, TargetType.STUDENT, studentId, Event.LINKED, now);
	}

	public static LinkHistory linkedToClassroom(UUID accountId, UUID classroomId, Instant now) {
		return new LinkHistory(accountId, TargetType.CLASSROOM, classroomId, Event.LINKED, now);
	}

	public static LinkHistory unlinkedFromStudent(UUID accountId, UUID studentId, Instant now) {
		return new LinkHistory(accountId, TargetType.STUDENT, studentId, Event.UNLINKED, now);
	}

	public static LinkHistory unlinkedFromClassroom(UUID accountId, UUID classroomId, Instant now) {
		return new LinkHistory(accountId, TargetType.CLASSROOM, classroomId, Event.UNLINKED, now);
	}

	public static LinkHistory withdrawn(UUID accountId, Instant now) {
		return new LinkHistory(accountId, TargetType.NONE, null, Event.WITHDRAWN, now);
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

	public UUID getAccountId() {
		return accountId;
	}

	public TargetType getTargetType() {
		return targetType;
	}

	public UUID getTargetId() {
		return targetId;
	}

	public Event getEvent() {
		return event;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public enum TargetType {
		STUDENT,
		CLASSROOM,
		NONE
	}

	public enum Event {
		LINKED,
		UNLINKED,
		WITHDRAWN
	}

}
