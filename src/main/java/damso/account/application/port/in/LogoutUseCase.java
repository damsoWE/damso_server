package damso.account.application.port.in;

import java.util.UUID;

/** 로그아웃: 이 기기의 세션만 폐기한다. 다른 기기의 세션은 유지된다. */
public interface LogoutUseCase {

	void logout(UUID sessionId);

}
