package damso.notification.adapter.in.controller.dto.response;

/** 학부모 답변 도착 알림 설정. 설정 행이 없으면 기본값 켜짐(true)으로 보인다. */
public record ParentNotificationSettingsResponseDTO(boolean answerNotifyEnabled) {
}
