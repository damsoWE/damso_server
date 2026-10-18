package damso.school.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import damso.school.domain.Classroom;

interface SpringDataClassroomRepository extends JpaRepository<Classroom, UUID> {

	boolean existsByGradeAndClassNumber(int grade, int classNumber);

	List<Classroom> findAllByOrderByGradeAscClassNumberAsc();

	Optional<Classroom> findByHomeroomTeacherId(UUID teacherId);

}
