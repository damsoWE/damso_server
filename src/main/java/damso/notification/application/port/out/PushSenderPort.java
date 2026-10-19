package damso.notification.application.port.out;

import damso.notification.domain.PushMessage;

/**
 * 기기 한 대에 푸시를 보낸다. 구현은 FCM(Firebase Admin SDK). 장애는 예외가 아니라 결과로 알린다.
 * 토큰과 메시지는 로그에 남기지 않는다.
 */
public interface PushSenderPort {

	enum SendResult {
		/** 전달 요청이 받아들여졌다. */
		SENT,
		/** 등록되지 않았거나 만료된 토큰. 그 기기를 비활성화한다. */
		INVALID_TOKEN,
		/** 일시 오류. 나중에 다시 시도한다. */
		FAILED
	}

	SendResult send(String deviceToken, PushMessage message);

}
