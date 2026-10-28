package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/**
 * "응답 시간 차이로도 가입 여부를 드러내지 않는다".
 * 느슨한 기준(중앙값 비율 0.5~2.0)으로 "없는 이메일이라 비밀번호 비교를 건너뛰는" 실수만 잡는다.
 */
class LoginTimingTest extends IntegrationTestSupport {

	private static final int ROUNDS = 20;

	private long millis(String email, String password) throws Exception {
		long start = System.nanoTime();
		send(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"),
				null);
		return (System.nanoTime() - start) / 1_000_000;
	}

	private static long median(List<Long> values) {
		List<Long> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	@Test
	void unknownEmailAndWrongPasswordTakeSimilarTime() throws Exception {
		UserAccount account = createAccount(Role.PARENT);
		String wrong = "Wrong" + "Pass1";
		millis(uniqueEmail(), wrong); // 워밍업(BCrypt·클래스 로딩)
		millis(account.getEmail(), wrong);
		clock.advance(java.time.Duration.ofMinutes(11));

		List<Long> unknown = new ArrayList<>();
		List<Long> known = new ArrayList<>();
		for (int i = 0; i < ROUNDS; i++) {
			unknown.add(millis(uniqueEmail(), wrong)); // 매번 다른 이메일이라 대기 규칙에 걸리지 않는다
			// 같은 계정은 대기에 걸리므로 시간을 앞당기고 비교한다
			clock.advance(java.time.Duration.ofMinutes(11));
			known.add(millis(account.getEmail(), wrong));
		}

		double ratio = (double) Math.max(1, median(unknown)) / Math.max(1, median(known));
		assertThat(ratio).as("median unknown=%dms known=%dms", median(unknown), median(known))
				.isBetween(0.5, 2.0);
	}

}
