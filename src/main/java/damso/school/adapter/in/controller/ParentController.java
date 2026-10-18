package damso.school.adapter.in.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.school.adapter.in.controller.dto.response.ChildResponseDTO;
import damso.school.adapter.in.controller.dto.request.LinkChildRequestDTO;
import damso.school.application.port.in.ParentChildrenUseCase;

/**
 * 학부모 전용 API (경로 단위 역할 검사는 SecurityConfig). 사용자 ID는 토큰에서만 꺼내고,
 * 본인이 보호자인 학생만 보인다. 담임 프로필에는 이메일이 없다.
 */
@RestController
@RequestMapping("/api/v1/parent")
class ParentController {

	private final ParentChildrenUseCase children;

	ParentController(ParentChildrenUseCase children) {
		this.children = children;
	}

	@GetMapping("/children")
	List<ChildResponseDTO> list(CurrentUser parent) {
		return children.list(parent.userId()).stream().map(ChildResponseDTO::from).toList();
	}

	@PostMapping("/children")
	@ResponseStatus(HttpStatus.CREATED)
	ChildResponseDTO link(CurrentUser parent, @Valid @RequestBody LinkChildRequestDTO request) {
		return ChildResponseDTO.from(children.link(parent.userId(), request.inviteCode()));
	}

}
