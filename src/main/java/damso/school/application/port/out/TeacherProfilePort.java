package damso.school.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.school.domain.TeacherProfile;

public interface TeacherProfilePort {

	TeacherProfile saveProfile(TeacherProfile profile);

	Optional<TeacherProfile> findProfileByTeacher(UUID teacherId);

	List<TeacherProfile> findProfilesByTeachers(Collection<UUID> teacherIds);

	void deleteProfileByTeacher(UUID teacherId);

}
