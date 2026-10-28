package damso.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 학부모 안내는 코드뿐이고 위기 종류별 번호가 고정되어 있다. */
class GuidancePolicyTest {

	@Test
	void hotlinesFollowTheCrisisType() {
		assertThat(GuidancePolicy.hotlinesFor(CrisisType.SELF_HARM)).containsExactly(HotlineCode.SUICIDE_PREVENTION_109);
		assertThat(GuidancePolicy.hotlinesFor(CrisisType.ABUSE)).containsExactly(HotlineCode.CHILD_ABUSE_112);
		assertThat(GuidancePolicy.hotlinesFor(CrisisType.OTHER_SAFETY)).isEmpty();
	}

	@Test
	void unclassifiedUrgentInquiriesShowBothNumbers() {
		assertThat(GuidancePolicy.hotlinesFor(CrisisType.UNCLASSIFIED)).containsExactly(HotlineCode.CHILD_ABUSE_112,
				HotlineCode.SUICIDE_PREVENTION_109);
	}

	@Test
	void generalInquiriesNeverShowNumbers() {
		for (CrisisType type : CrisisType.values()) {
			assertThat(GuidancePolicy.hotlinesFor(false, type)).as(type.name()).isEmpty();
		}
		assertThat(GuidancePolicy.hotlinesFor(true, CrisisType.ABUSE)).containsExactly(HotlineCode.CHILD_ABUSE_112);
	}

	@Test
	void noticesFollowUrgencyAndDeferral() {
		assertThat(GuidancePolicy.noticesFor(false, false)).containsExactly(NoticeCode.RECEIVED);
		assertThat(GuidancePolicy.noticesFor(false, true)).containsExactly(NoticeCode.RECEIVED,
				NoticeCode.DEFERRED_TO_AVAILABLE_HOURS);
		assertThat(GuidancePolicy.noticesFor(true, false)).containsExactly(NoticeCode.URGENT_DELIVERED);
		// 긴급은 어떤 설정에도 지연 안내가 붙지 않는다
		assertThat(GuidancePolicy.noticesFor(true, true)).containsExactly(NoticeCode.URGENT_DELIVERED);
	}

}
