package damso.notification.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.TeacherNotificationSetting;

public interface NotificationSettingPort {

	Optional<TeacherNotificationSetting> findSetting(UUID teacherId);

	TeacherNotificationSetting saveSetting(TeacherNotificationSetting setting);

	/** 교사의 응답 가능 구간. 없으면 빈 목록(항상 응답 가능). */
	List<AvailabilityWindow> findWindows(UUID teacherId);

	/** 구간을 통째로 바꾼다. 호출한 트랜잭션 안에서 지우고 다시 넣는다. */
	void replaceWindows(UUID teacherId, List<AvailabilityWindow> windows);

}
