package damso.attempt.application.port.out;

import java.time.Instant;

import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptRule;
import damso.attempt.domain.AttemptState;

/** 반복 실패 상태 저장소. 구현은 Redis (research R7). 저장소 장애는 BusinessException(INTERNAL_ERROR)로 알린다(fail-closed). */
public interface AttemptLimitPort {

	AttemptState get(AttemptKind kind, String subjectHash);

	/** 대기·잠금 중이면 횟수를 올리지 않고 현재 상태를 돌려준다. 아니면 횟수를 올리고 규칙에 따른 대기 시각을 정한다. */
	AttemptState recordFailure(AttemptKind kind, String subjectHash, Instant now, AttemptRule rule);

	void reset(AttemptKind kind, String subjectHash);

	/** 메일 발송 한 건을 예약한다. 허용되면 0, 거부되면 기다려야 하는 밀리초. */
	long reserveMailSend(String subjectHash, Instant now);

}
