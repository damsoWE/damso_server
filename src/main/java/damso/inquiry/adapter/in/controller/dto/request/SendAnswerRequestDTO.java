package damso.inquiry.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 답변 전송 요청. 길이는 코드 포인트 기준으로 서버(InquiryContentPolicy)가 다시 센다. */
public record SendAnswerRequestDTO(@NotBlank String content, @NotNull Boolean reviewed) {

	/** 본문이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "SendAnswerRequestDTO[]";
	}

}
