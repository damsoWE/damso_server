package damso.inquiry.adapter.in.controller.dto.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 답변 전송 요청. 길이는 코드 포인트 기준으로 서버(InquiryContentPolicy)가 다시 센다.
 * lastSeenFollowUpId는 키가 반드시 있어야 하고 값은 null일 수 있다(재문의가 없었으면 null, 이슈 #28) —
 * 키가 빠진 예전 요청을 "본 재문의 없음"으로 받아 검사를 건너뛰지 않도록 400으로 거른다.
 */
public record SendAnswerRequestDTO(@NotBlank String content, @NotNull Boolean reviewed,
		@JsonProperty(required = true) UUID lastSeenFollowUpId) {

	/** 본문이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "SendAnswerRequestDTO[]";
	}

}
