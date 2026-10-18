package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;

import damso.inquiry.application.port.in.StageEventView;
import damso.inquiry.domain.InquiryStage;

/** 처리 단계 이벤트 하나. 학부모·교사 타임라인에서 공용으로 쓴다. */
public record StageEventResponseDTO(InquiryStage stage, Instant occurredAt) {

	public static StageEventResponseDTO from(StageEventView event) {
		return new StageEventResponseDTO(event.stage(), event.occurredAt());
	}

}
