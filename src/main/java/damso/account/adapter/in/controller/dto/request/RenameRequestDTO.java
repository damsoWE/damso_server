package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameRequestDTO(@NotBlank @Size(max = 20) String name) {
}
