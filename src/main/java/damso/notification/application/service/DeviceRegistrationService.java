package damso.notification.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.notification.application.port.in.DeviceRegistrationUseCase;
import damso.notification.application.port.out.DevicePort;
import damso.notification.domain.DeviceRegistration;
import damso.notification.domain.DeviceRegistration.Platform;

@Service
@Transactional
public class DeviceRegistrationService implements DeviceRegistrationUseCase {

	private final DevicePort devices;

	public DeviceRegistrationService(DevicePort devices) {
		this.devices = devices;
	}

	@Override
	public void register(UUID userId, UUID sessionId, String token, Platform platform) {
		DeviceRegistration device = devices.findByToken(token)
				.orElseGet(() -> new DeviceRegistration(userId, sessionId, token, platform));
		device.reassign(userId, sessionId, platform);
		devices.save(device);
	}

	@Override
	public void unregisterCurrent(UUID userId, UUID sessionId) {
		for (DeviceRegistration device : devices.findBySession(userId, sessionId)) {
			device.deactivate();
			devices.save(device);
		}
	}

}
