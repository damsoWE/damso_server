package damso.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.out.ExternalAiPort;
import damso.gateway.domain.MaskedText;

/**
 * 테스트용 외부 AI. 실제 외부 AI에 접속하지 않는다. 받은 가린 본문을 기록하고, 테스트가 응답을 차례로 정할 수 있다.
 * - 정해 둔 응답이 없으면: 정리(REFINE)는 가린 본문을 facts에 그대로 담은 유효한 결과(토큰이 그대로 남아 복원을 확인할 수 있다),
 *   위기 분류(CLASSIFY_CRISIS)·답변 점검(REVIEW_ANSWER)은 응답 없음(기존 기본 동작이 바뀌지 않게).
 */
public class FakeExternalAi implements ExternalAiPort {

	private static final ObjectMapper JSON = new ObjectMapper();

	public record Call(AiTask task, String systemPrompt, String userText) {
	}

	private final List<Call> calls = new CopyOnWriteArrayList<>();

	private final Queue<Function<Call, AiReply>> replies = new ConcurrentLinkedQueue<>();

	@Override
	public AiReply call(AiTask task, String systemPrompt, MaskedText userText) {
		Call call = new Call(task, systemPrompt, userText.value());
		calls.add(call);
		Function<Call, AiReply> next = replies.poll();
		if (next != null) {
			return next.apply(call);
		}
		return task == AiTask.REFINE ? new AiReply(refinementJson(call.userText())) : AiReply.noResponse();
	}

	/** 다음 호출에 이 JSON을 돌려준다(형식 위반 JSON도 그대로). */
	public void replyNext(String json) {
		replies.add(call -> new AiReply(json));
	}

	/** 다음 호출은 응답 없음(시간 초과·오류). */
	public void noResponseNext() {
		replies.add(call -> AiReply.noResponse());
	}

	/** 다음 호출은 예외를 던진다. */
	public void throwNext() {
		replies.add(call -> {
			throw new IllegalStateException("external ai is down");
		});
	}

	/** 다음 호출은 이 시간만큼 기다린 뒤 이 JSON을 돌려준다. 기다리는 중에 중단되면 응답 없음이다(점검 상한 시험용). */
	public void replyAfter(Duration delay, String json) {
		replies.add(call -> {
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return AiReply.noResponse();
			}
			return new AiReply(json);
		});
	}

	/** 다음 호출은 기본 유효 결과(가린 본문을 facts로)를 돌려준다. */
	public void validNext() {
		replies.add(call -> new AiReply(refinementJson(call.userText())));
	}

	public List<Call> calls() {
		return new ArrayList<>(calls);
	}

	public List<Call> callsOf(AiTask task) {
		return calls.stream().filter(call -> call.task() == task).toList();
	}

	public void clear() {
		calls.clear();
		replies.clear();
	}

	/** 유효한 정리 결과 JSON. facts는 가린 본문(1,000자까지), 토큰이 그대로 남는다. */
	public static String refinementJson(String maskedText) {
		String facts = maskedText.codePointCount(0, maskedText.length()) > 1000
				? maskedText.substring(0, maskedText.offsetByCodePoints(0, 1000)) : maskedText;
		return refinementJson("문의 정리", facts, "", "MEDIUM", List.of("WORRY"));
	}

	public static String refinementJson(String title, String facts, String requests, String level,
			List<String> labels) {
		return JSON.writeValueAsString(Map.of("title", title, "facts", facts, "requests", requests, "emotionLevel",
				level, "emotionLabels", labels, "threatToTeacher", false, "threatToStudent", false, "abuseTowardTeacher",
				false, "abuseTowardStudent", false));
	}

	/** 답변 점검 결과 JSON. 지적 하나는 {excerpt, reason, suggestion} 순서의 세 문자열이다. */
	public static String reviewJson(String[]... highlights) {
		List<Map<String, String>> items = new ArrayList<>();
		for (String[] highlight : highlights) {
			items.add(Map.of("excerpt", highlight[0], "reason", highlight[1], "suggestion", highlight[2]));
		}
		return JSON.writeValueAsString(Map.of("highlights", items));
	}

	public static String crisisJson(String type) {
		return JSON.writeValueAsString(Map.of("crisisType", type));
	}

}
