package damso.school.domain;

/** ISSUED → USED(가입·자녀 추가 확정) 또는 ISSUED → REVOKED(같은 대상 재발행). 만료는 expires_at으로 판정한다. */
public enum InviteStatus {

	ISSUED,
	USED,
	REVOKED

}
