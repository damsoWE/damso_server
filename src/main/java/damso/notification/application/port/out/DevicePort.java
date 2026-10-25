package damso.notification.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.notification.domain.DeviceRegistration;

public interface DevicePort {

	/** 저장하고 바로 반영(flush)한다 — 토큰 유니크 충돌을 호출한 곳에서 잡기 위해서다. */
	DeviceRegistration save(DeviceRegistration device);

	Optional<DeviceRegistration> findByToken(String token);

	List<DeviceRegistration> findActiveByUsers(Collection<UUID> userIds);

	/** 그 세션으로 등록된 기기. */
	List<DeviceRegistration> findBySession(UUID userId, UUID sessionId);

}
