package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.audit.adapter.out.persistence.AuditLogRepository;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/**
 * 일반 문의도 정리 상태와 상관없이 원문을 열람하고, 열람마다 기록이 남는다.
 * 기록이 실패하면 원문은 나가지 않는다. 원문 확인 필요 문의만 원문 첫 열람이 "교사 확인"이다.
 */
class GeneralContentViewTest extends IntegrationTestSupport {

	/** 감사 기록 저장을 실패시킬 수 있는 포트. 이 테스트 클래스에서만 쓴다. */
	@TestConfiguration
	static class FailingAuditConfig {

		static volatile boolean failing;

		@Bean
		@Primary
		AuditLogPort failingAuditLogPort(AuditLogRepository repository) {
			return new AuditLogPort() {

				@Override
				public AuditLog append(AuditLog log) {
					if (failing) {
						throw new IllegalStateException("audit store is down");
					}
					return repository.save(log);
				}

				@Override
				public List<AuditLog> findByActor(UUID actorId) {
					return repository.findByActorIdOrderByOccurredAt(actorId);
				}

				@Override
				public List<AuditLog> findByTarget(AuditTargetType targetType, UUID targetId) {
					return repository.findByTargetTypeAndTargetIdOrderByOccurredAt(targetType, targetId);
				}

			};
		}

	}

	private UUID submitGeneral(Scenario s) throws Exception {
		return UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE),
				"$.inquiryId"));
	}

	private UUID submitFailing(Scenario s) throws Exception {
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		return submitGeneral(s);
	}

	private MockHttpServletResponse content(String token, UUID id) throws Exception {
		return send(get("/api/v1/teacher/inquiries/" + id + "/content"), token);
	}

	private String stage(UUID id) {
		return jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id);
	}

	private int history(UUID id, String stage) {
		return jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ? and stage = ?",
				Integer.class, id, stage);
	}

	@Test
	void refinedInquiryOriginalIsViewableAuditedAndDoesNotChangeTheStage() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);

		MockHttpServletResponse first = content(s.teacher().accessToken(), id);
		MockHttpServletResponse second = content(s.teacher().accessToken(), id);

		assertThat(first.getStatus()).isEqualTo(200);
		assertThat((String) read(first, "$.content")).isEqualTo(NORMAL_SAMPLE);
		assertThat(second.getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForList("select action from audit_log", String.class)).containsExactlyInAnyOrder(
				"INQUIRY_CONTENT_VIEW_ATTEMPTED", "INQUIRY_CONTENT_VIEWED", "INQUIRY_CONTENT_VIEW_ATTEMPTED",
				"INQUIRY_CONTENT_VIEWED");
		// 번역본이 있는 문의는 번역본 첫 열람이 "교사 확인"이다 — 원문 열람으로는 바뀌지 않는다
		assertThat(stage(id)).isEqualTo("REFINING");
	}

	@Test
	void needsOriginalInquiryIsConfirmedByTheFirstOriginalViewOnly() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitFailing(s);
		assertThat(stage(id)).isEqualTo("RECEIVED");

		assertThat(content(s.teacher().accessToken(), id).getStatus()).isEqualTo(200);
		assertThat(content(s.teacher().accessToken(), id).getStatus()).isEqualTo(200);

		assertThat(stage(id)).isEqualTo("TEACHER_CONFIRMED");
		assertThat(history(id, "TEACHER_CONFIRMED")).isEqualTo(1);
		assertThat(history(id, "REFINING")).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'INQUIRY_CONTENT_VIEWED'",
				Integer.class)).isEqualTo(2);
	}

	@Test
	void aLaterSuccessfulRetryAddsTheTranslationButNeverGoesBackToRefining() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitFailing(s);
		content(s.teacher().accessToken(), id);

		assertThat(send(post("/api/v1/teacher/inquiries/" + id + "/refinement/retry"), s.teacher().accessToken())
				.getStatus()).isEqualTo(202);

		assertThat(jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id))
				.isEqualTo("REFINED");
		assertThat(stage(id)).isEqualTo("TEACHER_CONFIRMED");
		assertThat(history(id, "REFINING")).isZero();
		assertThat(send(get("/api/v1/teacher/inquiries/" + id + "/refinement"), s.teacher().accessToken())
				.getStatus()).isEqualTo(200);
	}

	@Test
	void noOriginalLeavesWhenTheAuditRecordFails() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitFailing(s);
		FailingAuditConfig.failing = true;
		try {
			MockHttpServletResponse response = content(s.teacher().accessToken(), id);

			assertThat(response.getStatus()).isEqualTo(500);
			assertThat(response.getContentAsString()).doesNotContain(NORMAL_SAMPLE).doesNotContain("content");
			assertThat(stage(id)).isEqualTo("RECEIVED");
			assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
		}
		finally {
			FailingAuditConfig.failing = false;
		}
	}

	@Test
	void onlyTheCurrentHomeroomTeacherCanViewTheOriginal() throws Exception {
		Scenario s = teacherWithClassroom(1, 1);
		UUID id = submitGeneral(s);
		Classroom other = newClassroom(2, 1);
		SignedUp otherTeacher = signUpWith(issueTeacherCode(other), "정선생");

		assertThat(content(otherTeacher.accessToken(), id).getStatus()).isEqualTo(404);
		assertThat(content(s.parent().accessToken(), id).getStatus()).isEqualTo(403);
		assertThat(content(tokenFor(createAccount(Role.SUPER_ADMIN)), id).getStatus()).isEqualTo(403);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
	}

}
