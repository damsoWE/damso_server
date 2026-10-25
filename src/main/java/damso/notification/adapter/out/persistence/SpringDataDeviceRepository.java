package damso.notification.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import damso.notification.domain.DeviceRegistration;

interface SpringDataDeviceRepository extends JpaRepository<DeviceRegistration, UUID> {

	Optional<DeviceRegistration> findByToken(String token);

	List<DeviceRegistration> findByUserIdInAndActiveTrue(Collection<UUID> userIds);

	List<DeviceRegistration> findByUserIdAndSessionId(UUID userId, UUID sessionId);

}
