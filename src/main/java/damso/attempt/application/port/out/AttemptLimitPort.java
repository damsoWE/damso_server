package damso.attempt.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptPolicy;
import damso.attempt.domain.AttemptRule;
import damso.attempt.domain.AttemptState;

/** 반복 실패 상태 저장소. 구현은 Redis. 저장소 장애는 BusinessException(INTERNAL_ERROR)로 알린다(fail-closed). */
public interface AttemptLimitPort {

	AttemptState get(AttemptKind kind, String subjectHash);

	/** 대기·잠금 중이면 횟수를 올리지 않고 현재 상태를 돌려준다. 아니면 횟수를 올리고 규칙에 따른 대기 시각을 정한다. */
	AttemptState recordFailure(AttemptKind kind, String subjectHash, Instant now, AttemptRule rule);

	void reset(AttemptKind kind, String subjectHash);

	/**
	 * 요청 한 건을 예약한다. 최소 간격(minGap)과 윈도(window) 안 최대 건수(max)를 모두 지켜야 허용된다.
	 * 허용되면 0, 거부되면 기다려야 하는 밀리초.
	 */
	long reserveRate(AttemptKind kind, String subjectHash, Instant now, Duration minGap, Duration window, int max);

	/**
	 * 요청 한 건을 여러 칸에 한꺼번에 예약한다. 모든 칸이 허용하면 모든 칸에 기록하고 0, 하나라도 거부하면 어느 칸에도
	 * 기록하지 않고 거부한 칸들의 대기 밀리초 중 가장 큰 값.
	 */
	long reserveRates(Instant now, List<RateLimit> limits);

	/** 메일 발송 한 건을 예약한다. 허용되면 0, 거부되면 기다려야 하는 밀리초. */
	default long reserveMailSend(String subjectHash, Instant now) {
		return reserveRate(AttemptKind.MAIL_SEND, subjectHash, now, AttemptPolicy.MAIL_MIN_GAP,
				AttemptPolicy.MAIL_WINDOW, AttemptPolicy.MAIL_MAX_PER_WINDOW);
	}

	/** reserveRates의 칸 하나. 최소 간격(minGap)과 윈도(window) 안 최대 건수(max)를 모두 지켜야 허용된다. */
	record RateLimit(AttemptKind kind, String subjectHash, Duration minGap, Duration window, int max) {
	}

}
