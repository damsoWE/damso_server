package damso.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 통합 테스트는 @Async를 동기 실행기로 바꿔 결정적으로 돌린다. 그래서 운영에서의 구조(커밋 후·요청 스레드 밖·10초 안전망)가
 * 유지되는지는 여기서 어노테이션으로 확인한다 (research R7).
 */
class UrgentDispatchTriggerStructureTest {

	@Test
	void urgentTriggerRunsAfterCommitOffTheRequestThread() throws Exception {
		Method listener = UrgentDispatchTrigger.class.getDeclaredMethod("onUrgentNotificationRequested",
				UrgentNotificationRequested.class);

		assertThat(listener.isAnnotationPresent(Async.class)).isTrue();
		TransactionalEventListener config = listener.getAnnotation(TransactionalEventListener.class);
		assertThat(config).isNotNull();
		assertThat(config.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void dispatcherSafetyNetIsScheduled() throws Exception {
		Method dueMethod = NotificationDispatcher.class.getMethod("dispatchDue");

		Scheduled scheduled = dueMethod.getAnnotation(Scheduled.class);
		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedDelayString()).contains("damso.notification.dispatch-interval");
	}

}
