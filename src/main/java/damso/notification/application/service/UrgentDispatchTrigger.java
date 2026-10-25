package damso.notification.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 긴급 알림 요청이 커밋되면 바로 보낸다. 요청 스레드 밖(@Async)에서 돌려 접수 응답이 FCM 지연에 묶이지 않게 한다.
 * 실패해도 예외를 삼킨다 — 요청은 DB에 남아 있고 10초 주기 발송기가 안전망이다. 로그에는 요청 UUID만 남긴다.
 */
@Component
class UrgentDispatchTrigger {

	private static final Logger log = LoggerFactory.getLogger(UrgentDispatchTrigger.class);

	private final NotificationDispatcher dispatcher;

	UrgentDispatchTrigger(NotificationDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onUrgentNotificationRequested(UrgentNotificationRequested event) {
		try {
			dispatcher.dispatchOne(event.requestId());
		}
		catch (RuntimeException ex) {
			log.warn("긴급 알림 즉시 발송 실패, 안전망 발송기가 이어받습니다 (request={}, type={})", event.requestId(),
					ex.getClass().getSimpleName());
		}
	}

}
