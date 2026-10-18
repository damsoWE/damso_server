package damso.account.adapter.in.controller.dto.response;

import java.util.UUID;

public record PendingSignupResponseDTO(UUID pendingSignupId, long codeExpiresInSeconds) {
}
