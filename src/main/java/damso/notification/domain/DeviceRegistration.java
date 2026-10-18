package damso.notification.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 푸시를 받을 기기. 등록한 접근 토큰의 세션(sid)에 묶여, 그 세션이 살아 있을 때만 발송 대상이 된다 (research R9).
 * 로그아웃·탈퇴로 세션이 끝나면 별도 정리 없이 알림이 끊긴다.
 * FCM 토큰은 유니크하다. toString은 BaseEntity가 클래스명과 id만 출력하므로 토큰이 로그에 나오지 않는다.
 */
@Entity
@Table(name = "device_registration",
		uniqueConstraints = @UniqueConstraint(name = "uk_device_token", columnNames = "token"),
		indexes = { @Index(name = "idx_device_user", columnList = "user_id"),
				@Index(name = "idx_device_session", columnList = "session_id") })
public class DeviceRegistration extends BaseEntity {

	public static final int MAX_TOKEN_LENGTH = 512;

	public enum Platform {
		ANDROID,
		IOS
	}

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	@Column(nullable = false, length = MAX_TOKEN_LENGTH)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Platform platform;

	@Column(nullable = false)
	private boolean active = true;

	protected DeviceRegistration() {
	}

	public DeviceRegistration(UUID userId, UUID sessionId, String token, Platform platform) {
		this.userId = userId;
		this.sessionId = sessionId;
		this.token = token;
		this.platform = platform;
	}

	/** 같은 토큰이 다시 등록됐다(갱신하거나 기기를 넘겨받은 경우). 사용자·세션을 옮기고 다시 활성으로 둔다. */
	public void reassign(UUID userId, UUID sessionId, Platform platform) {
		this.userId = userId;
		this.sessionId = sessionId;
		this.platform = platform;
		this.active = true;
	}

	/** FCM이 무효 토큰이라 응답했거나 사용자가 해제했다. */
	public void deactivate() {
		this.active = false;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public String getToken() {
		return token;
	}

	public Platform getPlatform() {
		return platform;
	}

	public boolean isActive() {
		return active;
	}

}
