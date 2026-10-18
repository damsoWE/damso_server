package damso.account.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.MyAccountUseCase;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.UserAccount;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.InvalidInputException;

@Service
@Transactional
public class MyAccountService implements MyAccountUseCase {

	private final UserAccountPort accounts;

	public MyAccountService(UserAccountPort accounts) {
		this.accounts = accounts;
	}

	@Override
	@Transactional(readOnly = true)
	public MeView me(UUID userId) {
		return toView(requireActive(userId));
	}

	@Override
	public MeView rename(UUID userId, String name) {
		UserAccount account = requireActive(userId);
		try {
			account.rename(name);
		}
		catch (IllegalArgumentException outOfRange) {
			throw new InvalidInputException("name", "1~20자여야 합니다");
		}
		return toView(accounts.save(account));
	}

	private UserAccount requireActive(UUID userId) {
		return accounts.findById(userId).filter(UserAccount::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private static MeView toView(UserAccount account) {
		return new MeView(account.getId(), account.getName().reveal(), account.getEmail(), account.getRole());
	}

}
