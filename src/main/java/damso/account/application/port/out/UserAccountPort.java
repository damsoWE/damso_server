package damso.account.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.account.domain.UserAccount;

public interface UserAccountPort {

	UserAccount save(UserAccount account);

	Optional<UserAccount> findById(UUID id);

	/** 여러 계정을 한 번에. 없는 UUID는 결과에 없다. */
	List<UserAccount> findAllByIds(Collection<UUID> ids);

	/** 이메일은 소문자로 정규화해서 넘긴다. */
	Optional<UserAccount> findByEmail(String email);

	boolean existsByEmail(String email);

}
