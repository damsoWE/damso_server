package damso.inquiry.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;

public interface InquiryPort {

	/** 저장하고 바로 반영(flush)한다 — (parent_id, idempotency_key) 유니크 충돌을 호출한 곳에서 잡기 위해서다. */
	Inquiry saveInquiry(Inquiry inquiry);

	Optional<Inquiry> findInquiryById(UUID id);

	/**
	 * findInquiryById와 같지만 쓰기 잠금(SELECT ... FOR UPDATE)으로 조회한다. 이 트랜잭션이 끝날 때까지
	 * 그 행을 쓰려는 다른 트랜잭션(예: 종료)은 대기한다 — 확인(stage 검사)과 사용(저장) 사이의 TOCTOU를 막는다
	 *. 답변 전송·재문의 제출처럼 "루트가 종료되지 않았는지 확인한 뒤 그 확인이 유효한 동안
	 * 써야 하는" 경로에서만 쓴다 — 조회만 하는 경로(목록·상세)에는 쓰지 않는다.
	 */
	Optional<Inquiry> findInquiryByIdForUpdate(UUID id);

	/**
	 * 같은 (학부모, 멱등키) 접수를 현재 트랜잭션이 끝날 때까지 직렬화한다. 이미 다른 요청이 쥐고 있으면 그 요청이
	 * 끝나기를 기다리되, 오래 걸리면 {@link org.springframework.dao.PessimisticLockingFailureException}을 던진다.
	 * 반드시 트랜잭션 안에서 호출해야 한다.
	 */
	void lockSubmission(UUID parentId, UUID idempotencyKey);

	Optional<Inquiry> findByParentAndKey(UUID parentId, UUID idempotencyKey);

	/** 학부모가 작성한 문의(스레드 루트만, 재문의는 각 스레드 상세에서 보인다), 최신순. */
	List<Inquiry> findByParent(UUID parentId);

	/** 학급의 문의(스레드 루트만) 한 쪽. 긴급이 먼저, 그다음 최신순. page는 0부터. */
	List<Inquiry> findByClassroom(UUID classroomId, int page, int size);

	long countByClassroom(UUID classroomId);

	/** 그 교사 앞으로 알림이 미뤄졌고 아직 묶음 알림에 포함되지 않은 문의. */
	List<Inquiry> findDeferredNotNotified(UUID teacherId);

	/**
	 * 스레드(루트 또는 재문의) 전체 메시지: id = rootId 또는 thread_root_id = rootId, 제출 시각 오름차순
	 *.
	 */
	List<Inquiry> findThreadMessages(UUID rootId);

	/** 주어진 스레드 루트 가운데 위협·폭언 표시가 붙은 재문의가 있는 루트의 id(교사 목록의 스레드 단위 표시). */
	Set<UUID> findThreadsWithThreatFlaggedFollowUp(Collection<UUID> rootIds);

	/**
	 * 스레드 루트 수를 (학급, 루트 단계, 답변을 보냈는지)별로 센다(F5 슈퍼 관리자 현황). 본문·제목은 읽지 않는다.
	 * 문의가 없는 조합은 결과에 없다.
	 */
	List<ThreadStageCount> countThreadsByClassroomAndStage();

	record ThreadStageCount(UUID classroomId, InquiryStage stage, boolean answerSent, long count) {
	}

}
