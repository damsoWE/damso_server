package damso.school.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.school.application.port.in.ParentChildrenUseCase.ChildView;

/** 학부모 홈의 자녀 응답. 담임이 아직 없으면 teacher는 null이다. */
public record ChildResponseDTO(UUID studentId, String studentName, ClassroomRefResponseDTO classroom,
		TeacherProfileResponseDTO teacher) {

	public static ChildResponseDTO from(ChildView view) {
		return new ChildResponseDTO(view.studentId(), view.studentName(),
				new ClassroomRefResponseDTO(view.classroom().classroomId(), view.classroom().grade(),
						view.classroom().classNumber()),
				view.teacher() == null ? null
						: new TeacherProfileResponseDTO(view.teacher().displayName(), view.teacher().introduction()));
	}

}
