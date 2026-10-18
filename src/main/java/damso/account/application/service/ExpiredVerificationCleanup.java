package damso.account.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.PendingSignupPort;

/**
 * 하루 1회, 확정·사용·만료 후 24시간이 지난 가입 대기·비밀번호 재설정 건을 지운다 (data-model.md).
 * 이력 데이터가 아니라 인증 흐름의 임시 기록이다. 로그에는 지운 건수만 남긴다(이메일 없음).
 */
@Component
public class ExpiredVerificationCleanup {

	private static final Logger log = LoggerFactory.getLogger(ExpiredVerificationCleanup.class);

	static final Duration RETENTION = Duration.ofHours(24);

	private final PendingSignupPort pendingSignups;

	private final PasswordResetPort passwordResets;

	private final Clock clock;

	ExpiredVerificationCleanup(PendingSignupPort pendingSignups, PasswordResetPort passwordResets, Clock clock) {
		this.pendingSignups = pendingSignups;
		this.passwordResets = passwordResets;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 4 * * *")
	public void scheduled() {
		cleanup();
	}

	@Transactional
	public int cleanup() {
		Instant before = clock.instant().minus(RETENTION);
		int pending = pendingSignups.deleteStalePending(before);
		int resets = passwordResets.deleteStaleResets(before);
		log.info("expired verification records removed: pendingSignups={}, passwordResets={}", pending, resets);
		return pending + resets;
	}

}
