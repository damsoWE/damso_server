package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import damso.inquiry.adapter.out.persistence.SpringDataAnswerRepository;
import damso.inquiry.domain.Answer;

/** 원칙 III·VI, FR-003·004, SC-004·009: 답변에는 수정·삭제 경로가 없고 본문은 로그에 섞이지 않는다. */
class AnswerAppendOnlyTest {

	@Test
	void answerRepositoryOffersOnlySaveAndReadMethods() {
		assertThat(Repository.class).isAssignableFrom(SpringDataAnswerRepository.class);
		assertThat(CrudRepository.class.isAssignableFrom(SpringDataAnswerRepository.class)).isFalse();
		for (Method method : SpringDataAnswerRepository.class.getDeclaredMethods()) {
			String name = method.getName().toLowerCase();
			assertThat(name).doesNotStartWith("delete").doesNotStartWith("update").doesNotStartWith("remove")
					.doesNotContain("deleteall");
			assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
		}
		assertThat(Arrays.stream(SpringDataAnswerRepository.class.getDeclaredMethods()).map(Method::getName))
				.contains("save", "findByThreadRootIdOrderBySentAt");
	}

	@Test
	void answerHasNoPublicSetters() {
		List<String> publicMethods = Arrays.stream(Answer.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.map(Method::getName)
				.toList();
		assertThat(publicMethods).noneMatch(name -> name.startsWith("set"));
	}

	@Test
	void answerFrozenColumnsAreNotUpdatable() throws Exception {
		for (String name : List.of("content", "teacherId", "threadRootId", "reviewed", "sentAt")) {
			Column column = Answer.class.getDeclaredField(name).getAnnotation(Column.class);
			assertThat(column).as(name).isNotNull();
			assertThat(column.updatable()).as(name + " updatable").isFalse();
		}
	}

	@Test
	void answerToStringDoesNotLeakContent() {
		Answer answer = Answer.send(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "민감한 본문 내용", false,
				java.time.Instant.now());
		assertThat(answer.toString()).doesNotContain("민감한 본문 내용");
	}

}
