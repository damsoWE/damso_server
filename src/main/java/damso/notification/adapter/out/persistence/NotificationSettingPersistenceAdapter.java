package damso.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.TeacherAvailability;
import damso.notification.domain.TeacherNotificationSetting;

@Component
class NotificationSettingPersistenceAdapter implements NotificationSettingPort {

	private final SpringDataTeacherNotificationSettingRepository settings;

	private final SpringDataTeacherAvailabilityRepository availabilities;

	NotificationSettingPersistenceAdapter(SpringDataTeacherNotificationSettingRepository settings,
			SpringDataTeacherAvailabilityRepository availabilities) {
		this.settings = settings;
		this.availabilities = availabilities;
	}

	@Override
	public Optional<TeacherNotificationSetting> findSetting(UUID teacherId) {
		return settings.findByTeacherId(teacherId);
	}

	@Override
	public TeacherNotificationSetting saveSetting(TeacherNotificationSetting setting) {
		return settings.saveAndFlush(setting);
	}

	@Override
	public List<AvailabilityWindow> findWindows(UUID teacherId) {
		return availabilities.findByTeacherId(teacherId).stream().map(TeacherAvailability::toWindow).toList();
	}

	@Override
	@Transactional
	public void replaceWindows(UUID teacherId, List<AvailabilityWindow> windows) {
		availabilities.deleteByTeacherId(teacherId);
		availabilities.saveAll(windows.stream().map(window -> new TeacherAvailability(teacherId, window)).toList());
		availabilities.flush();
	}

}
