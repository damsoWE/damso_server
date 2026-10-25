package damso.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import damso.notification.domain.TeacherNotificationSetting;

interface SpringDataTeacherNotificationSettingRepository extends JpaRepository<TeacherNotificationSetting, UUID> {

	Optional<TeacherNotificationSetting> findByTeacherId(UUID teacherId);

}
