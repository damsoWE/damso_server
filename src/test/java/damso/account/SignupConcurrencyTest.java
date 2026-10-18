package damso.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/** SC-008: 같은 초대 코드로 동시에 가입을 확정하면 정확히 1건만 성공한다. */
class SignupConcurrencyTest extends IntegrationTestSupport {

	@RepeatedTest(5)
	void onlyOneOfTwoConcurrentSignupsWithTheSameCodeSucceeds() throws Exception {
		Classroom classroom = newClassroom();
		String code = issueTeacherCode(classroom);

		String emailA = uniqueEmail();
		String emailB = uniqueEmail();
		String pendingA = read(startSignup(code, "김가", emailA), "$.pendingSignupId");
		String pendingB = read(startSignup(code, "김나", emailB), "$.pendingSignupId");
		String codeA = mail.lastSignupCode(emailA);
		String codeB = mail.lastSignupCode(emailB);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (Callable<MockHttpServletResponse> task : List.<Callable<MockHttpServletResponse>>of(
					() -> awaitThenVerify(ready, go, pendingA, codeA),
					() -> awaitThenVerify(ready, go, pendingB, codeB))) {
				futures.add(pool.submit(task));
			}
			ready.await();
			go.countDown();

			int created = 0;
			int rejected = 0;
			for (Future<MockHttpServletResponse> future : futures) {
				MockHttpServletResponse response = future.get();
				if (response.getStatus() == 201) {
					created++;
				}
				else {
					assertThat(response.getStatus()).isEqualTo(400);
					assertThat(response.getContentAsString()).contains("INVALID_INVITE_CODE");
					rejected++;
				}
			}
			assertThat(created).isEqualTo(1);
			assertThat(rejected).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from invite_code where status = 'USED'", Integer.class))
				.isEqualTo(1);
		assertThat(classroomPort.findClassroomById(classroom.getId()).orElseThrow().hasTeacher()).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from link_history", Integer.class)).isEqualTo(1);
	}

	private MockHttpServletResponse awaitThenVerify(CountDownLatch ready, CountDownLatch go, String pendingId,
			String code) throws Exception {
		ready.countDown();
		go.await();
		return verifySignup(pendingId, code);
	}

}
