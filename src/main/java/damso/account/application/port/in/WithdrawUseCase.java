package damso.account.application.port.in;

import java.util.UUID;

/**
 * 회원 탈퇴 (FR-038, research R11). 현재 비밀번호를 다시 확인한다. 한 트랜잭션에서 담임·보호자 연결 해제와 이력,
 * 모든 세션 폐기, 이메일·이름 비식별화를 처리한다. 슈퍼 관리자는 앱에서 탈퇴할 수 없다(FORBIDDEN).
 * 비밀번호가 틀리면 INVALID_CREDENTIALS이고, 로그인과 같은 실패 횟수 규칙을 따른다.
 */
public interface WithdrawUseCase {

	void withdraw(UUID userId, String password);

}
