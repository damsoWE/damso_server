package damso.account.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.account.domain.UserAccount;

interface SpringDataUserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByEmail(String email);

	/** SELECT ... FOR UPDATE. UserAccountPort.findByEmailForUpdate 전용 — 다른 곳에서 직접 쓰지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from UserAccount u where u.email = :email")
	Optional<UserAccount> findByEmailForUpdate(@Param("email") String email);

	boolean existsByEmail(String email);

}
