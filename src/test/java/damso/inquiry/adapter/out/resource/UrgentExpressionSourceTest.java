package damso.inquiry.adapter.out.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** F2 T011 / 원칙 VIII, research R2: 목록이 비었거나 읽지 못하면 기동 거부, 주석·빈 줄 무시, 정규화해서 보관. */
class UrgentExpressionSourceTest {

	private static ByteArrayResource text(String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void ignoresCommentsAndBlankLinesAndNormalizes() {
		var source = new ClasspathUrgentExpressionSource(text("# 주석\n\n 죽고 싶 \n  \nSelf Harm\n자해\n자해\n"));

		assertThat(source.expressions()).containsExactly("죽고싶", "selfharm", "자해");
	}

	@Test
	void emptyListRefusesToStart() {
		assertThatThrownBy(() -> new ClasspathUrgentExpressionSource(text("")))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new ClasspathUrgentExpressionSource(text("# 주석뿐\n\n   \n")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void missingFileRefusesToStart() {
		assertThatThrownBy(() -> new ClasspathUrgentExpressionSource(new ClassPathResource("urgent/none.txt")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void shippedListLoads() {
		assertThat(new ClasspathUrgentExpressionSource(new ClassPathResource("urgent/expressions.txt")).expressions())
				.isNotEmpty();
	}

}
