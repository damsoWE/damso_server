package damso.gateway.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import damso.gateway.application.port.out.ExternalAiPort;
import damso.gateway.application.port.out.ExternalAiPort.AiReply;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.application.port.out.PromptSource;
import damso.gateway.application.port.out.SchoolTermsSource;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.MaskingVerifier;
import damso.gateway.domain.PseudonymMap;
import damso.gateway.domain.Pseudonymizer;
import damso.gateway.domain.Pseudonymizer.Dictionary;
import damso.gateway.domain.Pseudonymizer.Masked;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이의 공통 흐름: 학교 용어 합치기 → 사전을 한 번 만들기 → 가명화 → 가린 뒤 자기 점검
 * (못 가렸으면 외부 호출 없음) → 외부 AI. 정리·위기 분류 두 입구가 이 흐름을 함께 쓰고, 결과 검증만 각자 한다.
 * 대응표(PseudonymMap)는 결과로만 넘기고 필드·캐시·로그에 남기지 않는다. 로그에는 실패 종류만 남긴다.
 */
@Component
class MaskedAiCall {

	private static final Logger log = LoggerFactory.getLogger(MaskedAiCall.class);

	/** 성공이면 외부 AI 응답과 대응표, 실패면 failure 하나만 있다. toString은 응답·대응표 내용을 보이지 않는다. */
	record Outcome(GatewayFailure failure, AiReply reply, PseudonymMap map) {

		static Outcome failed(GatewayFailure failure) {
			return new Outcome(failure, null, null);
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
		Dictionary dictionary = Dictionary.of(roster.withSchoolTerms(schoolTerms.terms()));
		Masked masked;
		try {
			masked = Pseudonymizer.mask(content, dictionary);
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
			reply = ai.call(task, prompts.systemPrompt(task), masked.text());
		}
		catch (RuntimeException ex) {
			log.warn("외부 AI 호출 오류 (task={}, type={})", task, ex.getClass().getSimpleName());
			return Outcome.failed(GatewayFailure.NO_RESPONSE);
		}
		if (!reply.responded()) {
			return Outcome.failed(GatewayFailure.NO_RESPONSE);
		}
		return new Outcome(null, reply, masked.map());
	}

}
