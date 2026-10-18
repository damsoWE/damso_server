package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import damso.account.application.service.SuperAdminBootstrap;
import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/** FR-001, research R12: 환경변수 둘 다 있을 때만 1회 생성, 기본 계정 없음. */
class SuperAdminBootstrapTest extends IntegrationTestSupport {

	private static final String PASSWORD = "Admin" + "Pw12345";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private SuperAdminBootstrap bootstrap(String email, String password) throws Exception {
		var constructor = SuperAdminBootstrap.class.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		return (SuperAdminBootstrap) constructor.newInstance(accountPort, encoder, email, password);
	}

	@Test
	void doesNothingWhenVariablesAreNotSet() throws Exception {
		String email = "admin-" + UUID.randomUUID() + "@damso.test";
		bootstrap("", "").run(new DefaultApplicationArguments());
		bootstrap(email, "").run(new DefaultApplicationArguments());
		assertThat(accountPort.existsByEmail(email)).isFalse();
	}

	@Test
	void createsOneSuperAdminAndDoesNotChangeItOnSecondRun() throws Exception {
		String email = "Admin-" + UUID.randomUUID() + "@Damso.test";
		bootstrap(email, PASSWORD).run(new DefaultApplicationArguments());

		UserAccount created = accountPort.findByEmail(email.toLowerCase()).orElseThrow();
		assertThat(created.getRole()).isEqualTo(Role.SUPER_ADMIN);
		assertThat(created.isActive()).isTrue();
		assertThat(created.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(encoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
		String hashBefore = created.getPasswordHash();

		bootstrap(email, "Other" + "Pw98765").run(new DefaultApplicationArguments());
		assertThat(accountPort.findByEmail(email.toLowerCase()).orElseThrow().getPasswordHash())
				.isEqualTo(hashBefore);
	}

	@Test
	void refusesToStartWithPasswordThatBreaksThePolicyWithoutPrintingIt() {
		String weak = "weakpass";
		assertThatThrownBy(() -> bootstrap("weak@damso.test", weak).run(new DefaultApplicationArguments()))
				.isInstanceOf(IllegalStateException.class)
				.message().doesNotContain(weak);
	}

	@Test
	void noSuperAdminExistsByDefault() {
		assertThat(accountPort.findByEmail("admin@damso.test")).isEmpty();
	}

}
