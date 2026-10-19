package damso.notification.application.service;

import java.util.UUID;

/** 긴급 알림 요청이 커밋됐으니 바로 보내라는 알림. 요청 UUID만 담는다. */
record UrgentNotificationRequested(UUID requestId) {
}
