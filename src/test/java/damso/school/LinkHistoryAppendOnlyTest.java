package damso.school;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import damso.school.adapter.out.persistence.LinkHistoryRepository;
import damso.school.application.port.out.LinkHistoryPort;
import damso.school.domain.LinkHistory;

/** 연결 이력에는 수정·삭제 경로가 없다 — 구조로 검사한다. */
class LinkHistoryAppendOnlyTest {

	private static List<String> methodNames(Class<?> type) {
		return Arrays.stream(type.getMethods()).map(Method::getName).toList();
	}

	@Test
	void repositoryOffersOnlySaveAndReadMethods() {
		assertThat(Repository.class).isAssignableFrom(LinkHistoryRepository.class);
		assertThat(CrudRepository.class.isAssignableFrom(LinkHistoryRepository.class)).isFalse();
		for (Method method : LinkHistoryRepository.class.getDeclaredMethods()) {
			String name = method.getName().toLowerCase();
			assertThat(name).doesNotStartWith("delete").doesNotStartWith("update").doesNotStartWith("remove")
					.doesNotContain("deleteall");
			assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
		}
		assertThat(methodNames(LinkHistoryRepository.class)).contains("save", "findByAccountIdOrderByOccurredAt");
	}

	@Test
	void portOffersOnlyAppendAndRead() {
		assertThat(Arrays.stream(LinkHistoryPort.class.getDeclaredMethods()).map(Method::getName))
				.containsExactlyInAnyOrder("append", "findByAccount");
	}

	@Test
	void entityIsImmutableAndHasNoSetters() {
		assertThat(LinkHistory.class.isAnnotationPresent(Immutable.class)).isTrue();
		for (Method method : LinkHistory.class.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers())) {
				assertThat(method.getName()).doesNotStartWith("set");
			}
		}
		assertThat(Arrays.stream(LinkHistory.class.getDeclaredFields()).filter(field -> !field.isSynthetic())
				.filter(field -> !field.getName().equals("isNew"))
				.filter(field -> !Modifier.isStatic(field.getModifiers())))
						.as("개인정보 필드 없음")
						.extracting(java.lang.reflect.Field::getName)
						.doesNotContain("email", "name", "phone");
	}

}
