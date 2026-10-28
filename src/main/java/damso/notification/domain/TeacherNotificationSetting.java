package damso.notification.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 교사의 알림 설정. 일반 알림을 끌 수 있다(문의는 목록에 그대로 쌓인다). 긴급 알림 항목은 없다 — 끌 수 없다.
 * 설정 행이 없으면 일반 알림 켜짐 + 항상 응답 가능으로 본다.
 */
@Entity
@Table(name = "teacher_notification_setting",
		uniqueConstraints = @UniqueConstraint(name = "uk_notification_setting_teacher", columnNames = "teacher_id"))
public class TeacherNotificationSetting extends BaseEntity {

	@Column(name = "teacher_id", nullable = false, updatable = false)
	private UUID teacherId;

	@Column(name = "general_enabled", nullable = false)
	private boolean generalEnabled = true;

	protected TeacherNotificationSetting() {
	}

	public TeacherNotificationSetting(UUID teacherId, boolean generalEnabled) {
		this.teacherId = teacherId;
		this.generalEnabled = generalEnabled;
	}

	public void changeGeneralEnabled(boolean generalEnabled) {
		this.generalEnabled = generalEnabled;
	}

	public UUID getTeacherId() {
		return teacherId;
	}

	public boolean isGeneralEnabled() {
		return generalEnabled;
	}

}
