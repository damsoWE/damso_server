package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/** US4 시나리오 6~10, FR-025·025a·025b·026·027, SC-007: 기기별 세션, 갱신 토큰 회전·재사용 탐지, 14일 만료. */
class SessionApiTest extends IntegrationTestSupport {

	private record Tokens(String access, String refresh) {
	}

	private Tokens loginAs(UserAccount account) throws Exception {
		MockHttpServletResponse response = send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + TEST_PASSWORD + "\"}"), null);
		assertThat(response.getStatus()).isEqualTo(200);
		return new Tokens(read(response, "$.accessToken"), read(response, "$.refreshToken"));
	}

	private MockHttpServletResponse refresh(String refreshToken) throws Exception {
		return send(json(post("/api/v1/auth/token/refresh"), "{\"refreshToken\":\"" + refreshToken + "\"}"), null);
	}

	private int protectedStatus(String accessToken) throws Exception {
		return send(get("/test/auth/any"), accessToken).getStatus();
	}

	@Test
	void refreshRotatesTheRefreshTokenAndTheOldOneIsUnusable() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens device = loginAs(parent);

		MockHttpServletResponse rotated = refresh(device.refresh());

		assertThat(rotated.getStatus()).isEqualTo(200);
		assertThat((String) read(rotated, "$.refreshToken")).isNotEqualTo(device.refresh());
		assertThat((String) read(rotated, "$.role")).isEqualTo("PARENT");
		assertThat(protectedStatus(read(rotated, "$.accessToken"))).isEqualTo(200);
		assertThat(rotated.getHeader("Set-Cookie")).isNull();
		assertThat(jdbc.queryForObject("select count(*) from refresh_token where used_at is not null",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void reusingARotatedTokenRevokesOnlyThatDevice() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens deviceA = loginAs(parent);
		Tokens deviceB = loginAs(parent);

		MockHttpServletResponse rotated = refresh(deviceA.refresh());
		String newAccess = read(rotated, "$.accessToken");
		String newRefresh = read(rotated, "$.refreshToken");

		MockHttpServletResponse reuse = refresh(deviceA.refresh()); // 이미 쓴 갱신 토큰

		assertThat(reuse.getStatus()).isEqualTo(401);
		assertThat(reuse.getContentAsString()).contains("UNAUTHORIZED");
		// A 세션의 접근·갱신 토큰은 모두 쓸 수 없다
		assertThat(protectedStatus(deviceA.access())).isEqualTo(401);
		assertThat(protectedStatus(newAccess)).isEqualTo(401);
		assertThat(refresh(newRefresh).getStatus()).isEqualTo(401);
		// B는 정상이다
		assertThat(protectedStatus(deviceB.access())).isEqualTo(200);
		assertThat(refresh(deviceB.refresh()).getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from auth_session where revoke_reason = 'REUSE_DETECTED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void logoutEndsOnlyThisDevice() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens deviceA = loginAs(parent);
		Tokens deviceB = loginAs(parent);

		MockHttpServletResponse logout = send(post("/api/v1/auth/logout"), deviceA.access());

		assertThat(logout.getStatus()).isEqualTo(204);
		assertThat(protectedStatus(deviceA.access())).isEqualTo(401); // 접근 토큰이 만료되기 전에도 즉시
		assertThat(refresh(deviceA.refresh()).getStatus()).isEqualTo(401);
		assertThat(protectedStatus(deviceB.access())).isEqualTo(200);
		assertThat(refresh(deviceB.refresh()).getStatus()).isEqualTo(200);
	}

	@Test
	void logoutRequiresAuthentication() throws Exception {
		assertThat(send(post("/api/v1/auth/logout"), null).getStatus()).isEqualTo(401);
	}

	@Test
	void sessionExpiresFourteenDaysAfterTheLastRefresh() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens device = loginAs(parent);

		clock.advance(Duration.ofDays(13));
		MockHttpServletResponse extended = refresh(device.refresh()); // 14일 안에 갱신하면 연장된다
		assertThat(extended.getStatus()).isEqualTo(200);
		String nextRefresh = read(extended, "$.refreshToken");

		clock.advance(Duration.ofDays(13)); // 처음 로그인 후 26일이지만 마지막 갱신 후 13일
		MockHttpServletResponse extendedAgain = refresh(nextRefresh);
		assertThat(extendedAgain.getStatus()).isEqualTo(200);
		String lastRefresh = read(extendedAgain, "$.refreshToken");

		clock.advance(Duration.ofDays(14).plusSeconds(1)); // 14일 넘게 쓰지 않음
		assertThat(refresh(lastRefresh).getStatus()).isEqualTo(401);
	}

	@Test
	void expiredSessionAlsoRejectsTheAccessToken() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens device = loginAs(parent);

		clock.advance(Duration.ofDays(14).plusSeconds(1));

		assertThat(refresh(device.refresh()).getStatus()).isEqualTo(401);
		assertThat(protectedStatus(device.access())).isEqualTo(401);
	}

	@Test
	void unknownAndMalformedRefreshTokensAreUnauthorized() throws Exception {
		assertThat(refresh("not-a-real-token").getStatus()).isEqualTo(401);
		assertThat(send(json(post("/api/v1/auth/token/refresh"), "{}"), null).getStatus()).isEqualTo(400);
	}

	@Test
	void refreshTokensAreStoredOnlyAsHashes() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens device = loginAs(parent);

		String stored = jdbc.queryForObject("select token_hash from refresh_token", String.class);

		assertThat(stored).hasSize(64).isNotEqualTo(device.refresh()).doesNotContain(device.refresh());
	}

	@Test
	void withdrawnAccountCannotRefresh() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		Tokens device = loginAs(parent);
		parent.withdraw(clock.instant());
		accountPort.save(parent);

		assertThat(refresh(device.refresh()).getStatus()).isEqualTo(401);
		assertThat(protectedStatus(device.access())).isEqualTo(401);
	}

}
