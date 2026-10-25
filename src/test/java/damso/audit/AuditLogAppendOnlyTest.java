package damso.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import damso.audit.adapter.out.persistence.AuditLogRepository;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;

/** 원칙 III·VI, research R11: 감사 기록에는 수정·삭제 경로가 없고 개인정보 컬럼이 없다 — 구조로 검사한다. */
class AuditLogAppendOnlyTest {

	private static List<String> methodNames(Class<?> type) {
		return Arrays.stream(type.getMethods()).map(Method::getName).toList();
	}

	@Test
	void repositoryOffersOnlySaveAndReadMethods() {
		assertThat(Repository.class).isAssignableFrom(AuditLogRepository.class);
		assertThat(CrudRepository.class.isAssignableFrom(AuditLogRepository.class)).isFalse();
		for (Method method : AuditLogRepository.class.getDeclaredMethods()) {
			String name = method.getName().toLowerCase();
			assertThat(name).doesNotStartWith("delete").doesNotStartWith("update").doesNotStartWith("remove")
					.doesNotContain("deleteall");
			assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
		}
		assertThat(methodNames(AuditLogRepository.class)).contains("save");
	}

	@Test
	void portOffersOnlyAppendAndRead() {
		assertThat(Arrays.stream(AuditLogPort.class.getDeclaredMethods()).map(Method::getName))
				.containsExactlyInAnyOrder("append", "findByActor", "findByTarget");
	}

	@Test
	void entityIsImmutableWithoutSettersOrPersonalFields() {
		assertThat(AuditLog.class.isAnnotationPresent(Immutable.class)).isTrue();
		for (Method method : AuditLog.class.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers())) {
				assertThat(method.getName()).doesNotStartWith("set");
			}
		}
		assertThat(Arrays.stream(AuditLog.class.getDeclaredFields()).filter(field -> !field.isSynthetic())
				.filter(field -> !field.getName().equals("isNew"))
				.filter(field -> !Modifier.isStatic(field.getModifiers())))
						.as("개인정보 필드 없음")
						.extracting(Field::getName)
						.doesNotContain("email", "name", "phone", "content", "title");
	}

}
