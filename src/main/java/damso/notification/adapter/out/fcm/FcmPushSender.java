package damso.notification.adapter.out.fcm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.annotation.PreDestroy;

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

	private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

	private static final int READ_TIMEOUT_MILLIS = 10_000;

	private final String credentialsBase64;

	private volatile FirebaseMessaging messaging;

	/** 이 빈이 만든 FirebaseApp(만든 적 없으면 null). 종료 때 이것만 지운다. this로 동기화한다. */
	private FirebaseApp ownedApp;

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
		GoogleCredentials credentials;
		try (InputStream in = new ByteArrayInputStream(json)) {
			credentials = GoogleCredentials.fromStream(in);
		}
		// 기본값(SDK 내부값)에 기대지 않고 연결·읽기 대기 상한을 둔다 — 발송은 공용 실행기 스레드에서 돈다
		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(credentials)
				.setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
				.setReadTimeout(READ_TIMEOUT_MILLIS)
				.build();
		ownedApp = FirebaseApp.initializeApp(options, APP_NAME);
		return ownedApp;
	}

	/** 앱이 내려갈 때 이 빈이 만든 FirebaseApp을 지워 SDK의 스레드·연결을 놓는다. 다른 곳이 만든 앱은 건드리지 않는다. */
	@PreDestroy
	synchronized void close() {
		messaging = null;
		if (ownedApp != null) {
			ownedApp.delete();
			ownedApp = null;
		}
	}

	/** 자격 증명이 로그에 섞이지 않도록 아무것도 보이지 않는다. */
	@Override
	public String toString() {
		return "FcmPushSender[]";
	}

}
