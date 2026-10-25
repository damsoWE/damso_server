package damso.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** F2 T014 / FR-014, 원칙 V: 알림은 고정 문구뿐이고 이름·본문이 들어갈 자리가 없다. */
class PushMessageTest {

	@ParameterizedTest
	@EnumSource(value = NotificationKind.class, names = { "URGENT", "URGENT_REMINDER", "GENERAL" })
	void inquiryMessagesCarryFixedTextAndOnlyTheInquiryId(NotificationKind kind) {
		UUID inquiryId = UUID.randomUUID();
		PushMessage message = PushMessage.forInquiry(kind, inquiryId);

		assertThat(message.title()).isNotBlank();
		assertThat(message.body()).isNotBlank();
		assertThat(message.inquiryId()).isEqualTo(inquiryId);
		assertThat(message.digestCount()).isNull();
		// 문구는 문의 UUID나 입력값에 따라 달라지지 않는다
		assertThat(PushMessage.forInquiry(kind, UUID.randomUUID()).title()).isEqualTo(message.title());
		assertThat(PushMessage.forInquiry(kind, UUID.randomUUID()).body()).isEqualTo(message.body());
		assertThat(message.title() + message.body()).doesNotContain(inquiryId.toString());
	}

	@Test
	void digestCarriesOnlyTheCount() {
		PushMessage message = PushMessage.digest(3);

		assertThat(message.kind()).isEqualTo(NotificationKind.GENERAL_DIGEST);
		assertThat(message.digestCount()).isEqualTo(3);
		assertThat(message.inquiryId()).isNull();
		assertThat(message.body()).contains("3건");
		assertThat(PushMessage.digest(5).body()).contains("5건");
		assertThat(PushMessage.digest(5).title()).isEqualTo(message.title());
	}

	@Test
	void urgentTextDiffersFromGeneralText() {
		UUID id = UUID.randomUUID();
		assertThat(PushMessage.forInquiry(NotificationKind.URGENT, id).title())
				.isNotEqualTo(PushMessage.forInquiry(NotificationKind.GENERAL, id).title());
		assertThat(PushMessage.forInquiry(NotificationKind.URGENT_REMINDER, id).title())
				.isNotEqualTo(PushMessage.forInquiry(NotificationKind.URGENT, id).title());
	}

	@Test
	void rejectsInconsistentCombinations() {
		assertThatThrownBy(() -> PushMessage.forInquiry(NotificationKind.URGENT, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushMessage(NotificationKind.URGENT, UUID.randomUUID(), 2))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PushMessage.digest(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushMessage(NotificationKind.GENERAL_DIGEST, UUID.randomUUID(), 2))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
