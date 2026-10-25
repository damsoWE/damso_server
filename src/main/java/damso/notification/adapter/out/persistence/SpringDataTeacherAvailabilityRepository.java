package damso.notification.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.notification.domain.TeacherAvailability;

interface SpringDataTeacherAvailabilityRepository extends JpaRepository<TeacherAvailability, UUID> {

	List<TeacherAvailability> findByTeacherId(UUID teacherId);

	/** 즉시 실행되는 벌크 삭제. 같은 트랜잭션에서 곧바로 다시 넣을 때 유니크 충돌이 나지 않게 한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TeacherAvailability a where a.teacherId = :teacherId")
	void deleteByTeacherId(@Param("teacherId") UUID teacherId);

}
