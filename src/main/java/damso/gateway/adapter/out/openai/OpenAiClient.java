package damso.gateway.adapter.out.openai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

import damso.gateway.application.port.out.ExternalAiPort;
import damso.gateway.domain.EmotionLabel;
import damso.gateway.domain.EmotionLevel;
import damso.gateway.domain.MaskedText;

/**
 * OpenAI HTTP API로 외부 AI를 호출한다 (D1 — RestClient, 새 의존성 없음).
 * - 대상 주소는 코드 상수다. 설정이나 입력으로 바꿀 수 없다(SSRF).
 * - 시스템 지시는 system 역할, 가린 본문은 user 역할로만 보낸다. 본문을 지시에 이어 붙이지 않는다(L1).
 * - 출력은 JSON 스키마(strict)로 강제하고, 공급자 측 저장을 끈다(store: false). 결과는 서버가 다시 검증한다(RefinementResult).
 * - 시간 초과·연결 실패·오류 응답·거부 응답은 모두 응답 없음이다. 로그에는 작업 종류와 HTTP 상태만 남기고 요청·응답 본문과
 *   공급자의 오류 본문은 남기지 않는다.
 */
@Component
class OpenAiClient implements ExternalAiPort {

	private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

	static final String BASE_URL = "https://api.openai.com";

	static final String PATH = "/v1/chat/completions";

	private final RestClient client;

	private final String model;

	/** 이 빈이 만든 HTTP 클라이언트. 종료 때 닫는다. 테스트가 빌더를 넘겨 만든 경우에는 null이다. */
	private final HttpClient httpClient;

	@Autowired
	OpenAiClient(@Value("${damso.openai.api-key}") String apiKey, @Value("${damso.openai.model}") String model,
			@Value("${damso.openai.timeout.connect:3s}") Duration connectTimeout,
			@Value("${damso.openai.timeout.read:15s}") Duration readTimeout) {
		this(HttpClient.newBuilder().connectTimeout(connectTimeout).build(), readTimeout, apiKey, model);
	}

	private OpenAiClient(HttpClient httpClient, Duration readTimeout, String apiKey, String model) {
		this(RestClient.builder().requestFactory(requestFactory(httpClient, readTimeout)), apiKey, model, httpClient);
	}

	OpenAiClient(RestClient.Builder builder, String apiKey, String model) {
		this(builder, apiKey, model, null);
	}

	private OpenAiClient(RestClient.Builder builder, String apiKey, String model, HttpClient httpClient) {
		this.client = builder.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
		this.model = model;
		this.httpClient = httpClient;
	}

	private static JdkClientHttpRequestFactory requestFactory(HttpClient httpClient, Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(readTimeout);
		return factory;
	}

	/** 앱이 내려갈 때 HTTP 클라이언트의 연결·스레드를 놓는다. 진행 중인 호출은 읽기 시간 제한 안에서 끝난다. */
	@PreDestroy
	void close() {
		if (httpClient != null) {
			httpClient.close();
		}
	}

	@Override
	public AiReply call(AiTask task, String systemPrompt, MaskedText userText) {
		Map<String, Object> request = Map.of(
				"model", model,
				"store", false,
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userText.value())),
				"response_format", Map.of("type", "json_schema", "json_schema", schemaOf(task)));
		JsonNode response;
		try {
			response = client.post()
					.uri(PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(JsonNode.class);
		}
		catch (RestClientResponseException ex) {
			log.warn("외부 AI 오류 응답 (task={}, status={})", task, ex.getStatusCode().value());
			return AiReply.noResponse();
		}
		catch (RestClientException ex) {
			log.warn("외부 AI 호출 실패 (task={}, type={})", task, ex.getClass().getSimpleName());
			return AiReply.noResponse();
		}
		JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
		if (message == null || !message.path("content").isString() || message.path("refusal").isString()) {
			log.warn("외부 AI 응답에 결과가 없음 (task={})", task);
			return AiReply.noResponse();
		}
		return new AiReply(message.path("content").asString());
	}

	/** 작업별 출력 형식. 길이·개수 같은 나머지 규칙은 서버가 다시 검증한다. */
	static Map<String, Object> schemaOf(AiTask task) {
		return switch (task) {
			case REFINE -> Map.of("name", "inquiry_refinement", "strict", true, "schema", Map.of(
					"type", "object",
					"properties", Map.ofEntries(
							Map.entry("title", Map.of("type", "string")),
							Map.entry("facts", Map.of("type", "string")),
							Map.entry("requests", Map.of("type", "string")),
							Map.entry("emotionLevel", Map.of("type", "string", "enum", names(EmotionLevel.values()))),
							Map.entry("emotionLabels", Map.of("type", "array", "items",
									Map.of("type", "string", "enum", names(EmotionLabel.values())))),
							Map.entry("threatToTeacher", Map.of("type", "boolean")),
							Map.entry("threatToStudent", Map.of("type", "boolean")),
							Map.entry("abuseTowardTeacher", Map.of("type", "boolean")),
							Map.entry("abuseTowardStudent", Map.of("type", "boolean"))),
					"required", List.of("title", "facts", "requests", "emotionLevel", "emotionLabels",
							"threatToTeacher", "threatToStudent", "abuseTowardTeacher", "abuseTowardStudent"),
					"additionalProperties", false));
			case CLASSIFY_CRISIS -> Map.of("name", "crisis_type", "strict", true, "schema", Map.of(
					"type", "object",
					"properties", Map.of("crisisType",
							Map.of("type", "string", "enum", List.of("SELF_HARM", "ABUSE", "OTHER_SAFETY"))),
					"required", List.of("crisisType"),
					"additionalProperties", false));
		};
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

}
