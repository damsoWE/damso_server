package damso.school.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import damso.school.domain.LinkHistory;

/**
 * 연결 이력 저장소. CrudRepository가 아니라 Repository를 상속해 save·조회만 선언한다 — delete·update 메서드가 없다
 * (원칙 III, research R10). 메서드를 추가할 때도 수정·삭제는 넣지 않는다(LinkHistoryAppendOnlyTest가 검사한다).
 */
public interface LinkHistoryRepository extends Repository<LinkHistory, UUID> {

	LinkHistory save(LinkHistory history);

	List<LinkHistory> findByAccountIdOrderByOccurredAt(UUID accountId);

}
