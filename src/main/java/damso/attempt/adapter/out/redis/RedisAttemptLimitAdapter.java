package damso.attempt.adapter.out.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import damso.attempt.application.port.out.AttemptLimitPort;
import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptPolicy;
import damso.attempt.domain.AttemptRule;
import damso.attempt.domain.AttemptState;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

/**
 * 실패 횟수·대기·발송·제출 제한을 Redis TTL 키로 둔다 (research R7).
 * 증가와 대기 시각 계산은 Lua 스크립트 한 번으로 원자 처리한다.
 * 대기 판정은 앱 Clock 기준 시각(값)을 비교하고, Redis TTL은 오래된 키 정리에만 쓴다.
 * Redis에 닿을 수 없으면 요청을 거부한다(fail-closed) — 제한 없이 통과시키면 무차별 대입 방어가 꺼진다.
 */
@Component
public class RedisAttemptLimitAdapter implements AttemptLimitPort {

	private static final String KEY_PREFIX = "damso:attempt:";

	private final StringRedisTemplate redis;

	@SuppressWarnings("rawtypes")
	private final DefaultRedisScript<List> recordFailureScript = script("redis/attempt_record_failure.lua", List.class);

	private final DefaultRedisScript<Long> rateLimitScript = script("redis/rate_limit.lua", Long.class);

	private final DefaultRedisScript<Long> rateLimitAllScript = script("redis/rate_limit_all.lua", Long.class);

	public RedisAttemptLimitAdapter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public AttemptState get(AttemptKind kind, String subjectHash) {
		try {
			List<Object> values = redis.opsForHash().multiGet(key(kind, subjectHash), List.of("count", "until"));
			return toState(values.get(0), values.get(1));
		}
		catch (DataAccessException ex) {
			throw unavailable(ex);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public AttemptState recordFailure(AttemptKind kind, String subjectHash, Instant now, AttemptRule rule) {
		List<String> args = new ArrayList<>();
		args.add(String.valueOf(now.toEpochMilli()));
		args.add(rule.restartAfterLock() ? "1" : "0");
		args.add(String.valueOf(AttemptPolicy.RECORD_TTL.toSeconds()));
		rule.waitSecondsByCount().forEach(seconds -> args.add(String.valueOf(seconds)));
		try {
			List<Long> result = redis.execute(recordFailureScript, List.of(key(kind, subjectHash)), args.toArray());
			return toState(result.get(0), result.get(1));
		}
		catch (DataAccessException ex) {
			throw unavailable(ex);
		}
	}

	@Override
	public void reset(AttemptKind kind, String subjectHash) {
		try {
			redis.delete(key(kind, subjectHash));
		}
		catch (DataAccessException ex) {
			throw unavailable(ex);
		}
	}

	@Override
	public long reserveRate(AttemptKind kind, String subjectHash, Instant now, Duration minGap, Duration window,
			int max) {
		try {
			Long wait = redis.execute(rateLimitScript, List.of(key(kind, subjectHash)),
					String.valueOf(now.toEpochMilli()),
					String.valueOf(minGap.toMillis()),
					String.valueOf(window.toMillis()),
					String.valueOf(max),
					UUID.randomUUID().toString());
			return wait == null ? 0 : wait;
		}
		catch (DataAccessException ex) {
			throw unavailable(ex);
		}
	}

	@Override
	public long reserveRates(Instant now, List<RateLimit> limits) {
		List<String> keys = new ArrayList<>();
		List<String> args = new ArrayList<>();
		args.add(String.valueOf(now.toEpochMilli()));
		args.add(UUID.randomUUID().toString());
		for (RateLimit limit : limits) {
			keys.add(key(limit.kind(), limit.subjectHash()));
			args.add(String.valueOf(limit.minGap().toMillis()));
			args.add(String.valueOf(limit.window().toMillis()));
			args.add(String.valueOf(limit.max()));
		}
		try {
			Long wait = redis.execute(rateLimitAllScript, keys, args.toArray());
			return wait == null ? 0 : wait;
		}
		catch (DataAccessException ex) {
			throw unavailable(ex);
		}
	}

	private static AttemptState toState(Object count, Object until) {
		int failed = count == null ? 0 : Integer.parseInt(count.toString());
		long untilMs = until == null ? 0 : Long.parseLong(until.toString());
		return new AttemptState(failed, untilMs > 0 ? Instant.ofEpochMilli(untilMs) : null);
	}

	private static String key(AttemptKind kind, String subjectHash) {
		return KEY_PREFIX + kind.name() + ":" + subjectHash;
	}

	/** 원인은 서버 로그로만 남기고(전역 처리기), 응답은 INTERNAL_ERROR 고정 문구다. */
	private static BusinessException unavailable(DataAccessException cause) {
		BusinessException exception = new BusinessException(ErrorCode.INTERNAL_ERROR);
		exception.initCause(cause);
		return exception;
	}

	private static <T> DefaultRedisScript<T> script(String path, Class<T> resultType) {
		DefaultRedisScript<T> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource(path));
		script.setResultType(resultType);
		return script;
	}

}
