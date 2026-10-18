package damso.school.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateClassroomRequestDTO(
		@NotNull @Min(1) @Max(6) Integer grade,
		@NotNull @Min(1) @Max(20) Integer classNumber) {
}
