package damso.school.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import damso.school.domain.Student;

interface SpringDataStudentRepository extends JpaRepository<Student, UUID> {

	List<Student> findByClassroomId(UUID classroomId);

	List<Student> findByGuardianId(UUID guardianId);

	@Query("select s.classroomId, count(s) from Student s group by s.classroomId")
	List<Object[]> countGroupedByClassroom();

}
