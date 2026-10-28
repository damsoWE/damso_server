package damso.account.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 계정 이름을 읽는 통로 (가명화 사전의 보호자·교사 이름). 이름은 이때만 복호화한 String이며 로그·예외에 넣지 않는다.
 * 탈퇴해 비식별된 계정과 없는 계정은 결과에 없다 — 남은 이름이 아니라 가릴 이름만 돌려준다.
 */
public interface UserNameQuery {

	Map<UUID, String> namesOf(Collection<UUID> userIds);

}
