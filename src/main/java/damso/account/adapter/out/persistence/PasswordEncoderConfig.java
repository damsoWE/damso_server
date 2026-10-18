package damso.account.adapter.out.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호는 BCrypt(cost 10, 솔트 내장)로만 저장한다 (research R2, 체크리스트 보안기능 16).
 * UserDetailsService는 등록하지 않는다 — 기본 계정이 생기지 않는다 (NoDefaultAccountTest).
 */
@Configuration
class PasswordEncoderConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(10);
	}

}
