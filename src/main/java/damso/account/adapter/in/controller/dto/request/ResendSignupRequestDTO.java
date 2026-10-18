package damso.account.adapter.in.controller.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** 가입 인증 코드 재발송 요청. */
public record ResendSignupRequestDTO(@NotNull UUID pendingSignupId) {
}
