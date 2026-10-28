package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/**
 * 슈퍼 관리자는 학부모·교사 문의 API를 쓸 수 없고,
 * 관리자 경로에는 문의 내용·제목·처리 내용을 돌려주는 API가 없다.
 */
class SuperAdminInquiryAccessTest extends IntegrationTestSupport {

	@Test
	void adminTokenIsForbiddenOnEveryInquiryPath() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE),
				"$.inquiryId"));
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		List<String> getPaths = List.of("/api/v1/parent/inquiries", "/api/v1/parent/inquiries/" + id,
				"/api/v1/teacher/inquiries", "/api/v1/teacher/inquiries/" + id,
				"/api/v1/teacher/inquiries/" + id + "/content", "/api/v1/teacher/notification-settings");
		for (String path : getPaths) {
			assertThat(send(get(path), admin).getStatus()).as(path).isEqualTo(403);
		}
		MockHttpServletResponse submit = send(
				json(post("/api/v1/parent/inquiries"),
						"{\"studentId\":\"" + s.student().getId() + "\",\"content\":\"" + NORMAL_SAMPLE + "\"}")
						.header("Idempotency-Key", UUID.randomUUID().toString()),
				admin);
		assertThat(submit.getStatus()).isEqualTo(403);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isEqualTo(1);
	}

}
