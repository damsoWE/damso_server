package damso.school.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.school.application.port.in.RosterQuery;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.application.port.out.TeacherProfilePort;
import damso.school.domain.Classroom;
import damso.school.domain.TeacherProfile;

@Service
@Transactional(readOnly = true)
public class RosterQueryService implements RosterQuery {

	private final ClassroomPort classrooms;

	private final StudentPort students;

	private final TeacherProfilePort profiles;

	public RosterQueryService(ClassroomPort classrooms, StudentPort students, TeacherProfilePort profiles) {
		this.classrooms = classrooms;
		this.students = students;
		this.profiles = profiles;
	}

	@Override
	public ClassRoster rosterOf(UUID classroomId) {
		Optional<Classroom> classroom = classrooms.findClassroomById(classroomId);
		if (classroom.isEmpty()) {
			return ClassRoster.empty();
		}
		UUID teacherId = classroom.get().getHomeroomTeacherId();
		String displayName = teacherId == null ? null
				: profiles.findProfileByTeacher(teacherId).map(TeacherProfile::getDisplayName).orElse(null);
		return new ClassRoster(students.findStudentsByClassroom(classroomId).stream()
				.map(student -> new RosterStudent(student.getId(), student.getName().reveal(), student.getGuardianId()))
				.toList(), classroom.get().getGrade(), classroom.get().getClassNumber(), teacherId, displayName);
	}

}
