package damso.school.adapter.in.controller.dto.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 학생 이름 일괄 등록: 한 번에 최대 40명, 이름은 각 1~20자. */
public record RegisterStudentsRequestDTO(
		@NotEmpty @Size(max = 40) List<@NotBlank @Size(max = 20) String> names) {

	/** 받은 목록을 복사해 둔다(불변). 검증 전에 만들어지므로 null·null 원소는 그대로 두고 검증(@NotNull 등)이 거른다. */
	public RegisterStudentsRequestDTO {
		names = names == null ? null : Collections.unmodifiableList(new ArrayList<>(names));
	}

}
