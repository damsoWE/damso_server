package damso.school.application.port.in;

import java.util.UUID;

/**
 * 회원 탈퇴 때 계정의 학급·학생 연결을 풀고 이력을 남긴다 (FR-038·038a). account의 탈퇴가 이 포트로만 school을 쓴다.
 * 교사면 담임 연결과 공개 프로필을, 학부모면 자녀마다 보호자 연결을 해제한다. 해제된 학생·학급에는
 * 슈퍼 관리자가 코드를 다시 발행할 수 있다. 이력에는 계정 UUID·대상 UUID·시각만 남는다(개인정보 없음).
 */
public interface MembershipReleaseUseCase {

	void releaseAll(UUID accountId);

}
