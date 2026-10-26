package damso.inquiry.adapter.in.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.common.error.InvalidInputException;
import damso.common.security.CurrentUser;
import damso.inquiry.adapter.in.controller.dto.request.FollowUpRequestDTO;
import damso.inquiry.adapter.in.controller.dto.response.FollowUpSubmittedResponseDTO;
import damso.inquiry.application.port.in.SubmitFollowUpUseCase;

/**
 * 재문의 API (경로 단위 역할 검사는 SecurityConfig의 /api/v1/parent/**). {@code inquiryId}는 항상
 * 스레드 루트의 id다. 멱등키 파싱은 F2 {@code ParentInquiryController}와 같은 규칙.
 */
@RestController
@RequestMapping("/api/v1/parent/inquiries")
class ParentFollowUpController {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final SubmitFollowUpUseCase submitFollowUp;

	ParentFollowUpController(SubmitFollowUpUseCase submitFollowUp) {
		this.submitFollowUp = submitFollowUp;
	}

	@PostMapping("/{inquiryId}/follow-ups")
	@ResponseStatus(HttpStatus.CREATED)
	FollowUpSubmittedResponseDTO submit(CurrentUser parent, @PathVariable UUID inquiryId,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody FollowUpRequestDTO request) {
		return FollowUpSubmittedResponseDTO
				.from(submitFollowUp.submit(parent.userId(), inquiryId, request.content(), parseKey(idempotencyKey)));
	}

	/** 누락·UUID가 아닌 값은 400. 사유는 고정 문구이며 보낸 값을 담지 않는다. */
	private static UUID parseKey(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new InvalidInputException(IDEMPOTENCY_KEY, "필수 값입니다");
		}
		String trimmed = raw.trim();
		try {
			UUID key = UUID.fromString(trimmed);
			if (trimmed.length() == 36) {
				return key;
			}
		}
		catch (IllegalArgumentException ex) {
			// 아래에서 같은 오류로 처리한다
		}
		throw new InvalidInputException(IDEMPOTENCY_KEY, "형식이 올바르지 않습니다");
	}

}
