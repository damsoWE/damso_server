package damso.school.adapter.in.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.school.adapter.in.controller.dto.response.AdminClassroomResponseDTO;
import damso.school.adapter.in.controller.dto.request.CreateClassroomRequestDTO;
import damso.school.adapter.in.controller.dto.response.InviteCodeStatusResponseDTO;
import damso.school.adapter.in.controller.dto.response.IssuedInviteCodeResponseDTO;
import damso.school.adapter.in.controller.dto.request.RegisterStudentsRequestDTO;
import damso.school.adapter.in.controller.dto.response.StudentSummaryResponseDTO;
import damso.school.application.port.in.ClassroomAdminUseCase;
import damso.school.application.port.in.InviteIssueUseCase;

/**
 * 슈퍼 관리자 전용 API (경로 단위 역할 검사는 SecurityConfig). 민원 내용에 접근하는 경로는 없다.
 * 코드 원문은 발행 응답에만 있고 현황 응답에는 없다. 담임 이름·이메일은 어떤 응답에도 없다.
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

	private final ClassroomAdminUseCase classroomAdmin;

	private final InviteIssueUseCase inviteIssue;

	AdminController(ClassroomAdminUseCase classroomAdmin, InviteIssueUseCase inviteIssue) {
		this.classroomAdmin = classroomAdmin;
		this.inviteIssue = inviteIssue;
	}

	@GetMapping("/classrooms")
	List<AdminClassroomResponseDTO> listClassrooms() {
		return classroomAdmin.listClassrooms().stream().map(AdminClassroomResponseDTO::from).toList();
	}

	@PostMapping("/classrooms")
	@ResponseStatus(HttpStatus.CREATED)
	AdminClassroomResponseDTO createClassroom(@Valid @RequestBody CreateClassroomRequestDTO request) {
		return AdminClassroomResponseDTO.from(classroomAdmin.createClassroom(request.grade(), request.classNumber()));
	}

	@GetMapping("/classrooms/{classroomId}/students")
	List<StudentSummaryResponseDTO> listStudents(@PathVariable UUID classroomId) {
		return classroomAdmin.listStudents(classroomId).stream().map(StudentSummaryResponseDTO::from).toList();
	}

	@PostMapping("/classrooms/{classroomId}/students")
	@ResponseStatus(HttpStatus.CREATED)
	List<StudentSummaryResponseDTO> registerStudents(@PathVariable UUID classroomId,
			@Valid @RequestBody RegisterStudentsRequestDTO request) {
		return classroomAdmin.registerStudents(classroomId, request.names()).stream()
				.map(StudentSummaryResponseDTO::from).toList();
	}

	@PostMapping("/classrooms/{classroomId}/teacher-invite")
	@ResponseStatus(HttpStatus.CREATED)
	IssuedInviteCodeResponseDTO issueTeacherInvite(@PathVariable UUID classroomId, CurrentUser admin) {
		return IssuedInviteCodeResponseDTO.from(inviteIssue.issueTeacherInvite(classroomId, admin.userId()));
	}

	@PostMapping("/students/{studentId}/parent-invite")
	@ResponseStatus(HttpStatus.CREATED)
	IssuedInviteCodeResponseDTO issueParentInvite(@PathVariable UUID studentId, CurrentUser admin) {
		return IssuedInviteCodeResponseDTO.from(inviteIssue.issueParentInvite(studentId, admin.userId()));
	}

	@GetMapping("/invite-codes")
	List<InviteCodeStatusResponseDTO> listInviteCodes(@RequestParam UUID classroomId) {
		return inviteIssue.listInvites(classroomId).stream().map(InviteCodeStatusResponseDTO::from).toList();
	}

}
