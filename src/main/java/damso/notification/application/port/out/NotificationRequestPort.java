package damso.notification.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.notification.domain.NotificationRequest;

public interface NotificationRequestPort {

	NotificationRequest save(NotificationRequest request);

	Optional<NotificationRequest> findById(UUID id);

	/**
	 * 발송할 때가 된 요청을 짧은 트랜잭션에서 잡아 SENDING으로 표시하고 커밋한 뒤 돌려준다.
	 * 대상은 (PENDING ∧ scheduled_at ≤ now) ∨ (SENDING ∧ locked_until ≤ now)이고, FOR UPDATE SKIP LOCKED로
	 * 다른 발송기가 잡은 행은 건너뛰므로 같은 요청을 두 곳이 동시에 가져가지 않는다.
	 * 표시 시 locked_until = now + lease. FCM 호출은 이 메서드 밖(트랜잭션 밖)에서 한다.
	 */
	List<NotificationRequest> claimDue(Instant now, Duration lease, int limit);

	/** 즉시 발송용. claimDue와 같은 조건으로 특정 요청 하나만 잡는다. 이미 처리됐거나 다른 곳이 잡았으면 비어 있다. */
	Optional<NotificationRequest> claimOne(UUID requestId, Instant now, Duration lease);

	/** 교사 앞으로 대기 중인(PENDING) 묶음 알림. */
	Optional<NotificationRequest> findPendingDigest(UUID teacherId);

	/**
	 * 교사 앞으로 열려 있는(대기·전송 중) 묶음 알림이 없을 때만 scheduledAt에 만든다. 이미 있으면 아무것도 하지 않는다.
	 * 예외를 던지지 않는 INSERT … ON CONFLICT DO NOTHING이라, 동시에 두 문의가 들어와도 문의 접수 트랜잭션이 깨지지 않는다.
	 */
	void createDigestIfAbsent(UUID teacherId, Instant scheduledAt, Instant now);

}
