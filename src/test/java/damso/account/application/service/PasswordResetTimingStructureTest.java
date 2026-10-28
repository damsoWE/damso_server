package damso.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.common.crypto.CodeHasher;

/**
 * 이슈 #22 / spec "응답 통일": 재설정 요청 응답은 발송 제한 확인까지만 하고, 가입된 이메일일 때만 하는 계정 조회·코드 저장·메일은
 * 응답 뒤(커밋 후, 요청 스레드 밖)에 한다 — 가입 여부가 응답 시간에 드러나지 않는다. 통합 테스트는 @Async를 동기 실행기로
 * 돌리므로 운영 구조는 여기서 확인한다(UrgentDispatchTriggerStructureTest와 같은 방식).
 */
class PasswordResetTimingStructureTest {

	@Test
	void requestOnlyChecksTheLimitAndNeverTouchesAccountsOnTheRequestThread() {
		UserAccountPort accounts = mock(UserAccountPort.class);
		PasswordResetPort resets = mock(PasswordResetPort.class);
		AttemptLimitUseCase attempts = mock(AttemptLimitUseCase.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		PasswordResetService service = new PasswordResetService(accounts, resets, mock(SessionPort.class), attempts,
				events, mock(PasswordEncoder.class), mock(CodeHasher.class), Clock.systemUTC());

		service.request("Parent@Example.com", "203.0.113.7");

		verify(attempts).ensureResetMailAllowed("parent@example.com", "203.0.113.7");
		verify(events).publishEvent(any(PasswordResetRequested.class));
		verifyNoInteractions(accounts, resets);
	}

	@Test
	void issuingRunsAfterCommitOffTheRequestThreadInItsOwnTransaction() throws Exception {
		Method listener = PasswordResetIssuer.class.getDeclaredMethod("onPasswordResetRequested",
				PasswordResetRequested.class);

		assertThat(listener.isAnnotationPresent(Async.class)).isTrue();
		TransactionalEventListener config = listener.getAnnotation(TransactionalEventListener.class);
		assertThat(config).isNotNull();
		assertThat(config.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		assertThat(listener.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
	}

	@Test
	void requestedEventDoesNotLeakTheEmail() {
		assertThat(new PasswordResetRequested("parent@example.com").toString()).doesNotContain("parent@example.com");
	}

}
