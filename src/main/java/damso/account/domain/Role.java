package damso.account.domain;

/** 사용자 역할. 초대 코드가 정하고, 가입 후 바꿀 수 없다. 토큰의 role 클레임 값과 같은 이름이다. */
public enum Role {

	SUPER_ADMIN,
	TEACHER,
	PARENT

}
