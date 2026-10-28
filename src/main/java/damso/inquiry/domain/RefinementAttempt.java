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
 * 정리 시도 한 번의 기록. append-only다. 내용·프롬프트·응답은 담지 않고 결과 코드만 남긴다.
 * 한 문의의 시도는 최대 3번이다(자동 1 + 자동 재시도 1 + 교사 1).
 */
@Entity
@Immutable
@Table(name = "refinement_attempt",
		indexes = @Index(name = "idx_refinement_attempt_job", columnList = "job_id, occurred_at"))
public class RefinementAttempt implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "job_id", nullable = false, updatable = false)
	private UUID jobId;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger", nullable = false, updatable = false, length = 20)
	private RefinementTrigger trigger;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private RefinementOutcomeCode outcome;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Transient
	private boolean isNew = true;

	protected RefinementAttempt() {
	}

	private RefinementAttempt(UUID jobId, RefinementTrigger trigger, RefinementOutcomeCode outcome,
			Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.jobId = jobId;
		this.trigger = trigger;
		this.outcome = outcome;
		this.occurredAt = occurredAt;
	}

	public static RefinementAttempt of(UUID jobId, RefinementTrigger trigger, RefinementOutcomeCode outcome,
			Instant occurredAt) {
		return new RefinementAttempt(jobId, trigger, outcome, occurredAt);
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

	public UUID getJobId() {
		return jobId;
	}

	public RefinementTrigger getTrigger() {
		return trigger;
	}

	public RefinementOutcomeCode getOutcome() {
		return outcome;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
