package damso.inquiry.adapter.in.controller.dto.response;

import java.util.List;

import damso.inquiry.application.port.in.InquiryStatusQuery.ClassroomInquiryStatus;

/** 슈퍼 관리자 문의 현황 응답. 학급 순서는 학년·반 순. */
public record InquiryStatusSummaryResponseDTO(List<ClassroomInquiryStatusResponseDTO> items) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public InquiryStatusSummaryResponseDTO {
		items = items == null ? null : List.copyOf(items);
	}

	public static InquiryStatusSummaryResponseDTO from(List<ClassroomInquiryStatus> statuses) {
		return new InquiryStatusSummaryResponseDTO(
				statuses.stream().map(ClassroomInquiryStatusResponseDTO::from).toList());
	}

}
