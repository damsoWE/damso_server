package damso.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 메일 발송을 요청 스레드 밖에서 처리해 응답 시간에 메일 서버 지연이 드러나지 않게 한다. */
@Configuration
@EnableAsync
public class AsyncConfig {

}
