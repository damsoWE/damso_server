package damso.notification.application.service;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.InvalidInputException;
import damso.notification.application.port.in.NotificationSettingsUseCase;
import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.TeacherNotificationSetting;

@Service
@Transactional
public class NotificationSettingsService implements NotificationSettingsUseCase {

	private static final int MAX_WINDOWS = 7;

	private final NotificationSettingPort settings;

	public NotificationSettingsService(NotificationSettingPort settings) {
		this.settings = settings;
	}

	@Override
	@Transactional(readOnly = true)
	public Settings get(UUID teacherId) {
		boolean enabled = settings.findSetting(teacherId).map(TeacherNotificationSetting::isGeneralEnabled).orElse(true);
		return new Settings(enabled, sorted(settings.findWindows(teacherId)));
	}

	@Override
	public Settings save(UUID teacherId, boolean generalEnabled, List<WindowInput> availability) {
		List<AvailabilityWindow> windows = validate(availability);

		TeacherNotificationSetting setting = settings.findSetting(teacherId)
				.orElseGet(() -> new TeacherNotificationSetting(teacherId, generalEnabled));
		setting.changeGeneralEnabled(generalEnabled);
		settings.saveSetting(setting);
		// 구간만 바꾼다. 이미 잡힌 GENERAL_DIGEST의 scheduled_at은 건드리지 않는다(이후 문의부터 적용).
		settings.replaceWindows(teacherId, windows);
		return new Settings(generalEnabled, sorted(windows));
	}

	private static List<AvailabilityWindow> validate(List<WindowInput> availability) {
		if (availability.size() > MAX_WINDOWS) {
			throw new InvalidInputException("availability", "최대 " + MAX_WINDOWS + "개입니다");
		}
		Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
		for (WindowInput input : availability) {
			if (!input.start().isBefore(input.end())) {
				throw new InvalidInputException("availability", "시작은 종료보다 빨라야 합니다");
			}
			if (!seen.add(input.day())) {
				throw new InvalidInputException("availability", "요일마다 한 구간만 둘 수 있습니다");
			}
		}
		return availability.stream().map(input -> new AvailabilityWindow(input.day(), input.start(), input.end()))
				.toList();
	}

	private static List<AvailabilityWindow> sorted(List<AvailabilityWindow> windows) {
		return windows.stream().sorted(Comparator.comparing(AvailabilityWindow::day)).toList();
	}

}
