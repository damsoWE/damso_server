package damso.inquiry.adapter.in.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import damso.inquiry.adapter.in.controller.dto.response.InquiryStatusSummaryResponseDTO;
import damso.inquiry.application.port.in.InquiryStatusQuery;

/**
 * 슈퍼 관리자 문의 현황 (F5 US3). 역할 검사는 SecurityConfig의 /api/v1/admin/** = SUPER_ADMIN.
 * 학급별 건수만 돌려주고 문의 내용에 접근하는 경로는 여전히 없다(원칙 VII).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminInquiryController {

	private final InquiryStatusQuery status;

	AdminInquiryController(InquiryStatusQuery status) {
		this.status = status;
	}

	@GetMapping("/inquiry-status")
	InquiryStatusSummaryResponseDTO inquiryStatus() {
		return InquiryStatusSummaryResponseDTO.from(status.statusByClassroom());
	}

}
