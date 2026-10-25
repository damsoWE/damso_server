package damso.gateway.domain;

import java.util.regex.Pattern;

/**
 * 가명화 토큰의 종류. 토큰 모양은 {@code [KIND_n]}이다(예: [STUDENT_1]).
 * 1차 사전: STUDENT·GUARDIAN·TEACHER·CLASS·SCHOOL / 2차 패턴: PHONE·ADDRESS·EMAIL·ID_NUMBER (research R2·R3).
 */
public enum TokenKind {

	STUDENT,
	GUARDIAN,
	TEACHER,
	CLASS,
	SCHOOL,
	PHONE,
	ADDRESS,
	EMAIL,
	ID_NUMBER;

	/** 토큰 모양 문자열. 종류 이름은 대문자·밑줄, 번호는 1부터. */
	public static final Pattern TOKEN = Pattern.compile("\\[([A-Z]+(?:_[A-Z]+)*)_(\\d+)]");

	public String token(int number) {
		return "[" + name() + "_" + number + "]";
	}

}
