package damso.account.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.PasswordPolicy;
import damso.account.domain.UserAccount;

/**
 * 슈퍼 관리자는 서버가 직접 만든다 — 앱에는 생성 경로가 없다.
 * 환경변수 DAMSO_SUPER_ADMIN_EMAIL·DAMSO_SUPER_ADMIN_PASSWORD가 둘 다 있을 때만, 그 이메일 계정이 없으면 한 번 만든다.
 * 이미 있으면 아무것도 바꾸지 않는다. 변수가 없으면 관리자는 생기지 않는다(기본 계정 없음).
 * 비밀번호가 정책에 맞지 않으면 기동을 거부한다. 메시지에 값은 넣지 않는다.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

	static final String ADMIN_NAME = "관리자";

	private final UserAccountPort accounts;

	private final PasswordEncoder passwordEncoder;

	private final String email;

	private final String password;

	SuperAdminBootstrap(UserAccountPort accounts, PasswordEncoder passwordEncoder,
			@Value("${damso.super-admin.email:}") String email,
			@Value("${damso.super-admin.password:}") String password) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			return;
		}
		if (!PasswordPolicy.isValid(password)) {
			throw new IllegalStateException("DAMSO_SUPER_ADMIN_PASSWORD(비밀번호 정책 위반)");
		}
		String normalized = UserAccount.normalizeEmail(email);
		if (accounts.existsByEmail(normalized)) {
			return;
		}
		accounts.save(UserAccount.superAdmin(normalized, passwordEncoder.encode(password), ADMIN_NAME));
		log.info("super admin account created");
	}

}
