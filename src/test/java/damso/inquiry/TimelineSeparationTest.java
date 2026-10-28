package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import damso.audit.domain.AuditAction;
import damso.inquiry.domain.InquiryStage;
import damso.support.IntegrationTestSupport;

/**
 * 감사 기록이 넓어져도 학부모·교사 상세의 타임라인에는 처리 단계만 나오고, 두 화면이 같은 목록이다.
 * 앱 어디에도 감사 기록을 돌려주는 경로가 없다.
 */
class TimelineSeparationTest extends IntegrationTestSupport {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	private MockHttpServletResponse ok(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isBetween(200, 299);
		return response;
	}

	@Test
	void bothTimelinesShowOnlyStagesAndNoAuditActions() throws Exception {
		Scenario s = teacherWithClassroom();
		String teacher = s.teacher().accessToken();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID id = UUID.fromString(read(ok(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE)),
				"$.inquiryId"));
		// 원문 확인 필요 → 교사 재시도 → 정리 성공 → 번역본·원문 열람 → 점검 요청
		ok(send(post("/api/v1/teacher/inquiries/" + id + "/refinement/retry"), teacher));
		ok(send(get("/api/v1/teacher/inquiries/" + id + "/refinement"), teacher));
		ok(send(get("/api/v1/teacher/inquiries/" + id + "/content"), teacher));
		ok(send(json(post("/api/v1/teacher/inquiries/" + id + "/answer-reviews"), "{\"content\":\"확인 중입니다.\"}"),
				teacher));
		assertThat(jdbc.queryForList("select distinct action from audit_log", String.class)).containsExactlyInAnyOrder(
				"REFINEMENT_RETRY_REQUESTED", "INQUIRY_REFINEMENT_VIEW_ATTEMPTED", "INQUIRY_REFINEMENT_VIEWED",
				"INQUIRY_CONTENT_VIEW_ATTEMPTED", "INQUIRY_CONTENT_VIEWED", "ANSWER_REVIEW_REQUESTED");

		MockHttpServletResponse parentDetail = ok(send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken()));
		MockHttpServletResponse teacherDetail = ok(send(get("/api/v1/teacher/inquiries/" + id), teacher));

		List<String> parentStages = read(parentDetail, "$.timeline[*].stage");
		List<String> teacherStages = read(teacherDetail, "$.timeline[*].stage");
		Set<String> stageNames = Set.copyOf(Arrays.stream(InquiryStage.values()).map(Enum::name).toList());
		assertThat(parentStages).isNotEmpty().allMatch(stageNames::contains);
		assertThat(teacherStages).isEqualTo(parentStages);
		assertThat(parentStages).containsExactly("RECEIVED", "REFINING", "TEACHER_CONFIRMED");
		// 타임라인 항목은 단계와 시각뿐이다
		List<java.util.Map<String, Object>> parentEvents = read(parentDetail, "$.timeline[*]");
		assertThat(parentEvents).allSatisfy(event -> assertThat(event.keySet())
				.containsExactlyInAnyOrder("stage", "occurredAt"));
		for (MockHttpServletResponse detail : List.of(parentDetail, teacherDetail)) {
			String body = detail.getContentAsString();
			for (AuditAction action : AuditAction.values()) {
				assertThat(body).doesNotContain(action.name());
			}
			assertThat(body).doesNotContain("_VIEW").doesNotContain("_REQUESTED").doesNotContain("actorId");
		}
	}

	@Test
	void noAppRouteReturnsAuditRecords() {
		List<String> apiPaths = mappings.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream()).filter(path -> path.startsWith("/api/"))
				.toList();

		assertThat(apiPaths).isNotEmpty();
		assertThat(apiPaths).noneMatch(path -> path.toLowerCase().contains("audit"))
				.noneMatch(path -> path.toLowerCase().contains("history"));
	}

}
