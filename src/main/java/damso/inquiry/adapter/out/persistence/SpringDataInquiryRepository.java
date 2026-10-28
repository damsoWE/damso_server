package damso.inquiry.adapter.out.persistence;

import java.util.Collection;
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

	@Query("select distinct i.threadRootId from Inquiry i where i.threadRootId in :rootIds and i.threatFlagged = true")
	List<UUID> findThreadRootIdsWithThreatFlaggedFollowUp(@Param("rootIds") Collection<UUID> rootIds);

	/**
	 * 스레드 루트 수와 그중 답변을 1건 이상 보낸 수, (학급, 단계)별. 행: classroomId, stage, 전체 수, 답변 보낸 수.
	 * 한 쿼리로 세므로 두 수가 같은 시점 기준이다 — 따로 세면 사이에 온 답변 때문에 한 스레드가 겹치거나 빠진다.
	 */
	@Query("select i.classroomId, i.stage, count(i), "
			+ "sum(case when exists (select 1 from Answer a where a.threadRootId = i.id) then 1 else 0 end) "
			+ "from Inquiry i where i.threadRootId is null group by i.classroomId, i.stage")
	List<Object[]> countThreadsByClassroomAndStage();

}
