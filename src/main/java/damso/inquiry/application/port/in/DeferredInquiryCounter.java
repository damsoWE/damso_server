package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** notification이 묶음 알림에 쓸 "응답 가능 시간 밖에 접수돼 아직 알리지 않은 일반 문의" 수를 inquiry에 묻는 통로. */
public interface DeferredInquiryCounter {

	/**
	 * 그 교사 앞으로 알림이 미뤄졌고(notification_deferred) 아직 묶음에 포함되지 않은 문의의 수를 세고,
	 * 각 문의에 포함 시각(now)을 표시해 돌려준다. 한 트랜잭션이라 같은 문의를 두 번 세지 않는다.
	 */
	int claimDeferred(UUID teacherId, Instant now);

	/** 표시하지 않고 수만 센다. */
	int countDeferred(UUID teacherId);

}
