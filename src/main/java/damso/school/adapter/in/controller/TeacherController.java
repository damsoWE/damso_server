package damso.school.adapter.in.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.school.adapter.in.controller.dto.response.TeacherClassroomResponseDTO;
import damso.school.adapter.in.controller.dto.request.TeacherProfileRequestDTO;
import damso.school.adapter.in.controller.dto.response.TeacherProfileResponseDTO;
import damso.school.application.port.in.TeacherClassroomQuery;
import damso.school.application.port.in.TeacherProfileUseCase;

/**
 * 교사 전용 API (경로 단위 역할 검사는 SecurityConfig). 대상은 토큰의 사용자 UUID로만 정해지므로
 * 다른 교사의 프로필·다른 학급의 현황을 가리킬 방법이 없다. 학부모의 이메일·이름은 응답에 없다.
 */
@RestController
@RequestMapping("/api/v1/teacher")
class TeacherController {

	private final TeacherProfileUseCase profiles;

	private final TeacherClassroomQuery classroomQuery;

	TeacherController(TeacherProfileUseCase profiles, TeacherClassroomQuery classroomQuery) {
		this.profiles = profiles;
		this.classroomQuery = classroomQuery;
	}

	@GetMapping("/profile")
	TeacherProfileResponseDTO getProfile(CurrentUser teacher) {
		return TeacherProfileResponseDTO.from(profiles.get(teacher.userId()));
	}

	@PutMapping("/profile")
	TeacherProfileResponseDTO updateProfile(CurrentUser teacher, @Valid @RequestBody TeacherProfileRequestDTO request) {
		return TeacherProfileResponseDTO.from(
				profiles.update(teacher.userId(), request.displayName(), request.introduction()));
	}

	@GetMapping("/classroom")
	TeacherClassroomResponseDTO getClassroom(CurrentUser teacher) {
		return TeacherClassroomResponseDTO.from(classroomQuery.myClassroom(teacher.userId()));
	}

}
