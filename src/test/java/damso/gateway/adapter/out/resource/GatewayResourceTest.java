package damso.gateway.adapter.out.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import damso.gateway.application.port.out.ExternalAiPort.AiTask;

/** F3 T014 / 원칙 VIII, research R2·R5: 학교 용어·프롬프트 파일이 비었거나 없으면 기동 거부, 배포 파일은 정해진 내용. */
class GatewayResourceTest {

	private static ByteArrayResource text(String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}

	private static final ClassPathResource REFINE = new ClassPathResource("gateway/prompts/refine.txt");

	private static final ClassPathResource CLASSIFY = new ClassPathResource("gateway/prompts/classify-crisis.txt");

	private static final ClassPathResource REVIEW = new ClassPathResource("gateway/prompts/review-answer.txt");

	@Test
	void shippedSchoolTermsAreTheFourAgreedVariants() {
		var source = new ClasspathSchoolTermsSource(new ClassPathResource("pseudonymization/school-terms.txt"));

		assertThat(source.terms()).containsExactly("한양초등학교", "한양 초등학교", "한양초교", "한양초");
	}

	@Test
	void schoolTermsIgnoreCommentsAndBlankLines() {
		var source = new ClasspathSchoolTermsSource(text("# 주석\n\n 한양초 \n한양초\n"));

		assertThat(source.terms()).containsExactly("한양초");
	}

	@Test
	void emptyOrMissingSchoolTermsRefuseToStart() {
		assertThatThrownBy(() -> new ClasspathSchoolTermsSource(text("")))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new ClasspathSchoolTermsSource(text("# 주석뿐\n\n")))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new ClasspathSchoolTermsSource(new ClassPathResource("pseudonymization/none.txt")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void shippedPromptsLoadPerTask() {
		var source = new ClasspathPromptSource(REFINE, CLASSIFY, REVIEW);

		assertThat(source.systemPrompt(AiTask.REFINE)).contains("title").contains("emotionLabels");
		assertThat(source.systemPrompt(AiTask.CLASSIFY_CRISIS)).contains("crisisType").contains("SELF_HARM");
		assertThat(source.systemPrompt(AiTask.REVIEW_ANSWER)).contains("highlights").contains("excerpt")
				.contains("근거 누락");
	}

	@Test
	void emptyOrMissingPromptRefusesToStartWithoutPrintingContent() {
		assertThatThrownBy(() -> new ClasspathPromptSource(text("   \n"), CLASSIFY, REVIEW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("정리 지시 파일");
		assertThatThrownBy(() -> new ClasspathPromptSource(REFINE, new ClassPathResource("gateway/prompts/none.txt"), REVIEW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("위기 분류 지시 파일");
		assertThatThrownBy(() -> new ClasspathPromptSource(REFINE, CLASSIFY, text("")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("답변 점검 지시 파일");
	}

}
