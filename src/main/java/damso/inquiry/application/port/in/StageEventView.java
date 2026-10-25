package damso.inquiry.application.port.in;

import java.time.Instant;

import damso.inquiry.domain.InquiryStage;

/** 처리 단계가 바뀐 시점 하나. 학부모·교사 타임라인에서 공용으로 쓴다. */
public record StageEventView(InquiryStage stage, Instant occurredAt) {
}
