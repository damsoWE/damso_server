package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/**
 * F4 Polish / SC-005·010: 다른 학급 교사·다른 학부모·슈퍼 관리자가 답변·점검·종료·재문의 API에 접근할 수 있는
 * 경로가 0개, 점검 결과를 저장하는 테이블·컬럼이 없음, 학부모 알림 설정이 F2의 기존 알림 경로와 섞이지 않는다.
 * 운영에서는 종료 경로가 꺼져 있다(FR-031). 종료 경로를 다시 열 때를 대비해 FR-021·022 서버 검증을 계속 확인한다(PR #19 리뷰 P0).
 */
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
class AnswerExposureTest extends IntegrationTestSupport {

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId) throws Exception {
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"),
				"{\"content\":\"답변\",\"reviewed\":false}"), token);
	}

	private MockHttpServletResponse requestReview(String token, UUID inquiryId) throws Exception {
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answer-reviews"), "{\"content\":\"초안\"}"),
				token);
	}

	private MockHttpServletResponse close(String token, UUID inquiryId) throws Exception {
		return send(post("/api/v1/teacher/inquiries/" + inquiryId + "/close"), token);
	}

	@Test
	void noOtherClassroomOtherParentOrAdminCanReachAnyThreadApi() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		UUID inquiryId = confirmed.inquiryId();
		Scenario otherClassroom = teacherWithClassroom(2, 1);
		String otherTeacher = otherClassroom.teacher().accessToken();
		String otherParent = otherClassroom.parent().accessToken();
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		// 교사 전용 API: 다른 학급 교사는 404(존재 자체를 드러내지 않음), 학부모·관리자는 역할 검사에서 403
		List<MockHttpServletResponse> teacherOnly = List.of(sendAnswer(otherTeacher, inquiryId),
				sendAnswer(otherParent, inquiryId), sendAnswer(admin, inquiryId),
				requestReview(otherTeacher, inquiryId), requestReview(otherParent, inquiryId),
				requestReview(admin, inquiryId), close(otherTeacher, inquiryId), close(otherParent, inquiryId),
				close(admin, inquiryId));
		for (MockHttpServletResponse response : teacherOnly) {
			assertThat(response.getStatus()).isIn(403, 404);
		}

		// 학부모 전용 API: 본인이 아닌 학부모는 404, 교사·관리자는 역할 검사에서 403
		List<MockHttpServletResponse> parentOnly = List.of(submitFollowUp(otherParent, inquiryId, "재문의"),
				submitFollowUp(otherTeacher, inquiryId, "재문의"), submitFollowUp(admin, inquiryId, "재문의"));
		for (MockHttpServletResponse response : parentOnly) {
			assertThat(response.getStatus()).isIn(403, 404);
		}

		assertThat(jdbc.queryForObject("select count(*) from answer", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inquiry where thread_root_id is not null", Integer.class))
				.isZero();
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId))
				.isEqualTo("TEACHER_CONFIRMED");
	}

	@Test
	void noTableOrColumnPersistsReviewResults() {
		List<String> tables = jdbc.queryForList(
				"select tablename from pg_tables where schemaname = 'public' and tablename ilike '%review%'",
				String.class);
		assertThat(tables).isEmpty();
		// 다른 테이블의 컬럼으로 끼워 넣었을 가능성도 함께 본다(점검 결과 고유 필드명)
		List<String> columns = jdbc.queryForList(
				"select table_name || '.' || column_name from information_schema.columns "
						+ "where table_schema = 'public' and (column_name ilike '%highlight%' "
						+ "or column_name ilike '%excerpt%' or column_name ilike '%suggestion%')",
				String.class);
		assertThat(columns).isEmpty();
	}

	@Test
	void parentNotificationOffOnlySuppressesAnswerKindAndDoesNotAffectExistingF2Alerts() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		String parentToken = confirmed.scenario().parent().accessToken();
		// urgentInquiryConfirmedByTeacher()의 접수 과정에서 이미 URGENT 알림 요청 1건이 만들어져 있다(F2 아웃박스)
		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'URGENT'",
				Integer.class)).isEqualTo(1);
		assertThat(send(json(put("/api/v1/parent/notification-settings"), "{\"answerNotifyEnabled\":false}"),
				parentToken).getStatus()).isEqualTo(200);

		assertThat(sendAnswer(teacherToken, confirmed.inquiryId()).getStatus()).isEqualTo(201);

		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'ANSWER'",
				Integer.class)).isZero();
		// F2의 URGENT 요청은 F4의 알림 꺼짐과 무관하게 그대로 남아 있다
		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'URGENT'",
				Integer.class)).isEqualTo(1);
	}

}
