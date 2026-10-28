package damso.school.adapter.in.controller.dto.response;

import java.util.List;

import damso.school.application.port.in.TeacherClassroomQuery.TeacherClassroomView;

/** 교사의 담당 학급 응답. 학생별 학부모 연결 여부만 있고 학부모의 이메일·이름은 없다. */
public record TeacherClassroomResponseDTO(ClassroomRefResponseDTO classroom,
		List<StudentSummaryResponseDTO> students) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public TeacherClassroomResponseDTO {
		students = students == null ? null : List.copyOf(students);
	}

	public static TeacherClassroomResponseDTO from(TeacherClassroomView view) {
		return new TeacherClassroomResponseDTO(
				new ClassroomRefResponseDTO(view.classroom().classroomId(), view.classroom().grade(),
						view.classroom().classNumber()),
				view.students().stream().map(StudentSummaryResponseDTO::from).toList());
	}

}
