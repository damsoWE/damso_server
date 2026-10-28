package damso.account.application.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.application.port.out.VerificationMailPort;
import damso.account.domain.PasswordReset;
import damso.account.domain.UserAccount;
import damso.common.crypto.CodeHasher;
import damso.common.crypto.SecureCodes;

/**
 * 재설정 코드 발급. 요청 스레드 밖(@Async)에서 돌려, 가입된 이메일일 때만 하는 계정 조회 이후 작업(코드 저장·메일)이
 * 응답 시간에 드러나지 않게 한다 — 응답은 발송 제한 확인까지만 보고 끝난다(FR-029, spec "응답 통일", 이슈 #22).
 * 실패해도 예외를 삼키고 로그에는 예외 종류만 남긴다(이메일 없음).
 */
@Component
class PasswordResetIssuer {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetIssuer.class);

	private final UserAccountPort accounts;

	private final PasswordResetPort resets;

	private final VerificationMailPort mail;

	private final CodeHasher hasher;

	private final Clock clock;

	PasswordResetIssuer(UserAccountPort accounts, PasswordResetPort resets, VerificationMailPort mail, CodeHasher hasher,
			Clock clock) {
		this.accounts = accounts;
		this.resets = resets;
		this.mail = mail;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onPasswordResetRequested(PasswordResetRequested event) {
		try {
			// 가입된 활성 계정일 때만 코드를 만든다
			UserAccount account = accounts.findByEmail(event.email()).filter(UserAccount::isActive).orElse(null);
			if (account == null) {
				return;
			}
			String code = SecureCodes.sixDigits();
			resets.deleteByUserId(account.getId());
			resets.save(new PasswordReset(account.getId(), hasher.hash(code), clock.instant()));
			mail.sendPasswordResetCode(event.email(), code);
		}
		catch (RuntimeException ex) {
			log.warn("password reset issue failed: cause={}", ex.getClass().getSimpleName());
		}
	}

}
