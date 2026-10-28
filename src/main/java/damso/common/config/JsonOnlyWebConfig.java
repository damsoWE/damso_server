package damso.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 응답 형식은 JSON 하나로 고정한다(보안약점 체크리스트 입력데이터 4). 요청의 Accept 헤더로 다른 형식(XML 등)을 고르지 못하게 해,
 * 오류 응답까지 항상 같은 JSON 본문으로 나간다. 요청 본문 쪽은 XML 변환기를 의존성에서 뺐다(build.gradle).
 */
@Configuration
public class JsonOnlyWebConfig implements WebMvcConfigurer {

	@Override
	public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
		configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
	}

}
