package damso.school.application.port.out;

import java.util.List;
import java.util.UUID;

import damso.school.domain.LinkHistory;

/** 연결 이력은 덧붙이기만 한다 — 수정·삭제 메서드를 두지 않는다 (원칙 III). */
public interface LinkHistoryPort {

	void append(LinkHistory history);

	List<LinkHistory> findByAccount(UUID accountId);

}
