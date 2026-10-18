package damso.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 만료된 가입 대기·재설정 건 정리 같은 주기 작업. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
