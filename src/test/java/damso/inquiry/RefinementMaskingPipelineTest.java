package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.support.FakeExternalAi.Call;
import damso.support.IntegrationTestSupport;

/**
 * 일반 문의는 접수와 같은 트랜잭션에 정리 작업이 남고, 커밋 뒤 가린 글만
 * 외부 AI로 나간다. 긴급 문의는 정리하지 않는다. (정리 확정 전 상태·동시 실행·임대 만료는 RefinementDispatchTest)
 */
class RefinementMaskingPipelineTest extends IntegrationTestSupport {

	@Test
	void onlyMaskedTextLeavesForTheExternalAi() throws Exception {
		Scenario s = teacherWithClassroom(3, 2);
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s)), "$.inquiryId"));

		List<Call> calls = fakeAi.callsOf(AiTask.REFINE);
		assertThat(calls).hasSize(1);
		String sent = calls.get(0).userText();
		assertThat(sent).doesNotContain(STUDENT_NAME).doesNotContain("하나")
				.doesNotContain(TEACHER_NAME).doesNotContain(PARENT_NAME)
				.doesNotContain("한양초").doesNotContain("3학년").doesNotContain("2반")
				.doesNotContain("010-1234-5678");
		assertThat(sent).contains("[STUDENT_1]").contains("[TEACHER_1]").contains("[GUARDIAN_1]")
				.contains("[SCHOOL_1]").contains("[CLASS_1]").contains("[PHONE_1]");
		// 시스템 지시와 본문은 따로 간다 — 지시에 본문이 이어 붙지 않는다
		assertThat(calls.get(0).systemPrompt()).doesNotContain("숙제를 자꾸 잊어버려서").contains("emotionLabels");
		assertThat(jdbc.queryForObject("select count(*) from refinement_job where inquiry_id = ? and kind = 'REFINE'",
				Integer.class, id)).isEqualTo(1);
	}

	@Test
	void sameNameIsMaskedWithTheSameTokenEveryTime() throws Exception {
		Scenario s = teacherWithClassroom();
		submitInquiry(s.parent().accessToken(), s.student().getId(),
				STUDENT_NAME + "가 오늘 아팠어요. " + STUDENT_NAME + " 약을 챙겨 주세요.");

		assertThat(fakeAi.callsOf(AiTask.REFINE).get(0).userText())
				.isEqualTo("[STUDENT_1]가 오늘 아팠어요. [STUDENT_1] 약을 챙겨 주세요.");
	}

	@Test
	void urgentInquiriesAreNotRefined() throws Exception {
		Scenario s = teacherWithClassroom();
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(fakeAi.callsOf(AiTask.REFINE)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from refinement_job where kind = 'REFINE'", Integer.class))
				.isZero();
	}

	@Test
	void triggerRunsAfterCommitOutsideTheRequestThread() throws Exception {
		Class<?> trigger = Class.forName("damso.inquiry.application.service.RefinementDispatchTrigger");
		Method listener = trigger.getDeclaredMethod("onRefinementRequested",
				Class.forName("damso.inquiry.application.service.RefinementRequested"));

		assertThat(listener.isAnnotationPresent(Async.class)).isTrue();
		assertThat(listener.getAnnotation(TransactionalEventListener.class).phase())
				.isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

}
