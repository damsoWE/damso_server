package damso.inquiry.adapter.in.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.common.error.InvalidInputException;
import damso.common.security.CurrentUser;
import damso.inquiry.adapter.in.controller.dto.request.SubmitInquiryRequestDTO;
import damso.inquiry.adapter.in.controller.dto.response.InquirySubmittedResponseDTO;
import damso.inquiry.adapter.in.controller.dto.response.ParentInquiryDetailResponseDTO;
import damso.inquiry.adapter.in.controller.dto.response.ParentInquirySummaryResponseDTO;
import damso.inquiry.application.port.in.ParentInquiryQuery;
import damso.inquiry.application.port.in.SubmitInquiryUseCase;

/**
 * 학부모 문의 API (경로 단위 역할 검사는 SecurityConfig의 /api/v1/parent/**). 사용자 ID는 토큰에서만 꺼낸다.
 * 문의를 수정·삭제하는 경로는 없다.
 */
@RestController
@RequestMapping("/api/v1/parent/inquiries")
class ParentInquiryController {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final SubmitInquiryUseCase submitInquiry;

	private final ParentInquiryQuery query;

	ParentInquiryController(SubmitInquiryUseCase submitInquiry, ParentInquiryQuery query) {
		this.submitInquiry = submitInquiry;
		this.query = query;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	InquirySubmittedResponseDTO submit(CurrentUser parent,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody SubmitInquiryRequestDTO request) {
		return InquirySubmittedResponseDTO.from(submitInquiry.submit(parent.userId(), request.studentId(),
				request.content(), parseKey(idempotencyKey)));
	}

	@GetMapping
	List<ParentInquirySummaryResponseDTO> list(CurrentUser parent) {
		return query.list(parent.userId()).stream().map(ParentInquirySummaryResponseDTO::from).toList();
	}

	@GetMapping("/{inquiryId}")
	ParentInquiryDetailResponseDTO get(CurrentUser parent, @PathVariable UUID inquiryId) {
		return ParentInquiryDetailResponseDTO.from(query.get(parent.userId(), inquiryId));
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
