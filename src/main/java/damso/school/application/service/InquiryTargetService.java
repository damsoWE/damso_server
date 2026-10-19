package damso.school.application.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.InquiryTargetQuery;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.domain.Classroom;
import damso.school.domain.Student;

@Service
@Transactional(readOnly = true)
public class InquiryTargetService implements InquiryTargetQuery {

	private final StudentPort students;

	private final ClassroomPort classrooms;

	public InquiryTargetService(StudentPort students, ClassroomPort classrooms) {
		this.students = students;
		this.classrooms = classrooms;
	}

	@Override
	public ChildTarget resolveChild(UUID parentId, UUID studentId) {
		Student student = students.findStudentById(studentId)
				.filter(found -> parentId.equals(found.getGuardianId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		UUID teacherId = classrooms.findClassroomById(student.getClassroomId())
				.map(Classroom::getHomeroomTeacherId)
				.orElse(null);
		return new ChildTarget(student.getId(), student.getName().reveal(), student.getClassroomId(), teacherId);
	}

	@Override
	public boolean isCurrentHomeroomTeacher(UUID teacherId, UUID classroomId) {
		return currentHomeroomTeacher(classroomId).filter(teacherId::equals).isPresent();
	}

	@Override
	public Optional<UUID> currentHomeroomTeacher(UUID classroomId) {
		return classrooms.findClassroomById(classroomId).map(Classroom::getHomeroomTeacherId);
	}

	@Override
	public Map<UUID, String> studentNames(Collection<UUID> studentIds) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		return students.findStudentsByIds(studentIds).stream()
				.collect(Collectors.toMap(Student::getId, student -> student.getName().reveal()));
	}

	@Override
	public Optional<UUID> homeroomClassroomOf(UUID teacherId) {
		return classrooms.findClassroomByHomeroomTeacher(teacherId).map(Classroom::getId);
	}

}
