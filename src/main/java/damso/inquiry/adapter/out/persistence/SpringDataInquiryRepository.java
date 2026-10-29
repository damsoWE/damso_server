package damso.inquiry.adapter.out.persistence;

import java.time.Instant;
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

	/** 스레드 루트별 마지막 재문의 접수 시각. */
	interface LastFollowUpAt {

		UUID getThreadRootId();

		Instant getLastFollowUpAt();

	}

	/**
	 * 답변 완료 스레드 조건(별칭 i는 스레드 루트): 답변이 1건 이상 있고, 마지막 답변 뒤에 재문의가 없다.
	 * 서비스의 InquirySummary.needsAnswer()와 같은 기준(재문의 접수 시각이 마지막 답변 시각보다 엄격히 늦으면 대기)이다.
	 */
	String ANSWERED_THREAD = "exists (select 1 from Answer a where a.threadRootId = i.id) "
			+ "and not exists (select 1 from Inquiry f where f.threadRootId = i.id "
			+ "and f.submittedAt > (select max(a2.sentAt) from Answer a2 where a2.threadRootId = i.id))";

	String CLASSROOM_ROOTS = "from Inquiry i where i.classroomId = :classroomId and i.threadRootId is null and ";

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

	/** 스레드 루트만, 답변 완료 스레드. */
	@Query("select i " + CLASSROOM_ROOTS + "(" + ANSWERED_THREAD + ")")
	List<Inquiry> findAnsweredByClassroom(@Param("classroomId") UUID classroomId, Pageable pageable);

	@Query("select count(i) " + CLASSROOM_ROOTS + "(" + ANSWERED_THREAD + ")")
	long countAnsweredByClassroom(@Param("classroomId") UUID classroomId);

	/** 스레드 루트만, 답변 대기 스레드(답변이 없거나 마지막 답변 뒤에 재문의가 있다). */
	@Query("select i " + CLASSROOM_ROOTS + "not (" + ANSWERED_THREAD + ")")
	List<Inquiry> findWaitingByClassroom(@Param("classroomId") UUID classroomId, Pageable pageable);

	@Query("select count(i) " + CLASSROOM_ROOTS + "not (" + ANSWERED_THREAD + ")")
	long countWaitingByClassroom(@Param("classroomId") UUID classroomId);

	List<Inquiry> findByTeacherIdAndNotificationDeferredTrueAndDeferredNotifiedAtIsNull(UUID teacherId);

	@Query("select i from Inquiry i where i.id = :rootId or i.threadRootId = :rootId order by i.submittedAt asc")
	List<Inquiry> findThreadMessages(@Param("rootId") UUID rootId);

	@Query("select distinct i.threadRootId from Inquiry i where i.threadRootId in :rootIds and i.threatFlagged = true")
	List<UUID> findThreadRootIdsWithThreatFlaggedFollowUp(@Param("rootIds") Collection<UUID> rootIds);

	/** 본문을 복호화하지 않도록 id만 읽는다. 첫 행이 최신 재문의다. */
	@Query("select i.id from Inquiry i where i.threadRootId = :rootId order by i.submittedAt desc, i.id desc")
	List<UUID> findFollowUpIdsLatestFirst(@Param("rootId") UUID rootId, Pageable pageable);

	@Query("select i.threadRootId as threadRootId, max(i.submittedAt) as lastFollowUpAt from Inquiry i "
			+ "where i.threadRootId in :rootIds group by i.threadRootId")
	List<LastFollowUpAt> findLastFollowUpAtByThreadRootIds(@Param("rootIds") Collection<UUID> rootIds);

	/**
	 * 스레드 루트 수와 그중 답변을 1건 이상 보낸 수, (학급, 단계)별. 행: classroomId, stage, 전체 수, 답변 보낸 수.
	 * 한 쿼리로 세므로 두 수가 같은 시점 기준이다 — 따로 세면 사이에 온 답변 때문에 한 스레드가 겹치거나 빠진다.
	 */
	@Query("select i.classroomId, i.stage, count(i), "
			+ "sum(case when exists (select 1 from Answer a where a.threadRootId = i.id) then 1 else 0 end) "
			+ "from Inquiry i where i.threadRootId is null group by i.classroomId, i.stage")
	List<Object[]> countThreadsByClassroomAndStage();

}
