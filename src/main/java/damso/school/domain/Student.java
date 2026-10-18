package damso.school.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import damso.common.crypto.SensitiveText;
import damso.common.entity.BaseEntity;

/**
 * 학생. 이름은 아동 개인정보이자 F3 가명화 사전의 원천이라 SensitiveText로 암호화 저장한다 (research R13).
 * 보호자는 0~1명이다(FR-020). 학부모 탈퇴 시 NULL로 비운다. 같은 학급의 동명이인은 허용하고 구분은 UUID로 한다.
 */
@Entity
@Table(name = "student", indexes = @Index(name = "idx_student_classroom", columnList = "classroom_id"))
public class Student extends BaseEntity {

	public static final int MAX_NAME_LENGTH = 20;

	@Column(name = "classroom_id", nullable = false, updatable = false)
	private UUID classroomId;

	@Column(columnDefinition = "TEXT", nullable = false)
	private SensitiveText name;

	@Column(name = "guardian_id")
	private UUID guardianId;

	protected Student() {
	}

	public Student(UUID classroomId, String name) {
		this.classroomId = classroomId;
		this.name = SensitiveText.of(normalizeName(name));
	}

	/** 공백을 제거한 뒤 1~20자여야 한다. */
	public static String normalizeName(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("student name length out of range");
		}
		return trimmed;
	}

	public boolean hasGuardian() {
		return guardianId != null;
	}

	/** 보호자를 확정한다. 이미 보호자가 있으면 거부한다(학생 1명당 보호자 1명). */
	public void assignGuardian(UUID parentId) {
		if (hasGuardian()) {
			throw new IllegalStateException("student already has a guardian");
		}
		this.guardianId = parentId;
	}

	public void releaseGuardian() {
		this.guardianId = null;
	}

	public UUID getClassroomId() {
		return classroomId;
	}

	public SensitiveText getName() {
		return name;
	}

	public UUID getGuardianId() {
		return guardianId;
	}

}
