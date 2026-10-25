package damso.inquiry.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.inquiry.domain.Inquiry;

interface SpringDataInquiryRepository extends JpaRepository<Inquiry, UUID> {

	Optional<Inquiry> findByParentIdAndIdempotencyKey(UUID parentId, UUID idempotencyKey);

	/** 이 트랜잭션 안에서만 락 대기 시간을 제한한다(트랜잭션이 끝나면 원래 값으로 돌아간다). */
	@Query(value = "select set_config('lock_timeout', :millis, true)", nativeQuery = true)
	String limitLockWait(@Param("millis") String millis);

	/** 트랜잭션 종료 시 자동 해제되는 Postgres advisory lock. void 반환 함수라 text로 바꿔 받는다. */
	@Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:lockKey, 0)) as text)", nativeQuery = true)
	String lockSubmission(@Param("lockKey") String lockKey);

	List<Inquiry> findByParentIdOrderBySubmittedAtDesc(UUID parentId);

	List<Inquiry> findByClassroomId(UUID classroomId, Pageable pageable);

	long countByClassroomId(UUID classroomId);

	List<Inquiry> findByTeacherIdAndNotificationDeferredTrueAndDeferredNotifiedAtIsNull(UUID teacherId);

}
