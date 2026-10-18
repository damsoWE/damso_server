package damso.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.PendingSignupPort;
import damso.account.application.service.ExpiredVerificationCleanup;
import damso.account.domain.PasswordReset;
import damso.account.domain.PendingSignup;
import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/** data-model.md: 확정·만료 후 24시간이 지난 가입 대기·재설정 건만 정리한다. */
class ExpiredVerificationCleanupTest extends IntegrationTestSupport {

	@Autowired
	private ExpiredVerificationCleanup cleanup;

	@Autowired
	private PendingSignupPort pendingSignups;

	@Autowired
	private PasswordResetPort passwordResets;

	private PendingSignup pending(String email) {
		return pendingSignups.save(new PendingSignup(email, "김선생", "hash-not-a-real-password", UUID.randomUUID(),
				"a".repeat(64), clock.instant()));
	}

	@Test
	void removesOnlyRecordsOlderThan24HoursAfterTheyExpired() {
		PendingSignup old = pending("old@damso.test");
		UUID userId = createAccount(Role.PARENT).getId();
		passwordResets.save(new PasswordReset(userId, "b".repeat(64), clock.instant()));

		clock.advance(Duration.ofHours(25));
		PendingSignup fresh = pending("fresh@damso.test");

		int removed = cleanup.cleanup();

		assertThat(removed).isEqualTo(2);
		assertThat(pendingSignups.findPendingById(old.getId())).isEmpty();
		assertThat(pendingSignups.findPendingById(fresh.getId())).isPresent();
		assertThat(passwordResets.findByUserId(userId)).isEmpty();
	}

	@Test
	void keepsRecentRecordsEvenIfTheirCodeAlreadyExpired() {
		PendingSignup recent = pending("recent@damso.test");

		clock.advance(Duration.ofHours(1)); // 코드는 만료(10분)됐지만 24시간은 안 지났다

		assertThat(cleanup.cleanup()).isZero();
		assertThat(pendingSignups.findPendingById(recent.getId())).isPresent();
	}

}
