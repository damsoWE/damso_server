package damso.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** F2 T041 / FR-021·022: 처리 단계는 허용된 순서로만, 한 번에 한 칸씩만 나아간다. */
class InquiryStageTest {

	private static Inquiry inquiry(boolean urgent) {
		return Inquiry.receive(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "본문",
				urgent, false, UUID.randomUUID(), "0".repeat(64), Instant.now());
	}

	@Test
	void generalInquiryFollowsReceivedRefiningConfirmedAnswered() {
		Inquiry inquiry = inquiry(false);
		assertThat(inquiry.getStage()).isEqualTo(InquiryStage.RECEIVED);

		inquiry.moveTo(InquiryStage.REFINING);
		inquiry.moveTo(InquiryStage.TEACHER_CONFIRMED);
		inquiry.moveTo(InquiryStage.ANSWERED);

		assertThat(inquiry.getStage()).isEqualTo(InquiryStage.ANSWERED);
	}

	@Test
	void urgentInquiryFollowsReceivedDeliveredConfirmedAnswered() {
		Inquiry inquiry = inquiry(true);

		inquiry.moveTo(InquiryStage.DELIVERED);
		inquiry.moveTo(InquiryStage.TEACHER_CONFIRMED);
		inquiry.moveTo(InquiryStage.ANSWERED);

		assertThat(inquiry.getStage()).isEqualTo(InquiryStage.ANSWERED);
	}

	@Test
	void generalInquiryCannotBeDeliveredOrSkipAhead() {
		assertThat(InquiryStage.RECEIVED.canMoveTo(false, InquiryStage.DELIVERED)).isFalse();
		assertThat(InquiryStage.RECEIVED.canMoveTo(false, InquiryStage.TEACHER_CONFIRMED)).isFalse();
		assertThat(InquiryStage.RECEIVED.canMoveTo(false, InquiryStage.ANSWERED)).isFalse();
		assertThat(InquiryStage.REFINING.canMoveTo(false, InquiryStage.ANSWERED)).isFalse();
		assertThatThrownBy(() -> inquiry(false).moveTo(InquiryStage.DELIVERED))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void urgentInquiryNeverGoesThroughRefining() {
		assertThat(InquiryStage.RECEIVED.canMoveTo(true, InquiryStage.REFINING)).isFalse();
		assertThat(InquiryStage.RECEIVED.canMoveTo(true, InquiryStage.TEACHER_CONFIRMED)).isFalse();
		assertThat(InquiryStage.DELIVERED.canMoveTo(true, InquiryStage.ANSWERED)).isFalse();
		assertThatThrownBy(() -> inquiry(true).moveTo(InquiryStage.REFINING)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void stagesNeverGoBackwardsOrStayInPlace() {
		for (boolean urgent : new boolean[] { false, true }) {
			for (InquiryStage from : InquiryStage.values()) {
				assertThat(from.canMoveTo(urgent, from)).as("%s→%s 같은 단계", from, from).isFalse();
				for (InquiryStage to : InquiryStage.values()) {
					if (to.ordinal() < from.ordinal()) {
						assertThat(from.canMoveTo(urgent, to)).as("%s→%s 거꾸로", from, to).isFalse();
					}
				}
			}
		}
		assertThat(InquiryStage.ANSWERED.canMoveTo(false, InquiryStage.RECEIVED)).isFalse();
		for (InquiryStage next : InquiryStage.values()) {
			assertThat(InquiryStage.ANSWERED.canMoveTo(true, next)).isFalse();
			assertThat(InquiryStage.ANSWERED.canMoveTo(false, next)).isFalse();
		}
	}

}
