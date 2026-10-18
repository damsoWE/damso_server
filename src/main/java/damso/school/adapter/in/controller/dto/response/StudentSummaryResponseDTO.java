package damso.school.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.school.application.port.in.ClassroomAdminUseCase.StudentView;
import damso.school.application.port.in.TeacherClassroomQuery.StudentLinkView;

/** 학생 요약 응답(관리자 명단·교사 학급 현황 공통). 보호자는 연결 여부만 알려 주고 이메일·이름은 담지 않는다. */
public record StudentSummaryResponseDTO(UUID studentId, String name, boolean guardianLinked) {

	public static StudentSummaryResponseDTO from(StudentView view) {
		return new StudentSummaryResponseDTO(view.studentId(), view.name(), view.guardianLinked());
	}

	public static StudentSummaryResponseDTO from(StudentLinkView view) {
		return new StudentSummaryResponseDTO(view.studentId(), view.name(), view.guardianLinked());
	}

}
