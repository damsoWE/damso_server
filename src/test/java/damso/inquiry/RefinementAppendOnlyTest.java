package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import damso.gateway.application.port.in.RefineInquiryGateway.RefineOutcome;
import damso.gateway.application.port.in.RefineInquiryGateway.Refined;
import damso.inquiry.adapter.out.persistence.InquiryRefinementRepository;
import damso.inquiry.adapter.out.persistence.RefinementAttemptRepository;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryRefinementPort;
import damso.inquiry.application.port.out.RefinementAttemptPort;
import damso.inquiry.application.port.out.RefinementJobPort;
import damso.inquiry.domain.InquiryRefinement;
import damso.inquiry.domain.RefinementAttempt;
import damso.support.IntegrationTestSupport;

/**
 * F3 T030 / 원칙 III, FR-010·029: 정리 결과와 시도 기록은 append-only이고 문의당 결과는 하나다.
 * 정리 결과는 저장·표시용일 뿐 조회 조건 같은 실행 경로에 쓰이지 않는다(구조로 검사한다).
 */
class RefinementAppendOnlyTest extends IntegrationTestSupport {

	private static void assertSaveAndReadOnly(Class<?> repository) {
		assertThat(Repository.class).isAssignableFrom(repository);
		assertThat(CrudRepository.class.isAssignableFrom(repository)).isFalse();
		for (Method method : repository.getDeclaredMethods()) {
			String name = method.getName().toLowerCase();
			assertThat(name).doesNotStartWith("delete").doesNotStartWith("update").doesNotStartWith("remove");
			assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
		}
	}

	private static void assertImmutableWithoutSetters(Class<?> entity) {
		assertThat(entity.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(Arrays.stream(entity.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
				.map(Method::getName)).noneMatch(name -> name.startsWith("set"));
	}

	@Test
	void refinementAndAttemptRepositoriesOfferOnlySaveAndRead() {
		assertSaveAndReadOnly(InquiryRefinementRepository.class);
		assertSaveAndReadOnly(RefinementAttemptRepository.class);
		assertThat(Arrays.stream(InquiryRefinementPort.class.getDeclaredMethods()).map(Method::getName))
				.containsExactlyInAnyOrder("save", "findByInquiry");
		assertThat(Arrays.stream(RefinementAttemptPort.class.getDeclaredMethods()).map(Method::getName))
				.containsExactlyInAnyOrder("append", "findByJob");
	}

	@Test
	void refinementAndAttemptEntitiesAreImmutableAndAttemptsHoldNoContent() {
		assertImmutableWithoutSetters(InquiryRefinement.class);
		assertImmutableWithoutSetters(RefinementAttempt.class);
		assertThat(Arrays.stream(RefinementAttempt.class.getDeclaredFields()).map(f -> f.getName()))
				.doesNotContain("content", "facts", "requests", "title", "prompt", "response", "json");
	}

	@Test
	void onlyOneRefinementPerInquiry(@org.springframework.beans.factory.annotation.Autowired
	InquiryRefinementPort refinements) throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE),
				"$.inquiryId"));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> refinements.save(
				InquiryRefinement.of(id, "다른 사실", "", "LOW", List.of(), clock.instant())))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("select count(*) from inquiry_refinement where inquiry_id = ?", Integer.class,
				id)).isEqualTo(1);
	}

	@Test
	void refinementValuesAreNeverQueryArguments() {
		// 저장소 포트의 조회 메서드는 정리 결과 값(문자열·결과 객체)을 조건으로 받지 않는다 — UUID·시각·종류로만 찾는다 (FR-010)
		for (Class<?> port : List.of(InquiryRefinementPort.class, RefinementJobPort.class, RefinementAttemptPort.class,
				InquiryPort.class)) {
			for (Method method : port.getDeclaredMethods()) {
				assertThat(method.getParameterTypes()).as(port.getSimpleName() + "." + method.getName())
						.doesNotContain(RefineOutcome.class, Refined.class);
				if (method.getName().startsWith("find")) {
					assertThat(method.getParameterTypes()).as(port.getSimpleName() + "." + method.getName())
							.doesNotContain(String.class);
				}
			}
		}
		// 게이트웨이 결과(RefineOutcome)를 받는 곳은 정리 실행기 안의 결과 반영뿐이다
		List<String> takers = Arrays.stream(
				damso.inquiry.application.service.RefinementRunner.class.getDeclaredMethods())
				.filter(m -> !m.isSynthetic())
				.filter(m -> Arrays.asList(m.getParameterTypes()).contains(RefineOutcome.class))
				.map(Method::getName)
				.toList();
		assertThat(takers).allMatch(name -> name.equals("confirm") || name.equals("codeOf"));
	}

}
