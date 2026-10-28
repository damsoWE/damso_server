package damso.inquiry.adapter.in.controller.dto.response;

import java.util.List;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquiryPage;

public record TeacherInquiryPageResponseDTO(List<TeacherInquirySummaryResponseDTO> items, int page, int size,
		long totalElements) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public TeacherInquiryPageResponseDTO {
		items = items == null ? null : List.copyOf(items);
	}

	public static TeacherInquiryPageResponseDTO from(InquiryPage page) {
		return new TeacherInquiryPageResponseDTO(
				page.items().stream().map(TeacherInquirySummaryResponseDTO::from).toList(), page.page(), page.size(),
				page.totalElements());
	}

}
