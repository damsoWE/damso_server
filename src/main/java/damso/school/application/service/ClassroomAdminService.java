package damso.school.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.ClassroomAdminUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.domain.Classroom;
import damso.school.domain.Student;

@Service
@Transactional
public class ClassroomAdminService implements ClassroomAdminUseCase {

	private final ClassroomPort classrooms;

	private final StudentPort students;

	public ClassroomAdminService(ClassroomPort classrooms, StudentPort students) {
		this.classrooms = classrooms;
		this.students = students;
	}

	@Override
	public ClassroomView createClassroom(int grade, int classNumber) {
		if (classrooms.existsClassroom(grade, classNumber)) {
			throw new BusinessException(ErrorCode.CONFLICT);
		}
		Classroom saved = classrooms.saveClassroom(new Classroom(grade, classNumber));
		return toView(saved, 0);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ClassroomView> listClassrooms() {
		Map<UUID, Long> counts = students.countStudentsByClassroom();
		return classrooms.findAllClassrooms().stream()
				.map(classroom -> toView(classroom, counts.getOrDefault(classroom.getId(), 0L)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<StudentView> listStudents(UUID classroomId) {
		requireClassroom(classroomId);
		return students.findStudentsByClassroom(classroomId).stream().map(ClassroomAdminService::toView).toList();
	}

	@Override
	public List<StudentView> registerStudents(UUID classroomId, List<String> names) {
		requireClassroom(classroomId);
		List<Student> toSave = names.stream().map(name -> new Student(classroomId, name)).toList();
		return students.saveStudents(toSave).stream().map(ClassroomAdminService::toView).toList();
	}

	private Classroom requireClassroom(UUID classroomId) {
		return classrooms.findClassroomById(classroomId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private static ClassroomView toView(Classroom classroom, long studentCount) {
		return new ClassroomView(classroom.getId(), classroom.getGrade(), classroom.getClassNumber(),
				classroom.hasTeacher(), studentCount);
	}

	private static StudentView toView(Student student) {
		return new StudentView(student.getId(), student.getName().reveal(), student.hasGuardian());
	}

}
