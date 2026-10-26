package damso.notification.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotNull;

/** 학부모 답변 도착 알림 설정 저장 요청. */
public record ParentNotificationSettingsRequestDTO(@NotNull Boolean answerNotifyEnabled) {
}
