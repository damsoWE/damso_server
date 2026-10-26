package damso.inquiry.adapter.out.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** spec 005 T007 / 원칙 VIII, research R3: 목록이 비었거나 읽지 못하면 기동 거부, 주석·빈 줄 무시, 정규화해서 보관. */
class ThreatExpressionSourceTest {

	private static ByteArrayResource text(String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void ignoresCommentsAndBlankLinesAndNormalizesKeepingInnerSpaces() {
		var source = new ClasspathThreatExpressionSource(
				text("# 주석\n\n 가만   두지 않 \n  \nShut　Up\n지랄\n지랄\n"));

		assertThat(source.expressions()).containsExactly("가만 두지 않", "shut up", "지랄");
	}

	@Test
	void emptyListRefusesToStart() {
		assertThatThrownBy(() -> new ClasspathThreatExpressionSource(text("")))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new ClasspathThreatExpressionSource(text("# 주석뿐\n\n   \n")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void missingFileRefusesToStart() {
		assertThatThrownBy(() -> new ClasspathThreatExpressionSource(new ClassPathResource("threat/none.txt")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusalMessageDoesNotEchoTheFile() {
		assertThatThrownBy(() -> new ClasspathThreatExpressionSource(text("# 비밀 주석\n")))
				.hasMessageNotContaining("비밀");
	}

	@Test
	void shippedListLoads() {
		assertThat(new ClasspathThreatExpressionSource(new ClassPathResource("threat/expressions.txt")).expressions())
				.isNotEmpty();
	}

}
