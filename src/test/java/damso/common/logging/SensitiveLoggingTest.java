package damso.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import damso.common.crypto.SensitiveText;
import damso.support.IntegrationTestSupport;
import damso.support.SecretNote;
import damso.support.SecretNoteRepository;

/** US5 / SC-005: 민감 값은 어떤 로그 경로로도 원문이 남지 않는다. */
@ExtendWith(OutputCaptureExtension.class)
class SensitiveLoggingTest extends IntegrationTestSupport {

	private static final Logger log = LoggerFactory.getLogger(SensitiveLoggingTest.class);
	private static final String MARKER = "PII-MARKER-7f3a";

	@Autowired
	private SecretNoteRepository repository;

	@Test
	void sensitiveValuesAreMaskedInDirectLogging(CapturedOutput output) {
		SensitiveText text = SensitiveText.of("김OO 학생 " + MARKER);
		SecretNote note = repository.saveAndFlush(new SecretNote(text));

		log.info("text={}", text);
		log.info("entity={}", note);
		log.error("failure", new IllegalStateException("could not process " + text));

		assertThat(output.getAll()).contains("[MASKED]").doesNotContain(MARKER);
	}

	@Test
	void requestBodyIsNotLoggedWhenHandlingFails(CapturedOutput output) throws Exception {
		mockMvc.perform(post("/test/errors/sensitive-body")
				.header(HttpHeaders.AUTHORIZATION, bearer(validToken("PARENT")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"" + MARKER + "\"}"));

		assertThat(output.getAll()).contains("Unhandled exception").doesNotContain(MARKER);
	}

	@Test
	void brokenJsonBodyIsNotLogged(CapturedOutput output) throws Exception {
		mockMvc.perform(post("/test/errors/body")
				.header(HttpHeaders.AUTHORIZATION, bearer(validToken("PARENT")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\": " + MARKER + " }"));

		assertThat(output.getAll()).doesNotContain(MARKER);
	}

}
