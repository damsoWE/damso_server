package damso.inquiry.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * 문의 단계가 바뀐 기록. append-only다 — 수정·삭제 경로가 없다.
 * BaseEntity를 상속하지 않으며(수정 시각·버전이 의미 없음) 개인정보·본문 컬럼이 없다.
 * 학부모 타임라인은 이 표를 시간순으로 그대로 보여 준다.
 */
@Entity
@Immutable
@Table(name = "inquiry_stage_history",
		indexes = @Index(name = "idx_stage_history_inquiry", columnList = "inquiry_id, occurred_at"))
public class InquiryStageHistory implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "inquiry_id", nullable = false, updatable = false)
	private UUID inquiryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private InquiryStage stage;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Transient
	private boolean isNew = true;

	protected InquiryStageHistory() {
	}

	private InquiryStageHistory(UUID inquiryId, InquiryStage stage, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.inquiryId = inquiryId;
		this.stage = stage;
		this.occurredAt = occurredAt;
	}

	public static InquiryStageHistory of(UUID inquiryId, InquiryStage stage, Instant occurredAt) {
		return new InquiryStageHistory(inquiryId, stage, occurredAt);
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		this.isNew = false;
	}

	public UUID getInquiryId() {
		return inquiryId;
	}

	public InquiryStage getStage() {
		return stage;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
