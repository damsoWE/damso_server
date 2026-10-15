package damso.common.entity;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.common.crypto.SensitiveText;
import damso.support.IntegrationTestSupport;
import damso.support.SecretNote;
import damso.support.SecretNoteRepository;
import damso.support.TestJwtIssuer;

/** US6 / SC-006: UUID v4 식별자, 감사 시각·버전 자동, 동시 수정 충돌 → 409. */
class BaseEntityTest extends IntegrationTestSupport {

	@Autowired
	private SecretNoteRepository repository;

	@Test
	void idsAreRandomUuidV4WithoutDuplicates() {
		List<SecretNote> notes = IntStream.range(0, 1000)
				.mapToObj(i -> new SecretNote(SensitiveText.of("n" + i)))
				.toList();
		Set<UUID> ids = new HashSet<>();
		for (SecretNote note : notes) {
			assertThat(note.getId().version()).isEqualTo(4);
			ids.add(note.getId());
		}
		assertThat(ids).hasSize(1000);
	}

	@Test
	void auditFieldsAndVersionAreSetOnSave() {
		SecretNote saved = repository.saveAndFlush(new SecretNote(SensitiveText.of("audit")));
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getVersion()).isZero();
	}

	@Test
	void secondUpdateFromSameVersionIsRejected() {
		UUID id = repository.saveAndFlush(new SecretNote(SensitiveText.of("v0"))).getId();
		SecretNote first = repository.findById(id).orElseThrow();
		SecretNote second = repository.findById(id).orElseThrow();

		first.changeContent(SensitiveText.of("by first"));
		repository.saveAndFlush(first);

		second.changeContent(SensitiveText.of("by second"));
		assertThatThrownBy(() -> repository.saveAndFlush(second))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(repository.findById(id).orElseThrow().getContent().reveal()).isEqualTo("by first");
	}

	@Test
	void conflictIsReportedAs409() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/test/errors/conflict")
				.header(HttpHeaders.AUTHORIZATION, bearer(TestJwtIssuer.valid("TEACHER"))))
				.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "CONFLICT");
	}

}
