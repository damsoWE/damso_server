package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import damso.inquiry.adapter.out.persistence.InquiryStageHistoryRepository;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStageHistory;

/** 문의 원문과 단계 이력에는 수정·삭제 경로가 없다 — 구조로 검사한다. */
class InquiryAppendOnlyTest {

	private static List<String> methodNames(Class<?> type) {
		return Arrays.stream(type.getMethods()).map(Method::getName).toList();
	}

	@Test
	void stageHistoryRepositoryOffersOnlySaveAndReadMethods() {
		assertThat(Repository.class).isAssignableFrom(InquiryStageHistoryRepository.class);
		assertThat(CrudRepository.class.isAssignableFrom(InquiryStageHistoryRepository.class)).isFalse();
		for (Method method : InquiryStageHistoryRepository.class.getDeclaredMethods()) {
			String name = method.getName().toLowerCase();
			assertThat(name).doesNotStartWith("delete").doesNotStartWith("update").doesNotStartWith("remove")
					.doesNotContain("deleteall");
			assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
		}
		assertThat(methodNames(InquiryStageHistoryRepository.class)).contains("save",
				"findByInquiryIdOrderByOccurredAt");
	}

	@Test
	void stageHistoryPortOffersOnlyAppendAndRead() {
		assertThat(Arrays.stream(InquiryStageHistoryPort.class.getDeclaredMethods()).map(Method::getName))
				.containsExactlyInAnyOrder("append", "findByInquiry");
	}

	@Test
	void stageHistoryEntityIsImmutableAndHasNoSettersOrPersonalFields() {
		assertThat(InquiryStageHistory.class.isAnnotationPresent(Immutable.class)).isTrue();
		for (Method method : InquiryStageHistory.class.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers())) {
				assertThat(method.getName()).doesNotStartWith("set");
			}
		}
		assertThat(Arrays.stream(InquiryStageHistory.class.getDeclaredFields()).filter(field -> !field.isSynthetic())
				.filter(field -> !field.getName().equals("isNew"))
				.filter(field -> !Modifier.isStatic(field.getModifiers())))
						.as("개인정보·본문 필드 없음")
						.extracting(Field::getName)
						.doesNotContain("email", "name", "phone", "content", "title");
	}

	@Test
	void inquiryHasNoPublicSettersForTheFrozenFields() {
		List<String> publicMethods = Arrays.stream(Inquiry.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.map(Method::getName)
				.toList();
		assertThat(publicMethods).noneMatch(name -> name.startsWith("set"));
		assertThat(publicMethods).doesNotContain("setContent", "setUrgent", "setStudentId", "setTeacherId",
				"setClassroomId");
	}

	@Test
	void inquiryFrozenColumnsAreNotUpdatable() throws Exception {
		for (String name : List.of("content", "urgent", "studentId", "teacherId", "classroomId", "parentId",
				"idempotencyKey", "requestHash", "submittedAt")) {
			Column column = Inquiry.class.getDeclaredField(name).getAnnotation(Column.class);
			assertThat(column).as(name).isNotNull();
			assertThat(column.updatable()).as(name + " updatable").isFalse();
		}
	}

}
