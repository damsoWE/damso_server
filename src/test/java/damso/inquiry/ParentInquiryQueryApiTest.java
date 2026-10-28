package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * 학부모가 본인 문의의 처리 단계·안내를 본다.
 * F3 이후 일반 문의는 커밋 직후 정리되므로, 이 클래스는 정리 확정 전 상태(접수 단계)를 보도록 즉시 실행을 끈다.
 * 정제·교사 확인 단계는 ParentRefinementTimelineTest가 확인한다.
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class ParentInquiryQueryApiTest extends IntegrationTestSupport {

	private UUID submit(Scenario s, String content) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	@Test
	void listShowsOnlyMyInquiriesNewestFirstWithoutContent() throws Exception {
		Scenario s = teacherWithClassroom();
		Student others = newStudent(s.classroom(), "이둘");
		SignedUp otherParent = signUpWith(issueParentCode(others), "정부모");
		UUID older = submit(s, NORMAL_SAMPLE + " 먼저");
		UUID newer = submit(s, NORMAL_SAMPLE + " 나중");
		submitInquiry(otherParent.accessToken(), others.getId(), NORMAL_SAMPLE + " 다른 학부모");

		MockHttpServletResponse response = send(get("/api/v1/parent/inquiries"), s.parent().accessToken());

		assertThat(response.getStatus()).isEqualTo(200);
		List<String> ids = read(response, "$[*].inquiryId");
		assertThat(ids).containsExactly(newer.toString(), older.toString());
		assertThat((String) read(response, "$[0].studentName")).isEqualTo("김하나");
		assertThat((String) read(response, "$[0].stage")).isEqualTo("RECEIVED");
		assertThat(response.getContentAsString()).doesNotContain("content").doesNotContain("준비물")
				.doesNotContain("다른 학부모");
	}

	@Test
	void generalDetailShowsContentTimelineAndNotices() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, NORMAL_SAMPLE);

		MockHttpServletResponse response = send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((String) read(response, "$.content")).isEqualTo(NORMAL_SAMPLE);
		assertThat((String) read(response, "$.studentName")).isEqualTo("김하나");
		assertThat((Boolean) read(response, "$.urgent")).isFalse();
		assertThat((String) read(response, "$.stage")).isEqualTo("RECEIVED");
		List<String> stages = read(response, "$.timeline[*].stage");
		assertThat(stages).containsExactly("RECEIVED");
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("RECEIVED");
		List<String> hotlines = read(response, "$.hotlines");
		assertThat(hotlines).isEmpty();
		// 교사 이메일·이름은 어디에도 없다
		assertThat(response.getContentAsString()).doesNotContain(s.teacher().email()).doesNotContain("최선생")
				.doesNotContain("@");
	}

	@Test
	void urgentTimelineFollowsDeliveredThenTeacherConfirmed() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);

		MockHttpServletResponse before = send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken());
		List<String> beforeStages = read(before, "$.timeline[*].stage");
		assertThat(beforeStages).containsExactly("RECEIVED", "DELIVERED");
		assertThat((String) read(before, "$.stage")).isEqualTo("DELIVERED");
		List<String> notices = read(before, "$.notices");
		assertThat(notices).containsExactly("URGENT_DELIVERED");
		List<String> hotlines = read(before, "$.hotlines");
		assertThat(hotlines).containsExactly("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");

		clock.advance(Duration.ofMinutes(5));
		assertThat(send(get("/api/v1/teacher/inquiries/" + id + "/content"), s.teacher().accessToken()).getStatus())
				.isEqualTo(200);

		MockHttpServletResponse after = send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken());
		List<String> afterStages = read(after, "$.timeline[*].stage");
		assertThat(afterStages).containsExactly("RECEIVED", "DELIVERED", "TEACHER_CONFIRMED");
		assertThat((String) read(after, "$.stage")).isEqualTo("TEACHER_CONFIRMED");
		List<String> times = read(after, "$.timeline[*].occurredAt");
		assertThat(times).hasSize(3);
		assertThat(Instant.parse(times.get(2))).isAfterOrEqualTo(Instant.parse(times.get(1)));
		assertThat(Instant.parse(times.get(1))).isAfterOrEqualTo(Instant.parse(times.get(0)));
	}

	@Test
	void anotherParentsInquiryIsNotFound() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, NORMAL_SAMPLE);
		Student others = newStudent(s.classroom(), "이둘");
		SignedUp otherParent = signUpWith(issueParentCode(others), "정부모");

		MockHttpServletResponse notMine = send(get("/api/v1/parent/inquiries/" + id), otherParent.accessToken());
		MockHttpServletResponse unknown = send(get("/api/v1/parent/inquiries/" + UUID.randomUUID()),
				otherParent.accessToken());

		assertThat(notMine.getStatus()).isEqualTo(404);
		assertThat(notMine.getContentAsString()).isEqualTo(unknown.getContentAsString());
	}

	@Test
	void teachersAndAdminsCannotUseTheParentApi() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, NORMAL_SAMPLE);
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		for (String path : List.of("/api/v1/parent/inquiries", "/api/v1/parent/inquiries/" + id)) {
			assertThat(send(get(path), s.teacher().accessToken()).getStatus()).as(path).isEqualTo(403);
			assertThat(send(get(path), admin).getStatus()).as(path).isEqualTo(403);
			assertThat(send(get(path), null).getStatus()).as(path).isEqualTo(401);
		}
	}

	@Test
	void originalContentSurvivesParentWithdrawal() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, NORMAL_SAMPLE);

		MockHttpServletResponse withdrawn = send(
				json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + TEST_PASSWORD + "\"}"),
				s.parent().accessToken());

		assertThat(withdrawn.getStatus()).isIn(200, 204);
		assertThat(jdbc.queryForObject("select count(*) from inquiry where id = ?", Integer.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select content from inquiry where id = ?", String.class, id)).isNotBlank();
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ?",
				Integer.class, id)).isEqualTo(1);
		Classroom classroom = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		assertThat(classroom.getHomeroomTeacherId()).isEqualTo(s.teacher().userId());
	}

}
