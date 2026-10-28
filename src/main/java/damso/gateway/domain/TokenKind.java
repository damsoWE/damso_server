package damso.gateway.domain;

import java.util.regex.Pattern;

/**
 * 가명화 토큰의 종류. 토큰 모양은 {@code [KIND_n]}이다(예: [STUDENT_1]).
 * 1차 사전: STUDENT·GUARDIAN·TEACHER·CLASS·SCHOOL / 2차 패턴: PHONE·ADDRESS·EMAIL·ID_NUMBER.
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

	/**
	 * 연락처·식별번호(전화·이메일·주소·주민번호)인지. 이 종류는 정리 결과로 복원하지 않고 고정 문구로 남긴다 —
	 * 정리 결과가 연락처를 새로 만들어 저장하지 않게.
	 */
	public boolean isContactOrIdentifier() {
		return this == PHONE || this == EMAIL || this == ADDRESS || this == ID_NUMBER;
	}

	/** 복원하지 않는 종류 대신 남기는 고정 문구. */
	public String withheldLabel() {
		return this == ID_NUMBER ? "(주민등록번호 생략)" : "(연락처 생략)";
	}

}
