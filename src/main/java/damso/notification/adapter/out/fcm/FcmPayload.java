package damso.notification.adapter.out.fcm;

import java.util.LinkedHashMap;
import java.util.Map;

import damso.notification.domain.NotificationKind;
import damso.notification.domain.PushMessage;

/**
 * FCM으로 보낼 내용: 고정 제목·본문과 데이터(종류, 문의 UUID 또는 묶음 건수)뿐이다.
 * Firebase SDK의 Message와 분리해 두어 무엇이 나가는지 SDK 없이 테스트로 확인할 수 있다.
 * toString은 종류만 보여 주어 문구·UUID가 로그에 섞이지 않는다.
 */
record FcmPayload(String title, String body, Map<String, String> data, boolean highPriority) {

	static FcmPayload of(PushMessage message) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("kind", message.kind().name());
		if (message.inquiryId() != null) {
			data.put("inquiryId", message.inquiryId().toString());
		}
		if (message.digestCount() != null) {
			data.put("count", String.valueOf(message.digestCount()));
		}
		boolean urgent = message.kind() == NotificationKind.URGENT
				|| message.kind() == NotificationKind.URGENT_REMINDER;
		return new FcmPayload(message.title(), message.body(), Map.copyOf(data), urgent);
	}

	@Override
	public String toString() {
		return "FcmPayload[kind=" + data.get("kind") + "]";
	}

}
