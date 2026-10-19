package damso.attempt.application.port.in;

import java.util.UUID;

import damso.attempt.domain.AttemptKind;

/**
 * 반복 실패 제한. subject는 소문자 이메일 또는 계정 UUID 문자열이며, 저장소에는 해시만 넘어간다.
 */
public interface AttemptLimitUseCase {

	/** 대기·잠금 중이면 TooManyAttemptsException(Retry-After 초). */
	void ensureAllowed(AttemptKind kind, String subject);

	/** 실패를 기록한다. 이미 대기·잠금 중이면 횟수를 올리지 않는다. */
	void recordFailure(AttemptKind kind, String subject);

	void reset(AttemptKind kind, String subject);

	/** 메일 발송 한 건을 예약한다. 허용되면 0, 거부되면 기다려야 하는 초(올림). 예외를 던지지 않는다. */
	long reserveMailSend(String subject);

	/** reserveMailSend가 거부되면 TooManyAttemptsException. */
	void ensureMailSendAllowed(String subject);

	/**
	 * 일반 문의 제출 한 건을 예약한다(학부모 UUID 기준 60초 1건). 허용되면 0, 거부되면 기다려야 하는 초(올림).
	 * 거부는 예외가 아니라 반환값이다. 저장소 장애는 BusinessException(INTERNAL_ERROR)로 전파되며 호출자가 정책을 정한다.
	 */
	long reserveInquirySubmit(UUID parentId);

}
