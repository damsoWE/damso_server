package damso.notification.adapter.out.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import damso.notification.domain.NotificationKind;
import damso.notification.domain.PushMessage;

/** F2 T025 / FR-014, 원칙 V·VI: FCM에 나가는 것은 고정 문구와 종류·문의 UUID·건수뿐이고, 로그에는 문구·UUID·토큰이 없다. */
class FcmMessageMappingTest {

	@ParameterizedTest
	@EnumSource(value = NotificationKind.class, names = { "URGENT", "URGENT_REMINDER", "GENERAL" })
	void inquiryPayloadCarriesFixedTextAndOnlyKindAndInquiryId(NotificationKind kind) {
		UUID inquiryId = UUID.randomUUID();
		PushMessage message = PushMessage.forInquiry(kind, inquiryId);

		FcmPayload payload = FcmPayload.of(message);

		assertThat(payload.title()).isEqualTo(message.title());
		assertThat(payload.body()).isEqualTo(message.body());
		assertThat(payload.data().keySet()).isEqualTo(Set.of("kind", "inquiryId"));
		assertThat(payload.data()).containsEntry("kind", kind.name()).containsEntry("inquiryId", inquiryId.toString());
		assertThat(payload.title() + payload.body()).doesNotContain(inquiryId.toString());
	}

	@Test
	void digestPayloadCarriesOnlyKindAndCount() {
		FcmPayload payload = FcmPayload.of(PushMessage.digest(3));

		assertThat(payload.data()).containsOnlyKeys("kind", "count");
		assertThat(payload.data()).containsEntry("kind", "GENERAL_DIGEST").containsEntry("count", "3");
		assertThat(payload.highPriority()).isFalse();
	}

	@Test
	void onlyUrgentKindsAreHighPriority() {
		UUID id = UUID.randomUUID();
		assertThat(FcmPayload.of(PushMessage.forInquiry(NotificationKind.URGENT, id)).highPriority()).isTrue();
		assertThat(FcmPayload.of(PushMessage.forInquiry(NotificationKind.URGENT_REMINDER, id)).highPriority()).isTrue();
		assertThat(FcmPayload.of(PushMessage.forInquiry(NotificationKind.GENERAL, id)).highPriority()).isFalse();
	}

	@Test
	void payloadAndSenderToStringHideTextIdsAndCredentials() {
		UUID inquiryId = UUID.randomUUID();
		FcmPayload payload = FcmPayload.of(PushMessage.forInquiry(NotificationKind.URGENT, inquiryId));
		FcmPushSender sender = new FcmPushSender("credentials-secret-xyz");

		assertThat(payload.toString()).doesNotContain(inquiryId.toString()).doesNotContain(payload.title())
				.doesNotContain(payload.body());
		assertThat(sender.toString()).doesNotContain("credentials-secret-xyz");
	}

	@Test
	void sdkMessageCanBeBuiltWithoutCallingFcm() {
		FcmPayload payload = FcmPayload.of(PushMessage.forInquiry(NotificationKind.URGENT, UUID.randomUUID()));

		assertThatCode(() -> FcmPushSender.toMessage("device-token", payload)).doesNotThrowAnyException();
	}

}
