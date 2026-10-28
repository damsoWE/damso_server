package damso.inquiry.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 정리·분류 작업이 커밋되면 바로 실행한다. 요청 스레드 밖(@Async)에서 돌려 접수 응답이 외부 AI를 기다리지 않는다.
 * 실패해도 예외를 삼킨다 — 작업은 DB에 남아 있고 10초 주기 실행기가 안전망이다. 로그에는 작업 UUID만 남긴다.
 * damso.refinement.dispatch-on-commit=false면 즉시 실행하지 않고 안전망에만 맡긴다(테스트에서 "정리 확정 전" 상태를 보려고 쓴다).
 */
@Component
class RefinementDispatchTrigger {

	private static final Logger log = LoggerFactory.getLogger(RefinementDispatchTrigger.class);

	private final RefinementRunner runner;

	private final boolean enabled;

	RefinementDispatchTrigger(RefinementRunner runner,
			@Value("${damso.refinement.dispatch-on-commit:true}") boolean enabled) {
		this.runner = runner;
		this.enabled = enabled;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onRefinementRequested(RefinementRequested event) {
		if (!enabled) {
			return;
		}
		try {
			runner.dispatchOne(event.jobId());
		}
		catch (RuntimeException ex) {
			log.warn("정리 작업 즉시 실행 실패, 안전망 실행기가 이어받습니다 (job={}, type={})", event.jobId(),
					ex.getClass().getSimpleName());
		}
	}

}
