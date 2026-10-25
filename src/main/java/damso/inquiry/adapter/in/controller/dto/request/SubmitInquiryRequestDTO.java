package damso.inquiry.adapter.in.controller.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 문의 제출 요청. 제목·연락처 같은 다른 필드는 받지 않는다(알 수 없는 필드는 거부). 본문은 길이를 서버가 코드 포인트로 센다. */
public record SubmitInquiryRequestDTO(@NotNull UUID studentId, @NotBlank String content) {

	/** 본문이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "SubmitInquiryRequestDTO[]";
	}

}
