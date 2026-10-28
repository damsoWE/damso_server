package damso.attempt.domain;

import java.time.Duration;
import java.util.List;

/**
 * 반복 실패 제한 규칙 (순수 규칙, 저장소를 모른다).
 * - LOGIN: 연속 실패 3회 30초 · 4회 1분 · 5회 2분 · 6회 5분 · 7회 이상 10분(상한). 대기 중 시도는 횟수에 더하지 않는다 (FR-024)
 * - 코드 입력(INVITE_CODE·SIGNUP_CODE·RESET_CODE): 5회 실패 시 10분 잠금, 풀리면 0회부터 (FR-011·030·032)
 * - MAIL_SEND: 60초 안 재발송 거부, 1시간 안 5회 초과 거부 (research R5)
 * - INQUIRY_SUBMIT: 학부모당 일반 문의 60초에 1건 (F2 research R6). 최소 간격은 설정 `damso.inquiry.submit-interval`
 * - 비밀번호 재설정 메일(이슈 #22): 이메일 하나만으로 세면 남이 내 이메일로 요청해 내 재설정을 막을 수 있으므로
 *   이메일 + 요청 위치(IP) 쌍으로 60초 1회·1시간 5회, 메일 폭탄·대량 요청을 막는 상한으로 이메일 하나 1시간 10통,
 *   요청 위치 하나 1시간 20회. 가입 인증 메일(MAIL_SEND)과는 따로 센다
 */
public final class AttemptPolicy {

	public static final Duration LOCK_DURATION = Duration.ofMinutes(10);

	public static final Duration MAIL_MIN_GAP = Duration.ofSeconds(60);

	public static final Duration MAIL_WINDOW = Duration.ofHours(1);

	public static final int MAIL_MAX_PER_WINDOW = 5;

	/** 재설정 메일: 이메일 하나(모든 요청 위치 합산)가 MAIL_WINDOW 안에 받을 수 있는 최대 통수. */
	public static final int RESET_MAIL_MAX_PER_EMAIL = 10;

	/** 재설정 메일: 요청 위치 하나(모든 이메일 합산)가 MAIL_WINDOW 안에 요청할 수 있는 최대 횟수. */
	public static final int RESET_MAIL_MAX_PER_CLIENT = 20;

	/** 문의 제출 윈도. 윈도 안 1건만 허용하므로 최소 간격과 같다. */
	public static final int INQUIRY_MAX_PER_WINDOW = 1;

	/** 실패 횟수 기록을 남겨 두는 최대 시간(정리용). 마지막 실패 후 이 시간이 지나면 키가 사라진다. */
	public static final Duration RECORD_TTL = Duration.ofHours(24);

	private static final AttemptRule LOGIN_RULE = new AttemptRule(List.of(0L, 0L, 30L, 60L, 120L, 300L, 600L), false);

	private static final AttemptRule CODE_RULE = new AttemptRule(
			List.of(0L, 0L, 0L, 0L, LOCK_DURATION.toSeconds()), true);

	private AttemptPolicy() {
	}

	public static AttemptRule ruleFor(AttemptKind kind) {
		return switch (kind) {
			case LOGIN -> LOGIN_RULE;
			case INVITE_CODE, SIGNUP_CODE, RESET_CODE -> CODE_RULE;
			case MAIL_SEND, INQUIRY_SUBMIT, RESET_MAIL_CLIENT, RESET_MAIL_PAIR, RESET_MAIL_EMAIL ->
				throw new IllegalArgumentException(kind + " has its own limit");
		};
	}

	/** n번째 연속 실패 직후의 대기 시간. */
	public static Duration waitAfterFailure(AttemptKind kind, int failedCount) {
		return Duration.ofSeconds(ruleFor(kind).waitSecondsFor(failedCount));
	}

}
