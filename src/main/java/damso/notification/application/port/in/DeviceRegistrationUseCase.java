package damso.notification.application.port.in;

import java.util.UUID;

import damso.notification.domain.DeviceRegistration.Platform;

public interface DeviceRegistrationUseCase {

	/**
	 * 이 기기를 알림 수신 기기로 등록한다. 등록은 접근 토큰의 세션에 묶인다.
	 * 같은 토큰이 이미 있으면(갱신하거나 다른 사용자가 기기를 넘겨받은 경우) 그 행을 이 사용자·세션으로 옮기고 다시 활성으로 둔다.
	 * 로그아웃 때 별도 정리는 없다 — 발송기가 세션이 살아 있는지(SessionStatusChecker)로 거른다.
	 */
	void register(UUID userId, UUID sessionId, String token, Platform platform);

	/** 이 세션에 등록된 기기를 비활성화한다. 등록이 없어도 오류가 아니다. */
	void unregisterCurrent(UUID userId, UUID sessionId);

}
