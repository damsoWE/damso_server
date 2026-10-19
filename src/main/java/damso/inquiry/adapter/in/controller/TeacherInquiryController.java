package damso.inquiry.adapter.in.controller;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.inquiry.adapter.in.controller.dto.response.InquiryContentResponseDTO;
import damso.inquiry.adapter.in.controller.dto.response.TeacherInquiryDetailResponseDTO;
import damso.inquiry.adapter.in.controller.dto.response.TeacherInquiryPageResponseDTO;
import damso.inquiry.application.port.in.TeacherInquiryQuery;
import damso.inquiry.application.port.in.ViewInquiryContentUseCase;

/**
 * 교사 문의 API (경로 단위 역할 검사는 SecurityConfig의 /api/v1/teacher/**). 사용자 ID는 토큰에서만 꺼내고,
 * 현재 담임인 학급의 문의만 보인다. 목록·상세에는 본문이 없고 원문은 /content로만 열람한다.
 */
@RestController
@RequestMapping("/api/v1/teacher/inquiries")
class TeacherInquiryController {

	private final TeacherInquiryQuery query;

	private final ViewInquiryContentUseCase viewContent;

	TeacherInquiryController(TeacherInquiryQuery query, ViewInquiryContentUseCase viewContent) {
		this.query = query;
		this.viewContent = viewContent;
	}

	@GetMapping
	TeacherInquiryPageResponseDTO list(CurrentUser teacher, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return TeacherInquiryPageResponseDTO.from(query.list(teacher.userId(), page, size));
	}

	@GetMapping("/{inquiryId}")
	TeacherInquiryDetailResponseDTO get(CurrentUser teacher, @PathVariable UUID inquiryId) {
		return TeacherInquiryDetailResponseDTO.from(query.get(teacher.userId(), inquiryId));
	}

	@GetMapping("/{inquiryId}/content")
	InquiryContentResponseDTO content(CurrentUser teacher, @PathVariable UUID inquiryId) {
		return InquiryContentResponseDTO.from(viewContent.view(teacher.userId(), inquiryId));
	}

}
