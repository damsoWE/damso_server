package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.AnswerView;

/** 스레드 타임라인에 포함되는 답변 하나. */
public record AnswerViewResponseDTO(UUID answerId, String content, boolean reviewed, Instant sentAt) {

	public static AnswerViewResponseDTO from(AnswerView view) {
		return new AnswerViewResponseDTO(view.answerId(), view.content(), view.reviewed(), view.sentAt());
	}

	/** 답변 본문이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "AnswerViewResponseDTO[answerId=" + answerId + "]";
	}

}
