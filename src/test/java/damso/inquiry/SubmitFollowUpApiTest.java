package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/**
 * F4 US4 시나리오 1~3·7~8 / F2 FR-004, FR-014~016·015a, SC-006·011: 재문의 제출 —
 * 본문 규칙·중복 확인·대상 재확인(현재 담임)·1분 제한(최초 문의와 같은 버킷 공유)·긴급 재문의의 즉시 접수.
 */
class SubmitFollowUpApiTest extends IntegrationTestSupport {

	private UUID submitRoot(Scenario s, String content) throws Exception {
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private int followUpCount() {
		return jdbc.queryForObject("select count(*) from inquiry where thread_root_id is not null", Integer.class);
	}

	@Test
	void followUpIsAddedToTheSameThread() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));

		MockHttpServletResponse response = submitFollowUp(s.parent().accessToken(), rootId,
				"추가로 궁금한 점이 있어요.");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((String) read(response, "$.threadRootId")).isEqualTo(rootId.toString());
		String followUpId = read(response, "$.inquiryId");
		assertThat(followUpId).isNotEqualTo(rootId.toString());
		assertThat((Boolean) read(response, "$.urgent")).isFalse();
		assertThat((String) read(response, "$.submittedAt")).isNotBlank();
		Map<String, Object> row = jdbc.queryForMap("select thread_root_id from inquiry where id = ?",
				UUID.fromString(followUpId));
		assertThat(row.get("thread_root_id")).isEqualTo(rootId);
	}

	@Test
	void contentRulesMatchTheInitialInquiry() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		String token = s.parent().accessToken();

		for (String bad : List.of("", "   ", "가".repeat(2001))) {
			MockHttpServletResponse response = submitFollowUp(token, rootId, bad);
			assertThat(response.getStatus()).as("[%d chars]", bad.length()).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
		assertThat(followUpCount()).isZero();
	}

	@Test
	void sameIdempotencyKeyRetryIsTheSameResultButDifferentContentIsAConflict() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		String token = s.parent().accessToken();
		UUID key = UUID.randomUUID();

		MockHttpServletResponse first = submitFollowUp(token, rootId, "같은 내용", key);
		MockHttpServletResponse retry = submitFollowUp(token, rootId, "같은 내용", key);
		MockHttpServletResponse conflict = submitFollowUp(token, rootId, "다른 내용", key);

		assertThat(first.getStatus()).isEqualTo(201);
		assertThat(retry.getStatus()).isEqualTo(201);
		assertThat((String) read(retry, "$.inquiryId")).isEqualTo(read(first, "$.inquiryId"));
		assertThat(conflict.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(conflict.getContentAsString(), "CONFLICT");
		assertThat(followUpCount()).isEqualTo(1);
	}

	@Test
	void secondGeneralFollowUpWithinAMinuteSharesTheInitialInquiryBucket() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		String token = s.parent().accessToken();

		MockHttpServletResponse limited = submitFollowUp(token, rootId, "바로 이어 보냄");

		assertThat(limited.getStatus()).isEqualTo(429);
		assertSafeErrorResponse(limited.getContentAsString(), "TOO_MANY_ATTEMPTS");
		assertThat(followUpCount()).isZero();

		clock.advance(Duration.ofSeconds(61));
		assertThat(submitFollowUp(token, rootId, "다시 보냄").getStatus()).isEqualTo(201);
	}

	@Test
	void urgentFollowUpIsAcceptedWithinAMinuteAndDeliveredImmediately() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		String token = s.parent().accessToken();

		long start = System.nanoTime();
		MockHttpServletResponse response = submitFollowUp(token, rootId, URGENT_SAMPLE);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((Boolean) read(response, "$.urgent")).isTrue();
		assertThat(elapsedMillis).as("SC-006 3초 이내").isLessThan(3_000);
		String followUpId = read(response, "$.inquiryId");
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class,
				UUID.fromString(followUpId))).isEqualTo("DELIVERED");
	}

	@Test
	void notOwnParentOrDifferentClassroomIsNotFound() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		Scenario other = teacherWithClassroom(2, 1);

		MockHttpServletResponse byOtherParent = submitFollowUp(other.parent().accessToken(), rootId, "제 문의 아님");

		assertThat(byOtherParent.getStatus()).isEqualTo(404);
		assertThat(followUpCount()).isZero();
	}

	@Test
	void classroomWithoutHomeroomTeacherIsRejectedWithoutStoring() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);

		MockHttpServletResponse response = submitFollowUp(s.parent().accessToken(), rootId, "담임이 없어요");

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "NO_HOMEROOM_TEACHER");
		assertThat(followUpCount()).isZero();
	}

	@Test
	void followUpGoesToTheNewHomeroomTeacherAfterReplacement() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);
		SignedUp newTeacher = signUpWith(issueTeacherCode(s.classroom()), "새선생");

		MockHttpServletResponse response = submitFollowUp(s.parent().accessToken(), rootId, "새 담임에게 전달되나요");

		assertThat(response.getStatus()).isEqualTo(201);
		String followUpId = read(response, "$.inquiryId");
		assertThat(jdbc.queryForObject("select teacher_id from inquiry where id = ?", UUID.class,
				UUID.fromString(followUpId))).isEqualTo(newTeacher.userId());
	}

}
