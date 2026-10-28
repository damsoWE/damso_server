package damso.school.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 학급. 슈퍼 관리자가 만든다. 담임은 0~1명이고 교사와 학급은 1:1이다.
 * 담임 교사 UUID는 DB 유니크로도 한 학급에만 묶인다. 교사 탈퇴 시 NULL로 비워 담임이 없는 상태가 된다.
 * 학교는 하나로 본다(대회용 MVP). 교사 계정은 account 도메인이 소유하므로 UUID로만 가리킨다.
 */
@Entity
@Table(name = "classroom", uniqueConstraints = {
		@UniqueConstraint(name = "uk_classroom_grade_class", columnNames = { "grade", "class_number" }),
		@UniqueConstraint(name = "uk_classroom_homeroom_teacher", columnNames = "homeroom_teacher_id") })
public class Classroom extends BaseEntity {

	public static final int MIN_GRADE = 1;

	public static final int MAX_GRADE = 6;

	public static final int MIN_CLASS_NUMBER = 1;

	public static final int MAX_CLASS_NUMBER = 20;

	@Column(nullable = false, updatable = false, columnDefinition = "smallint")
	private int grade;

	@Column(name = "class_number", nullable = false, updatable = false, columnDefinition = "smallint")
	private int classNumber;

	@Column(name = "homeroom_teacher_id")
	private UUID homeroomTeacherId;

	protected Classroom() {
	}

	public Classroom(int grade, int classNumber) {
		if (grade < MIN_GRADE || grade > MAX_GRADE || classNumber < MIN_CLASS_NUMBER
				|| classNumber > MAX_CLASS_NUMBER) {
			throw new IllegalArgumentException("grade or class number out of range");
		}
		this.grade = grade;
		this.classNumber = classNumber;
	}

	public boolean hasTeacher() {
		return homeroomTeacherId != null;
	}

	/** 담임을 확정한다. 이미 담임이 있으면 거부한다. */
	public void assignTeacher(UUID teacherId) {
		if (hasTeacher()) {
			throw new IllegalStateException("classroom already has a homeroom teacher");
		}
		this.homeroomTeacherId = teacherId;
	}

	public void releaseTeacher() {
		this.homeroomTeacherId = null;
	}

	public int getGrade() {
		return grade;
	}

	public int getClassNumber() {
		return classNumber;
	}

	public UUID getHomeroomTeacherId() {
		return homeroomTeacherId;
	}

}
