package damso.inquiry.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 재문의 요청. 본문 길이는 서버가 코드 포인트로 센다(최초 문의와 같은 규칙). */
public record FollowUpRequestDTO(@NotBlank String content) {

	/** 본문이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "FollowUpRequestDTO[]";
	}

}
