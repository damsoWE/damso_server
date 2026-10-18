package damso.inquiry.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.inquiry.application.port.in.ViewInquiryContentUseCase.InquiryContentView;

/** 열람한 원문. toString은 본문을 가려 로그에 섞이지 않게 한다. */
public record InquiryContentResponseDTO(UUID inquiryId, String content) {

	public static InquiryContentResponseDTO from(InquiryContentView view) {
		return new InquiryContentResponseDTO(view.inquiryId(), view.content());
	}

	@Override
	public String toString() {
		return "InquiryContentResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
