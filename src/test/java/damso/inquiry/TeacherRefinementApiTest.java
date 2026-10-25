package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/**
 * F3 T029 / US2 시나리오 1~9, FR-011·012·021·025~027, SC-008·012: 정리에 성공한 일반 문의를 교사가 번역본으로 본다.
 * 원래 이름으로 보이고 토큰은 없다. 첫 열람이 "교사 확인"이고, 번역본 열람은 감사 기록 대상이 아니다.
 */
class TeacherRefinementApiTest extends IntegrationTestSupport {

	private UUID submitGeneral(Scenario s) throws Exception {
		return UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s)), "$.inquiryId"));
	}

	private MockHttpServletResponse refinement(String token, UUID id) throws Exception {
		return send(get("/api/v1/teacher/inquiries/" + id + "/refinement"), token);
	}

	private int historyCount(UUID id, String stage) {
		return jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ? and stage = ?",
				Integer.class, id, stage);
	}

	@Test
	void teacherSeesTheRestoredTranslationWithoutTokens() throws Exception {
		Scenario s = teacherWithClassroom(3, 2);
		fakeAi.replyNext("{\"title\":\"[STUDENT_1] 숙제 문의\",\"facts\":\"[CLASS_1] [STUDENT_1]이 숙제를 자주 잊는다.\","
				+ "\"requests\":\"[TEACHER_1] 선생님이 확인해 주기를 바란다.\",\"emotionLevel\":\"MEDIUM\","
				+ "\"emotionLabels\":[\"WORRY\"],\"threatToTeacher\":false,\"threatToStudent\":false,"
				+ "\"abuseTowardTeacher\":false,\"abuseTowardStudent\":false}");
		UUID id = submitGeneral(s);

		MockHttpServletResponse response = refinement(s.teacher().accessToken(), id);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((String) read(response, "$.inquiryId")).isEqualTo(id.toString());
		assertThat((String) read(response, "$.title")).isEqualTo(STUDENT_NAME + " 숙제 문의");
		assertThat((String) read(response, "$.facts")).isEqualTo("3학년 2반 " + STUDENT_NAME + "이 숙제를 자주 잊는다.");
		assertThat((String) read(response, "$.requests")).isEqualTo(TEACHER_NAME + " 선생님이 확인해 주기를 바란다.");
		assertThat((String) read(response, "$.emotionLevel")).isEqualTo("MEDIUM");
		assertThat(IntegrationTestSupport.<List<String>>read(response, "$.emotionLabels")).containsExactly("WORRY");
		assertThat((String) read(response, "$.refinedAt")).isNotBlank();
		assertThat(response.getContentAsString()).doesNotContain("[STUDENT_").doesNotContain("[CLASS_")
				.doesNotContain("[TEACHER_").doesNotContain("@");
		// 정리 결과는 원문처럼 암호화해 저장한다
		assertThat(jdbc.queryForObject("select facts from inquiry_refinement where inquiry_id = ?", String.class, id))
				.doesNotContain(STUDENT_NAME).doesNotContain("숙제");
	}

	@Test
	void theTranslationNeverCarriesThePhoneNumberEvenIfTheAiKeepsItsToken() throws Exception {
		// 원칙 I: 외부 AI가 [PHONE_1]을 결과에 남겨도 번역본에는 고정 문구만 저장·표시된다
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext("{\"title\":\"[STUDENT_1] 다툼 문의\",\"facts\":\"[STUDENT_1] 보호자가 [PHONE_1]로 연락해 달라고 했다.\","
				+ "\"requests\":\"\",\"emotionLevel\":\"LOW\",\"emotionLabels\":[],\"threatToTeacher\":false,"
				+ "\"threatToStudent\":false,\"abuseTowardTeacher\":false,\"abuseTowardStudent\":false}");
		UUID id = submitGeneral(s);

		MockHttpServletResponse response = refinement(s.teacher().accessToken(), id);

		assertThat((String) read(response, "$.facts")).isEqualTo(STUDENT_NAME + " 보호자가 (연락처 생략)로 연락해 달라고 했다.");
		assertThat(response.getContentAsString()).doesNotContain("010-1234-5678").doesNotContain("[PHONE_");
	}

	@Test
	void listAndDetailShowTheRefinedTitleAndStatus() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(damso.support.FakeExternalAi.refinementJson("준비물 문의", "준비물을 묻는다", "", "LOW",
				List.of()));
		UUID id = submitGeneral(s);
		clock.advance(java.time.Duration.ofSeconds(61));
		UUID urgent = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE),
				"$.inquiryId"));

		MockHttpServletResponse list = send(get("/api/v1/teacher/inquiries"), s.teacher().accessToken());
		MockHttpServletResponse detail = send(get("/api/v1/teacher/inquiries/" + id), s.teacher().accessToken());

		assertThat(IntegrationTestSupport.<List<String>>read(list, "$.items[*].inquiryId")).containsExactly(urgent.toString(), id.toString());
		assertThat((String) read(list, "$.items[1].title")).isEqualTo("준비물 문의");
		assertThat((String) read(list, "$.items[1].refinementStatus")).isEqualTo("REFINED");
		assertThat((String) read(list, "$.items[1].stage")).isEqualTo("REFINING");
		assertThat((Object) read(list, "$.items[0].refinementStatus")).isNull();
		assertThat((String) read(detail, "$.title")).isEqualTo("준비물 문의");
		assertThat((String) read(detail, "$.refinementStatus")).isEqualTo("REFINED");
		assertThat((Boolean) read(detail, "$.retryAvailable")).isFalse();
		assertThat(detail.getContentAsString()).doesNotContain("준비물을 묻는다");
	}

	@Test
	void firstTranslationViewConfirmsOnceAndIsNotAudited() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id)).isEqualTo("REFINING");

		assertThat(refinement(s.teacher().accessToken(), id).getStatus()).isEqualTo(200);
		assertThat(refinement(s.teacher().accessToken(), id).getStatus()).isEqualTo(200);

		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("TEACHER_CONFIRMED");
		assertThat(historyCount(id, "REFINING")).isEqualTo(1);
		assertThat(historyCount(id, "TEACHER_CONFIRMED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
	}

	@Test
	void concurrentFirstViewsConfirmOnlyOnce() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return refinement(s.teacher().accessToken(), id);
				}));
			}
			start.countDown();
			for (Future<MockHttpServletResponse> future : futures) {
				assertThat(future.get().getStatus()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(historyCount(id, "TEACHER_CONFIRMED")).isEqualTo(1);
	}

	@Test
	void notReadyWhenThereIsNoTranslation() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID failed = submitGeneral(s);
		clock.advance(java.time.Duration.ofSeconds(61));
		UUID urgent = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE),
				"$.inquiryId"));

		for (UUID id : List.of(failed, urgent)) {
			MockHttpServletResponse response = refinement(s.teacher().accessToken(), id);
			assertThat(response.getStatus()).isEqualTo(409);
			assertSafeErrorResponse(response.getContentAsString(), "REFINEMENT_NOT_READY");
		}
	}

	@Test
	void onlyTheCurrentHomeroomTeacherCanSeeTheTranslation() throws Exception {
		Scenario s = teacherWithClassroom(1, 1);
		UUID id = submitGeneral(s);
		Classroom other = newClassroom(2, 1);
		SignedUp otherTeacher = signUpWith(issueTeacherCode(other), "정선생");

		MockHttpServletResponse foreign = refinement(otherTeacher.accessToken(), id);
		MockHttpServletResponse parent = refinement(s.parent().accessToken(), id);
		MockHttpServletResponse admin = refinement(tokenFor(createAccount(Role.SUPER_ADMIN)), id);

		assertThat(foreign.getStatus()).isEqualTo(404);
		assertSafeErrorResponse(foreign.getContentAsString(), "NOT_FOUND");
		assertThat(parent.getStatus()).isEqualTo(403);
		assertThat(admin.getStatus()).isEqualTo(403);
		assertThat(foreign.getContentAsString() + parent.getContentAsString() + admin.getContentAsString())
				.doesNotContain(STUDENT_NAME);
	}

}
