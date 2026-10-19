package damso.notification.adapter.out.fcm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import damso.notification.application.port.out.PushSenderPort;
import damso.notification.domain.PushMessage;

/**
 * Firebase Admin SDK로 푸시를 보낸다 (research R8). 자격 증명(DAMSO_FCM_CREDENTIALS)은 기동 시 형식만 검증하고,
 * FirebaseApp은 처음 보낼 때 만든다 — 기동이 외부(Google) 연결에 묶이지 않게 한다.
 * 오류는 종류만 WARN으로 남기고 토큰·문구·문의 UUID는 남기지 않는다. 장애는 예외가 아니라 결과로 알린다.
 */
@Component
class FcmPushSender implements PushSenderPort {

	private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

	private static final String APP_NAME = "damso";

	private final String credentialsBase64;

	private volatile FirebaseMessaging messaging;

	FcmPushSender(@Value("${damso.fcm.credentials}") String credentialsBase64) {
		this.credentialsBase64 = credentialsBase64;
	}

	@Override
	public SendResult send(String deviceToken, PushMessage message) {
		try {
			messaging().send(toMessage(deviceToken, FcmPayload.of(message)));
			return SendResult.SENT;
		}
		catch (FirebaseMessagingException ex) {
			MessagingErrorCode code = ex.getMessagingErrorCode();
			// 등록되지 않았거나 형식이 잘못된 토큰이다. 문구·데이터는 우리가 만든 고정값이라 토큰 문제로 본다.
			if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
				return SendResult.INVALID_TOKEN;
			}
			log.warn("FCM 발송 실패 (code={})", code);
			return SendResult.FAILED;
		}
		catch (IOException | RuntimeException ex) {
			log.warn("FCM 발송 오류 (type={})", ex.getClass().getSimpleName());
			return SendResult.FAILED;
		}
	}

	static Message toMessage(String deviceToken, FcmPayload payload) {
		Message.Builder builder = Message.builder()
				.setToken(deviceToken)
				.setNotification(Notification.builder().setTitle(payload.title()).setBody(payload.body()).build())
				.putAllData(payload.data());
		if (payload.highPriority()) {
			builder.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
		}
		return builder.build();
	}

	private FirebaseMessaging messaging() throws IOException {
		FirebaseMessaging current = messaging;
		if (current == null) {
			synchronized (this) {
				current = messaging;
				if (current == null) {
					current = FirebaseMessaging.getInstance(firebaseApp());
					messaging = current;
				}
			}
		}
		return current;
	}

	private FirebaseApp firebaseApp() throws IOException {
		for (FirebaseApp app : FirebaseApp.getApps()) {
			if (APP_NAME.equals(app.getName())) {
				return app;
			}
		}
		byte[] json = Base64.getDecoder().decode(credentialsBase64.trim());
		GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
		return FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build(), APP_NAME);
	}

	/** 자격 증명이 로그에 섞이지 않도록 아무것도 보이지 않는다. */
	@Override
	public String toString() {
		return "FcmPushSender[]";
	}

}
