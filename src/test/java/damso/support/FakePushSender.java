package damso.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import damso.notification.application.port.out.PushSenderPort;
import damso.notification.domain.PushMessage;

/** 테스트용 푸시 포트. 보낸 메시지·토큰을 기록하고, 실패·무효 토큰·예외를 켜고 끌 수 있다. 실제 FCM에 접속하지 않는다. */
public class FakePushSender implements PushSenderPort {

	public record Sent(String token, PushMessage message) {
	}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();

	private final Set<String> invalidTokens = ConcurrentHashMap.newKeySet();

	private volatile boolean failing;

	private volatile boolean throwing;

	@Override
	public SendResult send(String deviceToken, PushMessage message) {
		if (throwing) {
			throw new IllegalStateException("push sender is down");
		}
		if (failing) {
			return SendResult.FAILED;
		}
		if (invalidTokens.contains(deviceToken)) {
			return SendResult.INVALID_TOKEN;
		}
		sent.add(new Sent(deviceToken, message));
		return SendResult.SENT;
	}

	/** 성공으로 기록된 발송 전부(보낸 순서). */
	public List<Sent> sent() {
		return new ArrayList<>(sent);
	}

	public List<Sent> sentTo(String token) {
		return sent.stream().filter(entry -> entry.token().equals(token)).toList();
	}

	public List<Sent> sentOfKind(damso.notification.domain.NotificationKind kind) {
		return sent.stream().filter(entry -> entry.message().kind() == kind).toList();
	}

	/** true면 이후 모든 발송이 FAILED로 끝난다(성공 기록 없음). */
	public void failAll(boolean failing) {
		this.failing = failing;
	}

	/** true면 이후 모든 발송이 예외를 던진다. */
	public void throwAll(boolean throwing) {
		this.throwing = throwing;
	}

	public void markInvalid(String token) {
		invalidTokens.add(token);
	}

	public void clear() {
		sent.clear();
		invalidTokens.clear();
		failing = false;
		throwing = false;
	}

}
