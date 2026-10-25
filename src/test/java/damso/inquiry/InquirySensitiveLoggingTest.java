package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.audit.adapter.out.persistence.AuditLogRepository;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;
import damso.notification.application.service.NotificationDispatcher;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * F2 / SC-006, FR-008, 원칙 VI: 문의 제출(일반·긴급)·열람·알림 발송·기기 등록과 실패 경로(1분 제한 거부, 푸시 실패,
 * 감사 실패) 전체에서 문의 본문·제목, FCM 토큰, 학생 이름이 로그에 남지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class InquirySensitiveLoggingTest extends IntegrationTestSupport {

	private static final String GENERAL_BODY = "로그검사일반본문ZXQ" + "7391";

	private static final String URGENT_BODY = "로그검사긴급본문자해ZXQ" + "7392";

	private static final String STUDENT_NAME = "로그확인학생";

	private static final String FCM_TOKEN = "fcm-token-SECRET-" + "ZXQ7393";

	/** 감사 기록을 실패시킬 수 있는 포트. 이 테스트 클래스에서만 쓴다. */
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

	@Autowired
	private NotificationDispatcher dispatcher;

	@Test
	void inquiryTextTokensAndStudentNamesNeverAppearInTheLogs(CapturedOutput output) throws Exception {
		List<String> secrets = new ArrayList<>(List.of(GENERAL_BODY, URGENT_BODY, STUDENT_NAME, FCM_TOKEN));

		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, STUDENT_NAME);
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "로그선생");
		SignedUp parent = signUpWith(issueParentCode(student), "로그부모");

		// 기기 등록(성공·실패 경로)
		send(json(put("/api/v1/me/devices"), "{\"token\":\"" + FCM_TOKEN + "\",\"platform\":\"ANDROID\"}"),
				teacher.accessToken());
		send(json(put("/api/v1/me/devices"), "{\"token\":\"\",\"platform\":\"ANDROID\"}"), teacher.accessToken());

		// 일반 문의 제출, 1분 제한 거부, 같은 키 충돌
		UUID key = UUID.randomUUID();
		MockHttpServletResponse general = submitInquiry(parent.accessToken(), student.getId(), GENERAL_BODY, key);
		submitInquiry(parent.accessToken(), student.getId(), GENERAL_BODY + "추가"); // 429
		submitInquiry(parent.accessToken(), student.getId(), GENERAL_BODY + "다른내용", key); // 409
		submitInquiry(parent.accessToken(), UUID.randomUUID(), GENERAL_BODY); // 404
		submitInquiry(parent.accessToken(), student.getId(), "   "); // 400

		// 긴급 문의 제출과 푸시 실패·재시도 경로
		pushes.failAll(true);
		MockHttpServletResponse urgent = submitInquiry(parent.accessToken(), student.getId(), URGENT_BODY);
		pushes.throwAll(true);
		clock.advance(Duration.ofMinutes(2));
		dispatcher.dispatchDue();
		pushes.throwAll(false);
		pushes.failAll(false);
		clock.advance(Duration.ofMinutes(10));
		dispatcher.dispatchDue();
		clock.advance(Duration.ofHours(2));
		dispatcher.dispatchDue();

		// 조회·열람(성공), 일반 문의 열람 거부, 남의 문의
		String urgentId = read(urgent, "$.inquiryId");
		String generalId = read(general, "$.inquiryId");
		send(get("/api/v1/teacher/inquiries"), teacher.accessToken());
		send(get("/api/v1/teacher/inquiries/" + urgentId), teacher.accessToken());
		send(get("/api/v1/teacher/inquiries/" + urgentId + "/content"), teacher.accessToken());
		send(get("/api/v1/teacher/inquiries/" + generalId + "/content"), teacher.accessToken());
		send(get("/api/v1/parent/inquiries"), parent.accessToken());
		send(get("/api/v1/parent/inquiries/" + urgentId), parent.accessToken());
		send(get("/api/v1/parent/inquiries/" + UUID.randomUUID()), parent.accessToken());

		// 감사 기록 실패 경로
		FailingAuditConfig.failing = true;
		try {
			send(get("/api/v1/teacher/inquiries/" + urgentId + "/content"), teacher.accessToken());
		}
		finally {
			FailingAuditConfig.failing = false;
		}
		send(delete("/api/v1/me/devices/current"), teacher.accessToken());

		String logs = output.getAll();
		for (String secret : secrets) {
			assertThat(logs).as("secret of length %d", secret.length()).doesNotContain(secret);
		}
		// 의미 있는 실패 로그는 남아 있다(요청 UUID·종류만)
		assertThat(logs).contains("Unhandled exception");
	}

	/**
	 * F3 T054 / SC-002·009, 원칙 II·VI: 정리 성공·형식 위반·응답 없음·교사 재시도·위기 분류·번역본·원문 열람 경로 전체에서
	 * 문의 본문, 가린 본문, 정리 결과(제목·사실관계·요청사항), 학생·보호자·교사 이름, 전화번호가 로그에 남지 않는다.
	 */
	@Test
	void refinementPathsNeverLogContentMaskedTextResultsOrNames(CapturedOutput output) throws Exception {
		String parentName = "로그보호자" + "ZXQ";
		String teacherName = "로그담임" + "ZXQ";
		String phone = "010-9876-" + "5432";
		String body = "로그검사정리본문ZXQ" + "7394 " + STUDENT_NAME + " " + phone;
		String refinedTitle = "로그정리제목ZXQ" + "7395";
		String refinedFacts = "로그정리사실ZXQ" + "7396";
		String refinedRequests = "로그정리요청ZXQ" + "7397";
		List<String> secrets = List.of(body, STUDENT_NAME, parentName, teacherName, phone, refinedTitle, refinedFacts,
				refinedRequests, "[STUDENT_1]", "[PHONE_1]");

		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, STUDENT_NAME);
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), teacherName);
		SignedUp parent = signUpWith(issueParentCode(student), parentName);

		// 정리 성공 → 번역본·원문 열람
		fakeAi.replyNext(damso.support.FakeExternalAi.refinementJson(refinedTitle, refinedFacts + " [STUDENT_1]",
				refinedRequests, "LOW", List.of()));
		String refined = read(submitInquiry(parent.accessToken(), student.getId(), body), "$.inquiryId");
		send(get("/api/v1/teacher/inquiries"), teacher.accessToken());
		send(get("/api/v1/teacher/inquiries/" + refined + "/refinement"), teacher.accessToken());
		send(get("/api/v1/teacher/inquiries/" + refined + "/content"), teacher.accessToken());

		// 형식 위반 → 자동 재시도도 응답 없음 → 원문 확인 필요 → 교사 재시도(예외)
		clock.advance(Duration.ofSeconds(61));
		fakeAi.replyNext("{\"title\":\"" + refinedTitle + "\",\"facts\":\"" + refinedFacts + "\"}");
		fakeAi.noResponseNext();
		String failed = read(submitInquiry(parent.accessToken(), student.getId(), body + " 2"), "$.inquiryId");
		send(get("/api/v1/teacher/inquiries/" + failed + "/refinement"), teacher.accessToken());
		fakeAi.throwNext();
		send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/v1/teacher/inquiries/" + failed + "/refinement/retry"), teacher.accessToken());
		send(get("/api/v1/teacher/inquiries/" + failed + "/content"), teacher.accessToken());

		// 긴급 문의의 위기 분류(성공·실패)
		fakeAi.replyNext(damso.support.FakeExternalAi.crisisJson("ABUSE"));
		submitInquiry(parent.accessToken(), student.getId(), URGENT_BODY + " " + STUDENT_NAME);
		fakeAi.replyNext("{\"crisisType\":\"" + refinedTitle + "\"}");
		submitInquiry(parent.accessToken(), student.getId(), URGENT_BODY + " 다시 " + phone);

		String logs = output.getAll();
		for (String secret : secrets) {
			assertThat(logs).as("secret of length %d", secret.length()).doesNotContain(secret);
		}
		// 정리 결과와 실패는 작업 UUID·결과 코드로만 남는다
		assertThat(logs).contains("result=REFINED").contains("result=NEEDS_ORIGINAL").contains("result=CLASSIFIED")
				.contains("result=UNCLASSIFIED");
	}

}
