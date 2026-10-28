package damso.notification.application.port.in;

import java.util.UUID;

public interface ParentNotificationSettingsUseCase {

	/** 설정 행이 없으면 기본값 켜짐(true)으로 본다. */
	boolean get(UUID parentId);

	boolean save(UUID parentId, boolean answerNotifyEnabled);

}
