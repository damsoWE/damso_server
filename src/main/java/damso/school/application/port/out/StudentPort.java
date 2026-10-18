package damso.school.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import damso.school.domain.Student;

public interface StudentPort {

	Student saveStudent(Student student);

	List<Student> saveStudents(List<Student> students);

	Optional<Student> findStudentById(UUID id);

	List<Student> findStudentsByClassroom(UUID classroomId);

	/** 학급별 학생 수. 학생이 없는 학급은 키가 없다. */
	Map<UUID, Long> countStudentsByClassroom();

	List<Student> findStudentsByGuardian(UUID guardianId);

	List<Student> findStudentsByIds(Collection<UUID> ids);

}
