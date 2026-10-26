package damso.notification.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 학부모의 답변 도착 알림 설정. 설정 행이 없으면 기본값 켜짐(on)으로 본다 (FR-024, 클래리파이 Q3).
 */
@Entity
@Table(name = "parent_notification_setting",
		uniqueConstraints = @UniqueConstraint(name = "uk_parent_notification_setting_parent", columnNames = "parent_id"))
public class ParentNotificationSetting extends BaseEntity {

	@Column(name = "parent_id", nullable = false, updatable = false)
	private UUID parentId;

	@Column(name = "answer_notify_enabled", nullable = false)
	private boolean answerNotifyEnabled = true;

	protected ParentNotificationSetting() {
	}

	public ParentNotificationSetting(UUID parentId, boolean answerNotifyEnabled) {
		this.parentId = parentId;
		this.answerNotifyEnabled = answerNotifyEnabled;
	}

	public void changeAnswerNotifyEnabled(boolean answerNotifyEnabled) {
		this.answerNotifyEnabled = answerNotifyEnabled;
	}

	public UUID getParentId() {
		return parentId;
	}

	public boolean isAnswerNotifyEnabled() {
		return answerNotifyEnabled;
	}

}
