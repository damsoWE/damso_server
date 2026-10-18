package damso.notification.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import damso.notification.application.port.out.DevicePort;
import damso.notification.application.port.out.NotificationRequestPort;
import damso.notification.domain.DeviceRegistration;
import damso.notification.domain.NotificationKind;
import damso.notification.domain.NotificationRequest;
import damso.notification.domain.NotificationStatus;

/** notification 도메인의 출력 포트를 Spring Data JPA로 구현한다. */
@Component
class NotificationPersistenceAdapter implements NotificationRequestPort, DevicePort {

	private final SpringDataNotificationRequestRepository requests;

	private final SpringDataDeviceRepository devices;

	NotificationPersistenceAdapter(SpringDataNotificationRequestRepository requests,
			SpringDataDeviceRepository devices) {
		this.requests = requests;
		this.devices = devices;
	}

	// NotificationRequestPort

	@Override
	public NotificationRequest save(NotificationRequest request) {
		return requests.saveAndFlush(request);
	}

	@Override
	public Optional<NotificationRequest> findById(UUID id) {
		return requests.findById(id);
	}

	/** 잡기와 SENDING 표시를 한 짧은 트랜잭션에서 하고 커밋한다. 호출한 쪽의 트랜잭션에 묶이지 않는다. */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<NotificationRequest> claimDue(Instant now, Duration lease, int limit) {
		List<NotificationRequest> due = requests.lockDue(now, limit);
		Instant lockedUntil = now.plus(lease);
		due.forEach(request -> request.claim(lockedUntil));
		requests.flush();
		return due;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NotificationRequest> claimOne(UUID requestId, Instant now, Duration lease) {
		Optional<NotificationRequest> due = requests.lockOneDue(requestId, now);
		due.ifPresent(request -> request.claim(now.plus(lease)));
		requests.flush();
		return due;
	}

	@Override
	public Optional<NotificationRequest> findPendingDigest(UUID teacherId) {
		return requests.findFirstByRecipientIdAndKindAndStatus(teacherId, NotificationKind.GENERAL_DIGEST,
				NotificationStatus.PENDING);
	}

	@Override
	public void createDigestIfAbsent(UUID teacherId, Instant scheduledAt, Instant now) {
		requests.insertDigestIfAbsent(UUID.randomUUID(), teacherId, "DIGEST:" + teacherId, scheduledAt, now);
	}

	// DevicePort

	@Override
	public DeviceRegistration save(DeviceRegistration device) {
		return devices.saveAndFlush(device);
	}

	@Override
	public Optional<DeviceRegistration> findByToken(String token) {
		return devices.findByToken(token);
	}

	@Override
	public List<DeviceRegistration> findActiveByUsers(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return List.of();
		}
		return devices.findByUserIdInAndActiveTrue(userIds);
	}

	@Override
	public List<DeviceRegistration> findBySession(UUID userId, UUID sessionId) {
		return devices.findByUserIdAndSessionId(userId, sessionId);
	}

}
