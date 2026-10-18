package damso.school.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.school.domain.Classroom;

public interface ClassroomPort {

	/** 저장하고 바로 반영(flush)한다 — 담임 확정의 유니크 충돌을 호출한 곳에서 잡기 위해서다. */
	Classroom saveClassroom(Classroom classroom);

	Optional<Classroom> findClassroomById(UUID id);

	boolean existsClassroom(int grade, int classNumber);

	/** 학년·반 순서. */
	List<Classroom> findAllClassrooms();

	Optional<Classroom> findClassroomByHomeroomTeacher(UUID teacherId);

}
