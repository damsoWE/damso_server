package damso.school.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 초대 코드. 원문은 저장하지 않고 서버 키 HMAC(code_hash)만 둔다.
 * 1회용이고 발행 후 24시간 뒤 만료된다. 같은 대상에 활성 코드는 하나뿐이다:
 * active_target_key는 ISSUED일 때 "{kind}:{대상 UUID}", USED·REVOKED면 NULL이고 유니크라
 * 대상당 ISSUED 코드가 둘이 될 수 없다(PostgreSQL 유니크는 NULL끼리 충돌하지 않는다).
 * 코드 종류에 맞는 대상은 생성 메서드로만 정해진다.
 */
@Entity
@Table(name = "invite_code", uniqueConstraints = {
		@UniqueConstraint(name = "uk_invite_code_hash", columnNames = "code_hash"),
		@UniqueConstraint(name = "uk_invite_code_active_target", columnNames = "active_target_key") })
public class InviteCode extends BaseEntity {

	public static final Duration TTL = Duration.ofHours(24);

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private InviteKind kind;

	@Column(name = "classroom_id", updatable = false)
	private UUID classroomId;

	@Column(name = "student_id", updatable = false)
	private UUID studentId;

	@Column(name = "code_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
	private String codeHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private InviteStatus status;

	@Column(name = "active_target_key", length = 50)
	private String activeTargetKey;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "issued_by", nullable = false, updatable = false)
	private UUID issuedBy;

	@Column(name = "used_by")
	private UUID usedBy;

	@Column(name = "used_at")
	private Instant usedAt;

	protected InviteCode() {
	}

	private InviteCode(InviteKind kind, UUID classroomId, UUID studentId, String codeHash, UUID issuedBy, Instant now) {
		this.kind = kind;
		this.classroomId = classroomId;
		this.studentId = studentId;
		this.codeHash = codeHash;
		this.status = InviteStatus.ISSUED;
		this.activeTargetKey = activeTargetKey(kind, classroomId != null ? classroomId : studentId);
		this.expiresAt = now.plus(TTL);
		this.issuedBy = issuedBy;
	}

	public static InviteCode forTeacher(Classroom classroom, String codeHash, UUID issuer, Instant now) {
		return new InviteCode(InviteKind.TEACHER, classroom.getId(), null, codeHash, issuer, now);
	}

	public static InviteCode forParent(Student student, String codeHash, UUID issuer, Instant now) {
		return new InviteCode(InviteKind.PARENT, null, student.getId(), codeHash, issuer, now);
	}

	/** 같은 대상의 활성 코드를 찾는 키. */
	public static String activeTargetKey(InviteKind kind, UUID targetId) {
		return kind.name() + ":" + targetId;
	}

	/** 아직 쓸 수 있는가(발행됨 + 만료 전). 대상이 비어 있는지는 서비스가 따로 확인한다. */
	public boolean isUsable(Instant now) {
		return status == InviteStatus.ISSUED && expiresAt.isAfter(now);
	}

	public void markUsed(UUID userId, Instant now) {
		this.status = InviteStatus.USED;
		this.activeTargetKey = null;
		this.usedBy = userId;
		this.usedAt = now;
	}

	public void revoke() {
		this.status = InviteStatus.REVOKED;
		this.activeTargetKey = null;
	}

	/** 발행 현황에 보이는 상태: UNUSED / USED / EXPIRED / REVOKED. */
	public DisplayStatus displayStatus(Instant now) {
		return switch (status) {
			case USED -> DisplayStatus.USED;
			case REVOKED -> DisplayStatus.REVOKED;
			case ISSUED -> expiresAt.isAfter(now) ? DisplayStatus.UNUSED : DisplayStatus.EXPIRED;
		};
	}

	public enum DisplayStatus {
		UNUSED,
		USED,
		EXPIRED,
		REVOKED
	}

	public InviteKind getKind() {
		return kind;
	}

	public UUID getClassroomId() {
		return classroomId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	/** 코드가 가리키는 대상(교사 코드는 학급, 학부모 코드는 학생)의 UUID. */
	public UUID targetId() {
		return kind == InviteKind.TEACHER ? classroomId : studentId;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public InviteStatus getStatus() {
		return status;
	}

	public String getActiveTargetKey() {
		return activeTargetKey;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public UUID getIssuedBy() {
		return issuedBy;
	}

	public UUID getUsedBy() {
		return usedBy;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
