package damso.school.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 학부모가 자녀를 더 연결하기 위해 초대 코드를 입력하는 요청. */
public record LinkChildRequestDTO(
		@NotBlank @Pattern(regexp = "^[A-HJKMNP-Za-hjkmnp-z2-9]{10}$") String inviteCode) {

	@Override
	public String toString() {
		return "LinkChildRequestDTO[]";
	}

}
