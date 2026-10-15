package damso.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공통 기반: 무작위 테스트 시크릿 + 실제 PostgreSQL(Testcontainers) + MockMvc.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

	static {
		TestSecrets.init();
	}

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	static {
		POSTGRES.start();
	}

	@Autowired
	protected MockMvc mockMvc;

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

}
