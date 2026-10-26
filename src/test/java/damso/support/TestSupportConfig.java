package damso.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/** 통합 테스트 전용 빈: 시간을 옮길 수 있는 Clock, 실제로 보내지 않는 메일·푸시·외부 AI 포트. */
@TestConfiguration
public class TestSupportConfig {

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock();
	}

	@Bean
	@Primary
	FakeVerificationMailPort fakeVerificationMailPort() {
		return new FakeVerificationMailPort();
	}

	/**
	 * @Async를 호출한 스레드에서 바로 실행한다. 긴급 알림의 커밋 직후 발송이 테스트에서 결정적으로 끝난다.
	 * (@Async 자체는 UrgentDispatchTrigger 구조 테스트가 확인한다)
	 */
	@Bean
	@Primary
	TaskExecutor applicationTaskExecutor() {
		return new SyncTaskExecutor();
	}

	@Bean
	@Primary
	FakePushSender fakePushSender() {
		return new FakePushSender();
	}

	/** 외부 AI에 접속하지 않는다. 받은 가린 본문을 기록하고 응답을 테스트가 정한다 (F3). */
	@Bean
	@Primary
	FakeExternalAi fakeExternalAi() {
		return new FakeExternalAi();
	}

}
