package damso.inquiry.application.port.out;

import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.Answer;

public interface AnswerPort {

	Answer save(Answer answer);

	/** 스레드(루트 inquiry.id)의 모든 답변, 전송 시각 오름차순. */
	List<Answer> findByThreadRootId(UUID rootId);

}
