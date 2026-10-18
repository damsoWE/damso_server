package damso.attempt.domain;

import java.util.List;

/**
 * 연속 실패 횟수별 대기 시간(초). 인덱스 0이 1회째 실패 직후의 대기다. 횟수가 목록보다 많으면 마지막 값을 쓴다.
 *
 * @param waitSecondsByCount 실패 횟수별 대기 시간(초). 0이면 대기 없음
 * @param restartAfterLock true면 대기·잠금이 풀린 뒤 횟수를 0부터 다시 센다(코드 입력), false면 횟수를 유지한다(로그인)
 */
public record AttemptRule(List<Long> waitSecondsByCount, boolean restartAfterLock) {

	public AttemptRule {
		waitSecondsByCount = List.copyOf(waitSecondsByCount);
	}

	public long waitSecondsFor(int failedCount) {
		if (failedCount < 1) {
			return 0;
		}
		int index = Math.min(failedCount, waitSecondsByCount.size()) - 1;
		return waitSecondsByCount.get(index);
	}

}
