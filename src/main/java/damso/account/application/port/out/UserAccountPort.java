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

	/**
	 * findByEmail과 같고, 찾은 계정 행을 트랜잭션이 끝날 때까지 잠근다(SELECT ... FOR UPDATE).
	 * 같은 계정에 대한 작업을 한 번에 하나로 묶어야 하는 경로에서만 쓴다(재설정 코드 발급).
	 */
	Optional<UserAccount> findByEmailForUpdate(String email);

	boolean existsByEmail(String email);

}
