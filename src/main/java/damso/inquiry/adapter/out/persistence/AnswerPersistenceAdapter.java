package damso.inquiry.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

	@Override
	public Map<UUID, Instant> findLastAnsweredAt(Collection<UUID> rootIds) {
		if (rootIds.isEmpty()) {
			return Map.of();
		}
		return toMap(answers.findLastSentAtByThreadRootIds(rootIds));
	}

	@Override
	public Map<UUID, Instant> findLastAnsweredAtByParent(UUID parentId) {
		return toMap(answers.findLastSentAtByParentId(parentId));
	}

	private static Map<UUID, Instant> toMap(List<SpringDataAnswerRepository.LastSentAt> rows) {
		return rows.stream()
				.collect(Collectors.toUnmodifiableMap(SpringDataAnswerRepository.LastSentAt::getThreadRootId,
						SpringDataAnswerRepository.LastSentAt::getLastSentAt));
	}

}
