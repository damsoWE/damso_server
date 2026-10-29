package damso.gateway.application.service;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import damso.gateway.application.port.out.ExternalAiPort;
import damso.gateway.application.port.out.ExternalAiPort.AiReply;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.application.port.out.PromptSource;
import damso.gateway.application.port.out.SchoolTermsSource;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.MaskedText;
import damso.gateway.domain.MaskingVerifier;
import damso.gateway.domain.PseudonymMap;
import damso.gateway.domain.Pseudonymizer;
import damso.gateway.domain.Pseudonymizer.Dictionary;
import damso.gateway.domain.Pseudonymizer.Masked;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이의 공통 흐름 (원칙 II, research R1·R4): 학교 용어 합치기 → 사전을 한 번 만들기 → 가명화 → 가린 뒤 자기 점검
 * (못 가렸으면 외부 호출 없음) → 외부 AI. 정리·위기 분류 두 입구가 이 흐름을 함께 쓰고, 결과 검증만 각자 한다.
 * 대응표(PseudonymMap)는 결과로만 넘기고 필드·캐시·로그에 남기지 않는다. 로그에는 실패 종류만 남긴다.
 * 답변 점검(F4 research R16)은 여러 글(답변 초안·정리 결과)을 한 사전·한 번의 가명화로 가린다 — 같은 이름이 같은 토큰이 되게.
 */
@Component
class MaskedAiCall {

	private static final Logger log = LoggerFactory.getLogger(MaskedAiCall.class);

	/**
	 * 여러 글을 이어 붙일 때의 구분 문자(사설 영역 U+E000). 가명화 패턴(이름·연락처·주소)이 이 문자를 넘어 매치되지 않으므로
	 * 가린 뒤 그대로 다시 나눌 수 있다. 입력에 이 문자가 있으면 공백으로 바꾼다.
	 */
	static final String SEPARATOR = "\uE000";

	private static final Pattern SEPARATOR_PATTERN = Pattern.compile(SEPARATOR);

	/**
	 * 성공이면 외부 AI 응답, 대응표, 가린 글들(입력과 같은 순서), 실패면 failure 하나만 있다. toString은 응답·대응표·글을 보이지 않는다.
	 */
	record Outcome(GatewayFailure failure, AiReply reply, PseudonymMap map, List<String> maskedSegments) {

		static Outcome failed(GatewayFailure failure) {
			return new Outcome(failure, null, null, List.of());
		}

		boolean succeeded() {
			return failure == null;
		}

		@Override
		public String toString() {
			return "Outcome[" + (succeeded() ? "RESPONDED" : failure) + "]";
		}

	}

	private final ExternalAiPort ai;

	private final PromptSource prompts;

	private final SchoolTermsSource schoolTerms;

	MaskedAiCall(ExternalAiPort ai, PromptSource prompts, SchoolTermsSource schoolTerms) {
		this.ai = ai;
		this.prompts = prompts;
		this.schoolTerms = schoolTerms;
	}

	Outcome call(AiTask task, String content, Roster roster) {
		return call(task, Collections.singletonList(content), roster, segments -> segments.get(0));
	}

	/**
	 * 글들을 구분 문자로 이어 한 번에 가리고, 자기 점검을 통과하면 다시 나눠 compose로 외부에 보낼 글을 만든다.
	 * compose는 가린 글만 받는다 — 가리기 전 글은 외부로 나갈 수 없다.
	 */
	Outcome call(AiTask task, List<String> contents, Roster roster, Function<List<String>, String> compose) {
		Dictionary dictionary = Dictionary.of(roster.withSchoolTerms(schoolTerms.terms()));
		Masked masked;
		List<String> segments;
		MaskedText outgoing;
		try {
			String joined = String.join(SEPARATOR,
					contents.stream().map(content -> SEPARATOR_PATTERN.matcher(content).replaceAll(" ")).toList());
			masked = Pseudonymizer.mask(joined, dictionary);
			segments = List.of(SEPARATOR_PATTERN.split(masked.text().value(), -1));
			if (segments.size() != contents.size()) {
				throw new IllegalStateException("segment count changed");
			}
			outgoing = new MaskedText(compose.apply(segments));
		}
		catch (RuntimeException ex) {
			log.warn("가명화 실패 (task={}, type={})", task, ex.getClass().getSimpleName());
			return Outcome.failed(GatewayFailure.MASKING_FAILED);
		}
		if (MaskingVerifier.verify(masked.text(), dictionary).isPresent()) {
			log.warn("가린 뒤 자기 점검 실패, 외부 호출 안 함 (task={})", task);
			return Outcome.failed(GatewayFailure.MASKING_FAILED);
		}
		AiReply reply;
		try {
			reply = ai.call(task, prompts.systemPrompt(task), outgoing);
		}
		catch (RuntimeException ex) {
			log.warn("외부 AI 호출 오류 (task={}, type={})", task, ex.getClass().getSimpleName());
			return Outcome.failed(GatewayFailure.NO_RESPONSE);
		}
		if (!reply.responded()) {
			return Outcome.failed(GatewayFailure.NO_RESPONSE);
		}
		return new Outcome(null, reply, masked.map(), segments);
	}

}
