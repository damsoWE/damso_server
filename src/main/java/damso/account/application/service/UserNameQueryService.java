package damso.account.application.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.UserNameQuery;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.UserAccount;

@Service
@Transactional(readOnly = true)
public class UserNameQueryService implements UserNameQuery {

	private final UserAccountPort accounts;

	public UserNameQueryService(UserAccountPort accounts) {
		this.accounts = accounts;
	}

	@Override
	public Map<UUID, String> namesOf(Collection<UUID> userIds) {
		return accounts.findAllByIds(userIds).stream()
				.filter(UserAccount::isActive)
				.collect(Collectors.toMap(UserAccount::getId, account -> account.getName().reveal()));
	}

}
