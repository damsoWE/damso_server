package damso.account.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import damso.account.domain.PendingSignup;

public interface PendingSignupPort {

	PendingSignup save(PendingSignup pending);

	Optional<PendingSignup> findPendingById(UUID id);

	/** 같은 이메일의 미완료 건을 모두 지운다(이메일당 미완료 1건). */
	void deleteIncompleteByEmail(String email);

	/** 완료·만료 후 {@code before} 이전인 건을 지우고 지운 수를 돌려준다. */
	int deleteStalePending(Instant before);

}
