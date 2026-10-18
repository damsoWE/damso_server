package damso.school.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.school.domain.TeacherProfile;

interface SpringDataTeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {

	Optional<TeacherProfile> findByTeacherId(UUID teacherId);

	List<TeacherProfile> findByTeacherIdIn(Collection<UUID> teacherIds);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from TeacherProfile p where p.teacherId = :teacherId")
	int deleteByTeacher(@Param("teacherId") UUID teacherId);

}
