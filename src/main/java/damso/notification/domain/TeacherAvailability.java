package damso.notification.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 교사의 요일별 응답 가능 구간 한 줄. 요일당 한 구간이고 start_time &lt; end_time, 한국 시간, 시작 포함·종료 미포함이다.
 */
@Entity
@Table(name = "teacher_availability",
		uniqueConstraints = @UniqueConstraint(name = "uk_teacher_availability_day",
				columnNames = { "teacher_id", "day_of_week" }))
public class TeacherAvailability extends BaseEntity {

	@Column(name = "teacher_id", nullable = false, updatable = false)
	private UUID teacherId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 10)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false, columnDefinition = "time")
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false, columnDefinition = "time")
	private LocalTime endTime;

	protected TeacherAvailability() {
	}

	public TeacherAvailability(UUID teacherId, AvailabilityWindow window) {
		this.teacherId = teacherId;
		this.dayOfWeek = window.day();
		this.startTime = window.start();
		this.endTime = window.end();
	}

	public AvailabilityWindow toWindow() {
		return new AvailabilityWindow(dayOfWeek, startTime, endTime);
	}

	public UUID getTeacherId() {
		return teacherId;
	}

	public DayOfWeek getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

}
