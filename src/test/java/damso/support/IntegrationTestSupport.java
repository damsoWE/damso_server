package damso.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.application.service.SessionIssuer;
import damso.account.domain.AuthSession;
import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.school.application.port.in.InviteIssueUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.domain.Classroom;
import damso.school.domain.Student;

/**
 * 통합 테스트 공통 기반: 무작위 테스트 시크릿 + 실제 PostgreSQL·Redis(Testcontainers) + MockMvc.
 * 시간은 MutableClock, 메일은 FakeVerificationMailPort로 바꿔 두고, 테스트마다 모든 테이블을 비운다.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@Import(TestSupportConfig.class)
public abstract class IntegrationTestSupport {

	/** 테스트 계정 공통 비밀번호(정책 통과). 실제 비밀번호가 아니라 테스트 데이터다. */
	protected static final String TEST_PASSWORD = "Passw0rd" + "9x";

	static {
		TestSecrets.init();
	}

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	private static volatile String encodedTestPassword;

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected UserAccountPort accountPort;

	@Autowired
	protected SessionPort sessionPort;

	@Autowired
	protected SessionIssuer sessionIssuer;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected StringRedisTemplate redis;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected FakeVerificationMailPort mail;

	@Autowired
	protected ClassroomPort classroomPort;

	@Autowired
	protected StudentPort studentPort;

	@Autowired
	protected InviteIssueUseCase inviteIssue;

	@BeforeEach
	void resetSharedTestState() {
		truncateAllTables();
		Set<String> keys = redis.keys("damso:attempt:*");
		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
		clock.reset();
		mail.clear();
	}

	/** 테스트마다 모든 테이블을 비워 서로 영향을 주지 않게 한다(학년·반 유니크 등 충돌 방지). */
	private void truncateAllTables() {
		List<String> tables = jdbc.queryForList("select tablename from pg_tables where schemaname = 'public'",
				String.class);
		if (!tables.isEmpty()) {
			jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList())
					+ " CASCADE");
		}
	}

	// ---- HTTP 헬퍼 ----

	/** 본문이 JSON인 요청으로 만든다. */
	protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	/** 요청을 보낸다. accessToken이 null이면 Authorization 헤더를 붙이지 않는다. */
	protected MockHttpServletResponse send(MockHttpServletRequestBuilder request, String accessToken) throws Exception {
		if (accessToken != null) {
			request.header("Authorization", bearer(accessToken));
		}
		return mockMvc.perform(request).andReturn().getResponse();
	}

	/** 응답 본문에서 JSONPath로 값을 꺼낸다. */
	protected static <T> T read(MockHttpServletResponse response, String path) throws Exception {
		return JsonPath.read(response.getContentAsString(), path);
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@damso.test";
	}

	// ---- 계정·토큰 ----

	/** 활성 계정을 만든다(비밀번호는 TEST_PASSWORD). */
	protected UserAccount createAccount(Role role) {
		return createAccount(role, uniqueEmail());
	}

	protected UserAccount createAccount(Role role, String email) {
		String hash = encodedTestPassword();
		UserAccount account = role == Role.SUPER_ADMIN
				? UserAccount.superAdmin(email, hash, "관리자")
				: UserAccount.signUp(email, hash, "테스트", role, clock.instant());
		return accountPort.save(account);
	}

	/** 실제 세션을 만들어 접근 토큰을 돌려준다. */
	protected String tokenFor(UserAccount account) {
		return sessionIssuer.issueNewSession(account).accessToken();
	}

	/**
	 * 살아 있는 세션을 가리키는 sid가 들어간 유효 토큰. role은 임의 값이어도 된다(허용 목록 밖 역할 테스트용).
	 * foundation 테스트가 "유효한 토큰"이 필요할 때 쓴다.
	 */
	protected String validToken(String role) {
		Role owner = role.equals("TEACHER") ? Role.TEACHER : Role.PARENT;
		UserAccount account = createAccount(owner);
		AuthSession session = sessionPort.saveSession(
				new AuthSession(account.getId(), clock.instant(), Duration.ofDays(14)));
		return TestJwtIssuer.valid(role, session.getId());
	}

	private String encodedTestPassword() {
		String encoded = encodedTestPassword;
		if (encoded == null) {
			encoded = passwordEncoder.encode(TEST_PASSWORD);
			encodedTestPassword = encoded;
		}
		return encoded;
	}

	protected Clock appClock() {
		return clock;
	}

	// ---- 학급·학생·초대 코드·가입 흐름 ----

	protected Classroom newClassroom() {
		return newClassroom(1, 1);
	}

	protected Classroom newClassroom(int grade, int classNumber) {
		return classroomPort.saveClassroom(new Classroom(grade, classNumber));
	}

	protected Student newStudent(Classroom classroom, String name) {
		return studentPort.saveStudent(new Student(classroom.getId(), name));
	}

	/** 관리자가 발행한 것과 같은 경로로 교사 초대 코드 원문을 얻는다. */
	protected String issueTeacherCode(Classroom classroom) {
		return inviteIssue.issueTeacherInvite(classroom.getId(), UUID.randomUUID()).code();
	}

	protected String issueParentCode(Student student) {
		return inviteIssue.issueParentInvite(student.getId(), UUID.randomUUID()).code();
	}

	protected static String signupBody(String inviteCode, String name, String email, String password,
			boolean termsAgreed) {
		return "{\"inviteCode\":\"" + inviteCode + "\",\"name\":\"" + name + "\",\"email\":\"" + email
				+ "\",\"password\":\"" + password + "\",\"termsAgreed\":" + termsAgreed + "}";
	}

	/** 가입 1단계: 초대 코드 검증 + 인증 코드 발송. */
	protected MockHttpServletResponse startSignup(String inviteCode, String name, String email) throws Exception {
		return send(json(post("/api/v1/auth/signup"), signupBody(inviteCode, name, email, TEST_PASSWORD, true)), null);
	}

	/** 가입 2단계: 인증 코드 확인. */
	protected MockHttpServletResponse verifySignup(String pendingSignupId, String code) throws Exception {
		return send(json(post("/api/v1/auth/signup/verify"),
				"{\"pendingSignupId\":\"" + pendingSignupId + "\",\"code\":\"" + code + "\"}"), null);
	}

	/** 가입 완료된 사용자. */
	protected record SignedUp(String email, UUID userId, String accessToken, String refreshToken) {
	}

	/** 초대 코드로 1·2단계를 모두 거쳐 가입한다. 보내진 이메일 인증 코드를 가짜 메일 포트에서 읽는다. */
	protected SignedUp signUpWith(String inviteCode, String name) throws Exception {
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(inviteCode, name, email);
		if (started.getStatus() != 202) {
			throw new AssertionError("signup start failed: " + started.getStatus());
		}
		MockHttpServletResponse verified = verifySignup(read(started, "$.pendingSignupId"),
				mail.lastSignupCode(email));
		if (verified.getStatus() != 201) {
			throw new AssertionError("signup verify failed: " + verified.getStatus());
		}
		UserAccount account = accountPort.findByEmail(email).orElseThrow();
		return new SignedUp(email, account.getId(), read(verified, "$.accessToken"),
				read(verified, "$.refreshToken"));
	}

}
