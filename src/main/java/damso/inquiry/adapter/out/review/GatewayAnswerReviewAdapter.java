package damso.inquiry.adapter.out.review;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import damso.gateway.application.port.in.ReviewAnswerGateway;
import damso.gateway.application.port.in.ReviewAnswerGateway.Context;
import damso.gateway.application.port.in.ReviewAnswerGateway.ReviewOutcome;
import damso.gateway.domain.Roster;
import damso.inquiry.application.port.out.AnswerReviewPort;
import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.AnswerReviewResult.Highlight;

/**
 * 답변 점검을 가명화 게이트웨이({@link ReviewAnswerGateway})로 한다 (원칙 II, research R4 Update·R16·R18).
 * 게이트웨이 호출 전체(가명화·외부 AI·검증·복원)를 가상 스레드에서 돌리고 {@code damso.answer-review.timeout}(기본 8초) 안에 끝나지
 * 않으면 취소하고 결과 없음으로 돌려준다 — DB 조회·감사 기록을 더해도 10초 안에 응답하게(SC-002, FR-012b).
 * 실패·시간 초과는 모두 {@link Optional#empty()}다(fail-open). 로그에는 실패 종류만 남긴다.
 */
@Component
class GatewayAnswerReviewAdapter implements AnswerReviewPort {

	private static final Logger log = LoggerFactory.getLogger(GatewayAnswerReviewAdapter.class);

	private final ReviewAnswerGateway gateway;

	private final Duration timeout;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	GatewayAnswerReviewAdapter(ReviewAnswerGateway gateway,
			@Value("${damso.answer-review.timeout:8s}") Duration timeout) {
		this.gateway = gateway;
		this.timeout = timeout;
	}

	/** 앱이 내려갈 때 진행 중인 점검을 끊는다. */
	@PreDestroy
	void close() {
		executor.shutdownNow();
	}

	@Override
	public Optional<AnswerReviewResult> review(String draftContent, ReviewContext context, Roster roster) {
		Context gatewayContext = context == null ? null
				: new Context(context.facts(), context.requests(), context.emotionLevel(), context.emotionLabels());
		Future<ReviewOutcome> pending = executor.submit(() -> gateway.review(draftContent, gatewayContext, roster));
		ReviewOutcome outcome;
		try {
			outcome = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			pending.cancel(true);
			log.warn("답변 점검 시간 초과 (timeout={}ms)", timeout.toMillis());
			return Optional.empty();
		}
		catch (InterruptedException ex) {
			pending.cancel(true);
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
		catch (ExecutionException ex) {
			// 예외 메시지에 글이 섞일 수 있어 종류만 남긴다
			log.warn("답변 점검 게이트웨이 오류 (type={})",
					ex.getCause() == null ? "unknown" : ex.getCause().getClass().getSimpleName());
			return Optional.empty();
		}
		if (!outcome.succeeded()) {
			log.warn("답변 점검 실패 (failure={})", outcome.failure());
			return Optional.empty();
		}
		return Optional.of(new AnswerReviewResult(true, context != null, outcome.findings().stream()
				.map(finding -> new Highlight(finding.excerpt(), finding.reason(), finding.suggestion()))
				.toList()));
	}

}
