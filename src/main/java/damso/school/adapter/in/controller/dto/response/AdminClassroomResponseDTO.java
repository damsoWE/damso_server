package damso.school.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.school.application.port.in.ClassroomAdminUseCase.ClassroomView;

/** 관리자 학급 응답. 담임의 이름·이메일은 담지 않고 연결 여부만 알려 준다. */
public record AdminClassroomResponseDTO(UUID classroomId, int grade, int classNumber, boolean homeroomAssigned,
		long studentCount) {

	public static AdminClassroomResponseDTO from(ClassroomView view) {
		return new AdminClassroomResponseDTO(view.classroomId(), view.grade(), view.classNumber(),
				view.homeroomAssigned(), view.studentCount());
	}

}
