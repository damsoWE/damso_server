package damso.account.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import damso.account.domain.PasswordReset;

public interface PasswordResetPort {

	PasswordReset save(PasswordReset reset);

	Optional<PasswordReset> findByUserId(UUID userId);

	/** 사용자의 이전 재설정 건을 지운다(다시 요청하면 이전 코드는 무효). */
	void deleteByUserId(UUID userId);

	/** 사용·만료 후 {@code before} 이전인 건을 지우고 지운 수를 돌려준다. */
	int deleteStaleResets(Instant before);

}
