package damso.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquirySummary;

/**
 * 이슈 #28: 교사 목록 항목의 needsAnswer — 답변이 없거나 마지막 재문의가 마지막 답변보다 늦으면 true.
 * SQL 필터(answerStatus)와 같은 기준인지는 TeacherInquiryAnswerStatusFilterTest가 본다.
 */
class TeacherInquirySummaryTest {

	private static final Instant AT = Instant.parse("2026-10-29T01:00:00Z");

	private static InquirySummary summary(Instant lastAnsweredAt, Instant lastFollowUpAt) {
		return new InquirySummary(UUID.randomUUID(), UUID.randomUUID(), "", false, "제목", null, null, false, false,
				AT.minusSeconds(600), lastAnsweredAt, lastFollowUpAt);
	}

	@Test
	void needsAnswerWithoutAnyAnswer() {
		assertThat(summary(null, null).needsAnswer()).isTrue();
		assertThat(summary(null, AT).needsAnswer()).isTrue();
	}

	@Test
	void answeredWithoutFollowUpDoesNotNeedAnswer() {
		assertThat(summary(AT, null).needsAnswer()).isFalse();
	}

	@Test
	void followUpAfterTheLastAnswerNeedsAnswer() {
		assertThat(summary(AT, AT.plusNanos(1_000)).needsAnswer()).isTrue();
	}

	@Test
	void followUpBeforeTheLastAnswerDoesNotNeedAnswer() {
		assertThat(summary(AT, AT.minusSeconds(60)).needsAnswer()).isFalse();
	}

	@Test
	void followUpAtTheSameInstantAsTheAnswerDoesNotNeedAnswer() {
		assertThat(summary(AT, AT).needsAnswer()).isFalse();
	}

}
