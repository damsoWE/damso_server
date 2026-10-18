package damso.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 만료·대기 계산은 이 Clock을 주입받아 쓴다. Instant.now() 직접 호출 금지 — 테스트에서 시간을 고정·이동한다. */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
