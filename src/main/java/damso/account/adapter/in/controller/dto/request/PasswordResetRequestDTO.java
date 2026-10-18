package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 재설정 코드 요청. 가입 여부와 관계없이 같은 응답을 준다. */
public record PasswordResetRequestDTO(@NotBlank @Email @Size(max = 254) String email) {
}
