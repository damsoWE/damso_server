package damso.school.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 교사 공개 프로필 수정: 표시 이름 1~20자, 소개 문구 0~60자. */
public record TeacherProfileRequestDTO(
		@NotBlank @Size(max = 20) String displayName,
		@NotNull @Size(max = 60) String introduction) {
}
