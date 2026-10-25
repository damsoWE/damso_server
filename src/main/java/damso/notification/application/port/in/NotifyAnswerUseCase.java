package damso.notification.application.port.in;

import java.util.UUID;

/** 답변이 도착하면 학부모 앞으로 알림 요청을 남기는 통로(F4). 요청만 저장하며 발송은 발송기가 한다. */
public interface NotifyAnswerUseCase {

	/**
	 * 답변 알림을 설정해 둔 학부모에게만 요청을 만든다(끄져 있으면 요청을 만들지 않는다, FR-024).
	 * inquiryId는 답변이 속한 스레드(루트) id다 — 푸시의 data.inquiryId로 그대로 나가 학부모 앱이 그 문의 상세로
	 * 딥링크한다(FcmPayload.of, URGENT·GENERAL과 같은 규칙). 호출한 쪽의 트랜잭션에 참여한다.
	 */
	void requestAnswerNotification(UUID inquiryId, UUID parentId);

}
