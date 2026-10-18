package damso.school.adapter.in.controller.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 학생 이름 일괄 등록: 한 번에 최대 40명, 이름은 각 1~20자. */
public record RegisterStudentsRequestDTO(
		@NotEmpty @Size(max = 40) List<@NotBlank @Size(max = 20) String> names) {
}
