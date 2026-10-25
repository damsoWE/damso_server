package damso.inquiry.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import damso.inquiry.domain.RefinementAttempt;

/**
 * 정리 시도 기록 저장소. Repository를 상속해 save·조회만 선언한다 — delete·update 메서드가 없다 (원칙 III).
 * 메서드를 추가할 때도 수정·삭제는 넣지 않는다(RefinementAppendOnlyTest가 검사한다).
 */
public interface RefinementAttemptRepository extends Repository<RefinementAttempt, UUID> {

	RefinementAttempt save(RefinementAttempt attempt);

	List<RefinementAttempt> findByJobIdOrderByOccurredAt(UUID jobId);

}
