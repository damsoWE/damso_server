package damso.common.security;

import java.util.UUID;

/**
 * 접근 토큰의 세션(sid)이 지금도 살아 있는지 확인한다.
 * common은 도메인을 모르므로 인터페이스만 두고, 구현은 account 도메인 어댑터가 한다.
 * 로그아웃·비밀번호 재설정·탈퇴·재사용 탐지가 접근 토큰 만료(15분)를 기다리지 않고 바로 반영되게 한다 (research R4).
 */
public interface SessionStatusChecker {

	boolean isActive(UUID sessionId);

}
