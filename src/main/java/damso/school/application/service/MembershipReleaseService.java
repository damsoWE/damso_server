package damso.school.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.school.application.port.in.MembershipReleaseUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.LinkHistoryPort;
import damso.school.application.port.out.StudentPort;
import damso.school.application.port.out.TeacherProfilePort;
import damso.school.domain.LinkHistory;

@Service
@Transactional
public class MembershipReleaseService implements MembershipReleaseUseCase {

	private final ClassroomPort classrooms;

	private final StudentPort students;

	private final TeacherProfilePort profiles;

	private final LinkHistoryPort histories;

	private final Clock clock;

	public MembershipReleaseService(ClassroomPort classrooms, StudentPort students, TeacherProfilePort profiles,
			LinkHistoryPort histories, Clock clock) {
		this.classrooms = classrooms;
		this.students = students;
		this.profiles = profiles;
		this.histories = histories;
		this.clock = clock;
	}

	@Override
	public void releaseAll(UUID accountId) {
		Instant now = clock.instant();

		classrooms.findClassroomByHomeroomTeacher(accountId).ifPresent(classroom -> {
			classroom.releaseTeacher();
			classrooms.saveClassroom(classroom);
			histories.append(LinkHistory.unlinkedFromClassroom(accountId, classroom.getId(), now));
		});
		profiles.deleteProfileByTeacher(accountId);

		students.findStudentsByGuardian(accountId).forEach(student -> {
			student.releaseGuardian();
			students.saveStudent(student);
			histories.append(LinkHistory.unlinkedFromStudent(accountId, student.getId(), now));
		});

		histories.append(LinkHistory.withdrawn(accountId, now));
	}

}
