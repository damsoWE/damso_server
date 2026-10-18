package damso.account.application.port.out;

import java.util.Optional;
import java.util.UUID;

import damso.account.domain.UserAccount;

public interface UserAccountPort {

	UserAccount save(UserAccount account);

	Optional<UserAccount> findById(UUID id);

	/** 이메일은 소문자로 정규화해서 넘긴다. */
	Optional<UserAccount> findByEmail(String email);

	boolean existsByEmail(String email);

}
