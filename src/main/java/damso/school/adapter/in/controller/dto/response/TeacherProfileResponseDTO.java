package damso.school.adapter.in.controller.dto.response;

import damso.school.application.port.in.TeacherProfileUseCase.ProfileView;

/** 교사 공개 프로필 응답(표시 이름 + 소개 문구). 이메일은 담지 않는다. */
public record TeacherProfileResponseDTO(String displayName, String introduction) {

	public static TeacherProfileResponseDTO from(ProfileView view) {
		return new TeacherProfileResponseDTO(view.displayName(), view.introduction());
	}

}
