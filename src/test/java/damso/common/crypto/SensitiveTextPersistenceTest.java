package damso.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import damso.support.IntegrationTestSupport;
import damso.support.SecretNote;
import damso.support.SecretNoteRepository;

/** US4 / SC-004: SensitiveText 필드는 DB에 평문으로 남지 않고, 다시 읽으면 원문이다. */
class SensitiveTextPersistenceTest extends IntegrationTestSupport {

	@Autowired
	private SecretNoteRepository repository;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void storedValueIsEncryptedAndReadBackAsPlaintext() {
		String plain = "학생 이름이 들어간 민감한 문의 본문 " + UUID.randomUUID();
		SecretNote saved = repository.saveAndFlush(new SecretNote(SensitiveText.of(plain)));

		String raw = rawContent(saved.getId());
		assertThat(raw).isNotEqualTo(plain).doesNotContain("민감한");
		assertThat(Base64.getDecoder().decode(raw)[0]).isEqualTo((byte) 0x01);

		assertThat(repository.findById(saved.getId()).orElseThrow().getContent().reveal()).isEqualTo(plain);
	}

	@Test
	void samePlaintextIsStoredDifferently() {
		String plain = "same body " + UUID.randomUUID();
		SecretNote first = repository.saveAndFlush(new SecretNote(SensitiveText.of(plain)));
		SecretNote second = repository.saveAndFlush(new SecretNote(SensitiveText.of(plain)));
		assertThat(rawContent(first.getId())).isNotEqualTo(rawContent(second.getId()));
	}

	@Test
	void tamperedValueIsNotReturnedAsPlaintext() {
		SecretNote saved = repository.saveAndFlush(new SecretNote(SensitiveText.of("original " + UUID.randomUUID())));
		byte[] raw = Base64.getDecoder().decode(rawContent(saved.getId()));
		raw[raw.length - 1] ^= 0x01;
		jdbc.update("update test_secret_note set content = ? where id = ?",
				Base64.getEncoder().encodeToString(raw), saved.getId());

		assertThatThrownBy(() -> repository.findById(saved.getId()))
				.hasRootCauseInstanceOf(ColumnCryptoException.class);
	}

	private String rawContent(UUID id) {
		return jdbc.queryForObject("select content from test_secret_note where id = ?", String.class, id);
	}

}
