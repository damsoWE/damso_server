package damso.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/** 이슈 #26: 같은 계정의 재설정 코드 발급이 동시에 돌아도 재설정 건은 1개만 남고, 남은 건은 마지막으로 보낸 코드다. */
class PasswordResetConcurrentIssueTest extends IntegrationTestSupport {

	@Autowired
	private PasswordResetIssuer issuer;

	@Test
	void concurrentIssuesForTheSameAccountLeaveOneReset() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		// 메일 단계(저장 뒤, 커밋 전)에 먼저 닿은 발급은 다른 발급도 닿을 때까지(최대 1초) 기다린다.
		// 잠금이 없으면 두 발급이 서로의 저장 전 행을 못 본 채 함께 이 단계에 머문다. 잠금이 있으면 두 번째는 첫 번째 커밋까지 막혀 있다
		CountDownLatch bothAtMail = new CountDownLatch(2);
		AtomicInteger atMail = new AtomicInteger();
		AtomicInteger maxAtMail = new AtomicInteger();
		mail.beforeResetSend(() -> {
			maxAtMail.accumulateAndGet(atMail.incrementAndGet(), Math::max);
			bothAtMail.countDown();
			try {
				bothAtMail.await(1, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			atMail.decrementAndGet();
		});

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<?>> runs = List.of(
					pool.submit(() -> issuer.onPasswordResetRequested(new PasswordResetRequested(email))),
					pool.submit(() -> issuer.onPasswordResetRequested(new PasswordResetRequested(email))));
			for (Future<?> run : runs) {
				run.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		// @Async void 메서드의 예외는 호출한 쪽으로 오지 않으므로, 발급이 한 번에 하나씩 돌았는지를 직접 본다
		assertThat(maxAtMail.get()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from password_reset where user_id = ?", Long.class,
				parent.getId())).isEqualTo(1L);
		assertThat(mail.sendCount(email)).isEqualTo(2);
		String body = "{\"email\":\"" + email + "\",\"code\":\"" + mail.lastResetCode(email) + "\"}";
		assertThat(send(json(post("/api/v1/auth/password-reset/verify"), body), null).getStatus()).isEqualTo(204);
	}

	@Test
	void schemaAllowsOneResetPerUser() {
		String definition = jdbc.queryForObject(
				"select indexdef from pg_indexes where indexname = 'idx_password_reset_user'", String.class);

		assertThat(definition).startsWith("CREATE UNIQUE INDEX");
	}

}
