package damso.account.application.port.out;

/**
 * 인증 메일 발송. 본문은 고정 템플릿이고 코드 6자리만 들어간다(이름·학생 정보 없음).
 * 구현은 트랜잭션이 커밋된 뒤 비동기로 보낸다 — 응답 시간에 메일 서버 지연이 드러나지 않는다.
 */
public interface VerificationMailPort {

	void sendSignupCode(String email, String code);

	/** 이미 가입된 이메일로 가입을 시도했을 때 그 주소로만 안내한다(응답으로는 가입 여부를 알 수 없다). */
	void sendAlreadyRegistered(String email);

	void sendPasswordResetCode(String email, String code);

}
