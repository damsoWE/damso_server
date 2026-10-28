package damso.inquiry.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import damso.inquiry.domain.Answer;

public interface AnswerPort {

	Answer save(Answer answer);

	/** 스레드(루트 inquiry.id)의 모든 답변, 전송 시각 오름차순. */
	List<Answer> findByThreadRootId(UUID rootId);

	/** 루트 id별 마지막 답변 전송 시각. 답변이 없는 루트는 Map에 없다(F4 US6). */
	Map<UUID, Instant> findLastAnsweredAt(Collection<UUID> rootIds);

	/** 학부모 본인 스레드의 루트 id별 마지막 답변 전송 시각. 답변이 없는 루트는 Map에 없다(F4 US6). */
	Map<UUID, Instant> findLastAnsweredAtByParent(UUID parentId);

}
