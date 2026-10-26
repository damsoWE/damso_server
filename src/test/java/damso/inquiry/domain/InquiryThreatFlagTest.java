package damso.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** spec 005 T008 / FR-005a·007: 표시는 접수 때 한 번 정해지고, 긴급 문의에는 붙지 않으며, 바꾸는 길이 없다. */
class InquiryThreatFlagTest {

	private static Inquiry inquiry(boolean urgent, boolean threatFlagged) {
		return Inquiry.receive(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "본문",
				urgent, threatFlagged, UUID.randomUUID(), "0".repeat(64), Instant.now());
	}

	@Test
	void generalInquiryKeepsTheFlagItWasReceivedWith() {
		assertThat(inquiry(false, true).isThreatFlagged()).isTrue();
		assertThat(inquiry(false, false).isThreatFlagged()).isFalse();
	}

	@Test
	void urgentInquiryIsNeverFlagged() {
		assertThat(inquiry(true, true).isThreatFlagged()).isFalse();
	}

	@Test
	void noPublicMethodChangesTheFlag() {
		Method[] changers = Arrays.stream(Inquiry.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.filter(method -> method.getName().toLowerCase().contains("threat"))
				.filter(method -> !method.getName().equals("isThreatFlagged"))
				.toArray(Method[]::new);

		assertThat(changers).isEmpty();
	}

}
