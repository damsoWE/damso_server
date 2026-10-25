package damso.inquiry.adapter.in.controller.dto.response;

import java.util.List;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquiryPage;

public record TeacherInquiryPageResponseDTO(List<TeacherInquirySummaryResponseDTO> items, int page, int size,
		long totalElements) {

	public static TeacherInquiryPageResponseDTO from(InquiryPage page) {
		return new TeacherInquiryPageResponseDTO(
				page.items().stream().map(TeacherInquirySummaryResponseDTO::from).toList(), page.page(), page.size(),
				page.totalElements());
	}

}
