package damso.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;

/** PR #21 리뷰: 호출한 쪽이 정한 시각은 지금부터 5분 전까지만 받는다. 범위 밖이면 예외이고 기록하지 않는다. */
class AuditLogServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

	private final List<AuditLog> appended = new ArrayList<>();

	private final AuditLogService service = new AuditLogService(new AuditLogPort() {
		@Override
		public AuditLog append(AuditLog log) {
			appended.add(log);
			return log;
		}

		@Override
		public List<AuditLog> findByActor(UUID actorId) {
			return List.of();
		}

		@Override
		public List<AuditLog> findByTarget(AuditTargetType targetType, UUID targetId) {
			return List.of();
		}
	}, Clock.fixed(NOW, ZoneOffset.UTC));

	private void recordAt(Instant occurredAt) {
		service.record(UUID.randomUUID(), AuditAction.INQUIRY_CLOSED, AuditTargetType.INQUIRY, UUID.randomUUID(),
				occurredAt);
	}

	@Test
	void timesWithinTheAllowedRangeAreRecorded() {
		recordAt(NOW);
		recordAt(NOW.minus(AuditLogService.MAX_OCCURRED_AT_AGE));

		assertThat(appended).extracting(AuditLog::getOccurredAt)
			.containsExactly(NOW, NOW.minus(AuditLogService.MAX_OCCURRED_AT_AGE));
	}

	@Test
	void aTimeOlderThanTheAllowedRangeIsRejected() {
		Instant tooOld = NOW.minus(AuditLogService.MAX_OCCURRED_AT_AGE).minusNanos(1000);

		assertThatThrownBy(() -> recordAt(tooOld)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> recordAt(NOW.minus(Duration.ofDays(1)))).isInstanceOf(IllegalArgumentException.class);
		assertThat(appended).isEmpty();
	}

	@Test
	void aFutureTimeIsRejected() {
		assertThatThrownBy(() -> recordAt(NOW.plusNanos(1000))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> recordAt(NOW.plus(Duration.ofHours(1)))).isInstanceOf(IllegalArgumentException.class);
		assertThat(appended).isEmpty();
	}

}
