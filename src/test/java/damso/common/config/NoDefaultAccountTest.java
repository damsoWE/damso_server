package damso.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import damso.support.IntegrationTestSupport;

/** FR-017: 기본 계정·기본 비밀번호가 없다. */
class NoDefaultAccountTest extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void noUserDetailsServiceIsRegistered() {
		assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

}
