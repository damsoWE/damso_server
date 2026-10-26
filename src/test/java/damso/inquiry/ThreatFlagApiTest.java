package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * spec 005 T015 / US1 시나리오 1~9, FR-005·005a·005b·007·010, SC-002·003·006:
 * 위협·폭언 표시는 접수 때 규칙으로 정해져 교사 목록·상세에 보이고, 외부 AI 상태·번역본 내용·이후 처리와 무관하게 바뀌지 않는다.
 * spec 005 T021 / US2 시나리오 1~3, SC-001: 대표 사례 전체를 실제 접수 API로 넣어 교사 화면의 표시를 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class ThreatFlagApiTest extends IntegrationTestSupport {

	/** threat/must-flag.txt의 문장. 긴급 표현은 없다. */
	private static final String THREAT = "이런 식이면 선생님 가만두지 않겠습니다.";

	private UUID submit(Scenario s, String content) throws Exception {
		return submit(s, content, UUID.randomUUID());
	}

	private UUID submit(Scenario s, String content, UUID key) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content, key);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private boolean flagInList(Scenario s, UUID id) throws Exception {
		MockHttpServletResponse list = send(get("/api/v1/teacher/inquiries"), s.teacher().accessToken());
		List<Boolean> flags = read(list, "$.items[?(@.inquiryId=='" + id + "')].threatFlagged");
		assertThat(flags).hasSize(1);
		return flags.get(0);
	}

	private boolean flagInDetail(Scenario s, UUID id) throws Exception {
		MockHttpServletResponse detail = send(get("/api/v1/teacher/inquiries/" + id), s.teacher().accessToken());
		assertThat(detail.getStatus()).isEqualTo(200);
		return read(detail, "$.threatFlagged");
	}

	private String refinementStatus(UUID id) {
		return jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id);
	}

	@Test
	void threatInquiryIsFlaggedInTheListAndDetailAndOrdinaryOneIsNot() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID threat = submit(s, THREAT);
		UUID ordinary = submit(s, NORMAL_SAMPLE);

		assertThat(flagInList(s, threat)).isTrue();
		assertThat(flagInDetail(s, threat)).isTrue();
		assertThat(flagInList(s, ordinary)).isFalse();
		assertThat(flagInDetail(s, ordinary)).isFalse();
	}

	@Test
	void flagDoesNotDependOnTheExternalAi() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID outage = submit(s, THREAT);

		assertThat(refinementStatus(outage)).isEqualTo("NEEDS_ORIGINAL");
		assertThat(flagInList(s, outage)).isTrue();
		assertThat(flagInDetail(s, outage)).isTrue();
	}

	@Test
	void flagIsTheSameEvenWhenTranslationsDiffer() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(FakeExternalAi.refinementJson("담임 교체 요구", "학부모가 담임에게 강하게 항의했다.", "", "HIGH",
				List.of("ANGER")));
		UUID first = submit(s, THREAT);
		// 두 번째 번역본은 위협 사실을 빠뜨린다 — 표시는 번역본과 무관하다
		fakeAi.replyNext(FakeExternalAi.refinementJson("상담 요청", "학부모가 상담을 원한다.", "상담 일정", "LOW",
				List.of()));
		UUID second = submit(s, THREAT);

		assertThat(refinementStatus(first)).isEqualTo("REFINED");
		assertThat(refinementStatus(second)).isEqualTo("REFINED");
		assertThat(flagInDetail(s, first)).isTrue();
		assertThat(flagInDetail(s, second)).isTrue();
	}

	@Test
	void viewingTheTranslationOrOriginalDoesNotChangeTheFlag() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, THREAT);

		assertThat(send(get("/api/v1/teacher/inquiries/" + id + "/refinement"), s.teacher().accessToken())
				.getStatus()).isEqualTo(200);
		assertThat(send(get("/api/v1/teacher/inquiries/" + id + "/content"), s.teacher().accessToken())
				.getStatus()).isEqualTo(200);

		assertThat(flagInDetail(s, id)).isTrue();
		assertThat(jdbc.queryForObject("select threat_flagged from inquiry where id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void teacherRetryDoesNotChangeTheFlag() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID id = submit(s, THREAT);
		assertThat(refinementStatus(id)).isEqualTo("NEEDS_ORIGINAL");

		assertThat(send(post("/api/v1/teacher/inquiries/" + id + "/refinement/retry"), s.teacher().accessToken())
				.getStatus()).isEqualTo(202);

		assertThat(refinementStatus(id)).isEqualTo("REFINED");
		assertThat(flagInDetail(s, id)).isTrue();
	}

	@Test
	void urgentInquiryWithThreatWordsIsUrgentOnlyAndNotFlagged() throws Exception {
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(),
				URGENT_SAMPLE + " " + THREAT);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((Boolean) read(response, "$.urgent")).isTrue();
		UUID id = UUID.fromString(read(response, "$.inquiryId"));
		assertThat(flagInList(s, id)).isFalse();
		assertThat(flagInDetail(s, id)).isFalse();
		// 긴급 문의는 판정 자체를 하지 않는다
		assertThat(threatScreener.calls()).isZero();
	}

	@Test
	void screeningFailureStillAcceptsTheInquiryWithoutTheFlagAndLogsNoContent(CapturedOutput output)
			throws Exception {
		Scenario s = teacherWithClassroom();
		threatScreener.failNext(true);
		// 표지는 ASCII로만 둔다 — "위협" 같은 낱말은 긴급 목록에 있어 문의를 긴급으로 바꾼다
		String marker = "LOGCHECKZXQ" + "7401";
		UUID id = submit(s, THREAT + " " + marker);

		assertThat(flagInDetail(s, id)).isFalse();
		assertThat(threatScreener.calls()).isEqualTo(1);
		String logs = output.getAll();
		// 콘솔 인코딩에 따라 한글 문구가 깨질 수 있어 로거 이름과 수준으로 경고 한 줄을 확인한다
		assertThat(logs.lines().filter(line -> line.contains("WARN") && line.contains("ThreatFlagScreener")))
				.hasSize(1);
		assertThat(logs).doesNotContain(marker).doesNotContain("screening failed for")
				.doesNotContain("IllegalStateException");
	}

	@Test
	void resubmittingWithTheSameKeyReturnsTheFirstResultWithoutScreeningAgain() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID key = UUID.randomUUID();
		UUID first = submit(s, THREAT, key);
		int screened = threatScreener.calls();

		MockHttpServletResponse again = submitInquiry(s.parent().accessToken(), s.student().getId(), THREAT, key);

		assertThat(again.getStatus()).isEqualTo(201);
		assertThat((String) read(again, "$.inquiryId")).isEqualTo(first.toString());
		assertThat(threatScreener.calls()).isEqualTo(screened);
		assertThat(flagInDetail(s, first)).isTrue();
	}

	/** 파일의 문장과 줄 번호. 실패 메시지에는 본문 대신 줄 번호를 쓴다. */
	private record Case(String where, String text) {
	}

	private static List<Case> cases(String path) throws Exception {
		List<String> lines = new String(new ClassPathResource(path).getInputStream().readAllBytes(),
				StandardCharsets.UTF_8).lines().toList();
		List<Case> found = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (!line.isEmpty() && !line.startsWith("#")) {
				found.add(new Case(path + ":" + (i + 1), line));
			}
		}
		return found;
	}

	@Test
	void everyRepresentativeCaseShowsTheExpectedFlagToTheTeacher() throws Exception {
		Scenario s = teacherWithClassroom();
		List<Case> mustFlag = cases("threat/must-flag.txt");
		List<Case> mustNotFlag = cases("threat/must-not-flag.txt");

		for (Case flagged : mustFlag) {
			assertThat(flagInDetail(s, submit(s, flagged.text()))).as(flagged.where()).isTrue();
		}
		for (Case legitimate : mustNotFlag) {
			assertThat(flagInDetail(s, submit(s, legitimate.text()))).as(legitimate.where()).isFalse();
		}
		// 정당한 의사 표현과 위협 표현이 함께 있으면 위협 표현 때문에 표시된다
		UUID mixed = submit(s, "교육청에 민원 넣겠습니다. 그리고 선생님 가만두지 않겠습니다.");
		assertThat(flagInDetail(s, mixed)).isTrue();
	}

	/** spec 005 FR-005c: 재문의(F4)도 접수할 때마다 같은 규칙으로 표시를 정하고, 교사 상세의 followUps에서 보인다. */
	@Test
	void followUpsAreScreenedOneByOneAndShownInTheThreadDetail() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID root = submit(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse threat = submitFollowUp(s.parent().accessToken(), root, THREAT);
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse plain = submitFollowUp(s.parent().accessToken(), root, NORMAL_SAMPLE + " 다시");
		MockHttpServletResponse urgent = submitFollowUp(s.parent().accessToken(), root, URGENT_SAMPLE + " " + THREAT);
		assertThat(List.of(threat.getStatus(), plain.getStatus(), urgent.getStatus())).containsOnly(201);
		String threatId = read(threat, "$.inquiryId");
		String plainId = read(plain, "$.inquiryId");
		String urgentId = read(urgent, "$.inquiryId");

		MockHttpServletResponse detail = send(get("/api/v1/teacher/inquiries/" + root), s.teacher().accessToken());

		assertThat((Boolean) read(detail, "$.threatFlagged")).isFalse();
		assertThat(IntegrationTestSupport.<List<Boolean>>read(detail,
				"$.followUps[?(@.inquiryId=='" + threatId + "')].threatFlagged")).containsExactly(true);
		assertThat(IntegrationTestSupport.<List<Boolean>>read(detail,
				"$.followUps[?(@.inquiryId=='" + plainId + "')].threatFlagged")).containsExactly(false);
		assertThat(IntegrationTestSupport.<List<Boolean>>read(detail,
				"$.followUps[?(@.inquiryId=='" + urgentId + "')].threatFlagged")).containsExactly(false);
		// 재문의 응답(학부모)에는 표시가 없다
		assertThat(threat.getContentAsString()).doesNotContainIgnoringCase("threat");
	}

}
