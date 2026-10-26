package damso.inquiry.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.inquiry.application.port.out.AnswerPort;
import damso.inquiry.domain.Answer;

@Component
class AnswerPersistenceAdapter implements AnswerPort {

	private final SpringDataAnswerRepository answers;

	AnswerPersistenceAdapter(SpringDataAnswerRepository answers) {
		this.answers = answers;
	}

	@Override
	public Answer save(Answer answer) {
		return answers.save(answer);
	}

	@Override
	public List<Answer> findByThreadRootId(UUID rootId) {
		return answers.findByThreadRootIdOrderBySentAt(rootId);
	}

}
