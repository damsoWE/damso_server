package damso.account.application.port.in;

import java.util.UUID;

import damso.account.domain.Role;

/** 마이페이지의 내 정보 (FR-037). 본인 이메일만 돌려주고 다른 사용자의 정보는 다루지 않는다. */
public interface MyAccountUseCase {

	MeView me(UUID userId);

	/** 이름은 공백을 뺀 뒤 1~20자. 아니면 InvalidInputException. */
	MeView rename(UUID userId, String name);

	record MeView(UUID userId, String name, String email, Role role) {

		@Override
		public String toString() {
			return "MeView[userId=" + userId + "]";
		}

	}

}
