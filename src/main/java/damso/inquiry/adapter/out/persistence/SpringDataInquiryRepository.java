package damso.inquiry.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.inquiry.domain.Inquiry;

interface SpringDataInquiryRepository extends JpaRepository<Inquiry, UUID> {

	Optional<Inquiry> findByParentIdAndIdempotencyKey(UUID parentId, UUID idempotencyKey);

	/** SELECT ... FOR UPDATE. InquiryPort.findInquiryByIdForUpdate 전용 — 다른 곳에서 직접 쓰지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Inquiry i where i.id = :id")
	Optional<Inquiry> findByIdForUpdate(@Param("id") UUID id);

	/** 이 트랜잭션 안에서만 락 대기 시간을 제한한다(트랜잭션이 끝나면 원래 값으로 돌아간다). */
	@Query(value = "select set_config('lock_timeout', :millis, true)", nativeQuery = true)
	String limitLockWait(@Param("millis") String millis);

	/** 트랜잭션 종료 시 자동 해제되는 Postgres advisory lock. void 반환 함수라 text로 바꿔 받는다. */
	@Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:lockKey, 0)) as text)", nativeQuery = true)
	String lockSubmission(@Param("lockKey") String lockKey);

	/** 스레드 루트만(재문의는 thread_root_id가 있다). */
	List<Inquiry> findByParentIdAndThreadRootIdIsNullOrderBySubmittedAtDesc(UUID parentId);

	/** 스레드 루트만. */
	List<Inquiry> findByClassroomIdAndThreadRootIdIsNull(UUID classroomId, Pageable pageable);

	long countByClassroomIdAndThreadRootIdIsNull(UUID classroomId);

	List<Inquiry> findByTeacherIdAndNotificationDeferredTrueAndDeferredNotifiedAtIsNull(UUID teacherId);

	@Query("select i from Inquiry i where i.id = :rootId or i.threadRootId = :rootId order by i.submittedAt asc")
	List<Inquiry> findThreadMessages(@Param("rootId") UUID rootId);

}
