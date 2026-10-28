package damso.inquiry.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import damso.inquiry.domain.Answer;

/**
 * 답변 저장소. CrudRepository가 아니라 Repository를 상속해 save·조회만 선언한다 — delete·update 메서드가 없다
 * (원칙 III, FR-003). 메서드를 추가할 때도 수정·삭제는 넣지 않는다(AnswerAppendOnlyTest가 검사한다).
 */
public interface SpringDataAnswerRepository extends Repository<Answer, UUID> {

	/** 스레드 루트별 마지막 답변 전송 시각. 본문은 읽지 않는다. */
	interface LastSentAt {

		UUID getThreadRootId();

		Instant getLastSentAt();

	}

	Answer save(Answer answer);

	List<Answer> findByThreadRootIdOrderBySentAt(UUID threadRootId);

	/** 목록의 답변 여부(F4 US6, research R10) — 답변이 없는 루트는 결과에 없다. */
	@Query("select a.threadRootId as threadRootId, max(a.sentAt) as lastSentAt from Answer a "
			+ "where a.threadRootId in :rootIds group by a.threadRootId")
	List<LastSentAt> findLastSentAtByThreadRootIds(@Param("rootIds") Collection<UUID> rootIds);

}
