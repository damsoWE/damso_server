package damso.inquiry.adapter.in.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.inquiry.adapter.in.controller.dto.request.AnswerReviewRequestDTO;
import damso.inquiry.adapter.in.controller.dto.request.SendAnswerRequestDTO;
import damso.inquiry.adapter.in.controller.dto.response.AnswerReviewResultResponseDTO;
import damso.inquiry.adapter.in.controller.dto.response.AnswerSentResponseDTO;
import damso.inquiry.adapter.in.controller.dto.response.InquiryClosedResponseDTO;
import damso.inquiry.application.port.in.CloseInquiryUseCase;
import damso.inquiry.application.port.in.RequestAnswerReviewUseCase;
import damso.inquiry.application.port.in.SendAnswerUseCase;

/**
 * 교사 답변 API (경로 단위 역할 검사는 SecurityConfig의 /api/v1/teacher/**). {@code inquiryId}는 항상
 * 스레드 루트를 가리킨다. 답변을 수정·삭제하는 경로는 없다.
 */
@RestController
@RequestMapping("/api/v1/teacher/inquiries")
class TeacherAnswerController {

	private final SendAnswerUseCase sendAnswer;

	private final RequestAnswerReviewUseCase requestReview;

	private final CloseInquiryUseCase closeInquiry;

	TeacherAnswerController(SendAnswerUseCase sendAnswer, RequestAnswerReviewUseCase requestReview,
			CloseInquiryUseCase closeInquiry) {
		this.sendAnswer = sendAnswer;
		this.requestReview = requestReview;
		this.closeInquiry = closeInquiry;
	}

	@PostMapping("/{inquiryId}/answers")
	@ResponseStatus(HttpStatus.CREATED)
	AnswerSentResponseDTO send(CurrentUser teacher, @PathVariable UUID inquiryId,
			@Valid @RequestBody SendAnswerRequestDTO request) {
		return AnswerSentResponseDTO.from(
				sendAnswer.send(teacher.userId(), inquiryId, request.content(), request.reviewed()));
	}

	@PostMapping("/{inquiryId}/answer-reviews")
	AnswerReviewResultResponseDTO review(CurrentUser teacher, @PathVariable UUID inquiryId,
			@Valid @RequestBody AnswerReviewRequestDTO request) {
		return AnswerReviewResultResponseDTO.from(
				requestReview.request(teacher.userId(), inquiryId, request.content()));
	}

	@PostMapping("/{inquiryId}/close")
	InquiryClosedResponseDTO close(CurrentUser teacher, @PathVariable UUID inquiryId) {
		return InquiryClosedResponseDTO.from(closeInquiry.close(teacher.userId(), inquiryId));
	}

}
