package damso.school.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 교사 공개 프로필: 표시 이름 + 소개 문구(최대 60자). 사진은 없다 (FR-033).
 * 학부모가 담임을 알아보는 용도라 공개 정보이며 평문으로 둔다. 이메일은 담지 않는다 (원칙 I).
 * 교사 가입이 확정될 때 만들고, 탈퇴하면 지운다.
 */
@Entity
@Table(name = "teacher_profile", uniqueConstraints = @UniqueConstraint(name = "uk_teacher_profile_teacher",
		columnNames = "teacher_id"))
public class TeacherProfile extends BaseEntity {

	public static final int MAX_DISPLAY_NAME_LENGTH = 20;

	public static final int MAX_INTRODUCTION_LENGTH = 60;

	@Column(name = "teacher_id", nullable = false, updatable = false)
	private UUID teacherId;

	@Column(name = "display_name", nullable = false, length = MAX_DISPLAY_NAME_LENGTH)
	private String displayName;

	@Column(nullable = false, length = MAX_INTRODUCTION_LENGTH)
	private String introduction;

	protected TeacherProfile() {
	}

	public TeacherProfile(UUID teacherId, String displayName) {
		this.teacherId = teacherId;
		this.displayName = normalizeDisplayName(displayName);
		this.introduction = "";
	}

	public void update(String displayName, String introduction) {
		this.displayName = normalizeDisplayName(displayName);
		this.introduction = normalizeIntroduction(introduction);
	}

	/** 공백을 제거한 뒤 1~20자여야 한다. */
	public static String normalizeDisplayName(String displayName) {
		String trimmed = displayName == null ? "" : displayName.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
			throw new IllegalArgumentException("display name length out of range");
		}
		return trimmed;
	}

	/** 0~60자. null은 빈 문자열이다. */
	public static String normalizeIntroduction(String introduction) {
		String trimmed = introduction == null ? "" : introduction.strip();
		if (trimmed.length() > MAX_INTRODUCTION_LENGTH) {
			throw new IllegalArgumentException("introduction length out of range");
		}
		return trimmed;
	}

	public UUID getTeacherId() {
		return teacherId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getIntroduction() {
		return introduction;
	}

}
