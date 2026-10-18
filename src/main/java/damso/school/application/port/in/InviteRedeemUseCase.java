package damso.school.application.port.in;

import java.util.UUID;

import damso.school.domain.InviteKind;

/**
 * 초대 코드로 담임·보호자 연결을 확정한다. 가입(account)이 이 포트로만 school을 쓴다.
 * 없는·만료된·사용된·무효인·역할이 다른·대상이 이미 찬 코드는 모두 같은 INVALID_INVITE_CODE다 (FR-010).
 */
public interface InviteRedeemUseCase {

	/** 코드가 지금 쓸 수 있는지 확인한다(소모하지 않는다). 쓸 수 없으면 INVALID_INVITE_CODE. */
	InviteTarget validate(String rawCode);

	/** 가입 확정 시점에 같은 코드가 아직 쓸 수 있는지 다시 확인한다(재발행·만료·동시 사용 대비). */
	InviteTarget resolve(UUID inviteCodeId);

	/**
	 * 코드를 소모하고 연결을 확정한다. 교사 코드는 담임·공개 프로필, 학부모 코드는 보호자 연결을 만들고
	 * 연결 이력(LINKED)을 남긴다. 같은 코드를 동시에 쓰면 한 건만 성공하고 나머지는 INVALID_INVITE_CODE다.
	 *
	 * @param accountName 교사 공개 프로필의 처음 표시 이름
	 */
	void redeem(UUID inviteCodeId, UUID accountId, String accountName);

	/** 이미 가입한 학부모가 코드를 입력해 자녀를 더 연결한다. 학부모 코드가 아니면 INVALID_INVITE_CODE. */
	UUID redeemForExistingParent(String rawCode, UUID parentId);

	record InviteTarget(UUID inviteCodeId, InviteKind kind, UUID targetId) {
	}

}
