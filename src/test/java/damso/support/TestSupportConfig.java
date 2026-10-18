package damso.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트 전용 빈: 시간을 옮길 수 있는 Clock, 실제로 보내지 않는 메일 포트. */
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

}
