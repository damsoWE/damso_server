package damso.inquiry.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 정리·분류 작업(아웃박스). 문의 접수와 같은 트랜잭션에서 저장되므로 외부 AI가 죽어도 작업은 남는다.
 * 내용은 담지 않는다 — 상태·횟수·시각뿐이다.
 * - 문의·종류마다 하나: (inquiry_id, kind) 유니크.
 * - 전이: PENDING → RUNNING, RUNNING → DONE/FAILED, RUNNING → RUNNING(임대 만료 후 다시 확보), FAILED → PENDING(교사 재시도, 1회).
 * - 동시 교사 재시도는 @Version이 하나만 통과시킨다.
 */
@Entity
@Table(name = "refinement_job",
		uniqueConstraints = @UniqueConstraint(name = "uk_refinement_job_inquiry_kind",
				columnNames = { "inquiry_id", "kind" }),
		indexes = @Index(name = "idx_refinement_job_due", columnList = "status, scheduled_at"))
public class RefinementJob extends BaseEntity {

	@Column(name = "inquiry_id", nullable = false, updatable = false)
	private UUID inquiryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private RefinementJobKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RefinementJobStatus status = RefinementJobStatus.PENDING;

	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "teacher_retry_used", nullable = false, columnDefinition = "boolean default false")
	private boolean teacherRetryUsed;

	/** 확보된 횟수. 반영이 계속 실패해 같은 작업이 끝없이 다시 실행되지 않게 상한을 두는 데 쓴다(자체 코드 리뷰). */
	@Column(name = "claim_count", nullable = false, columnDefinition = "integer default 0")
	private int claimCount;

	protected RefinementJob() {
	}

	private RefinementJob(UUID inquiryId, RefinementJobKind kind, Instant scheduledAt) {
		this.inquiryId = inquiryId;
		this.kind = kind;
		this.scheduledAt = scheduledAt;
	}

	public static RefinementJob refine(UUID inquiryId, Instant now) {
		return new RefinementJob(inquiryId, RefinementJobKind.REFINE, now);
	}

	public static RefinementJob classifyCrisis(UUID inquiryId, Instant now) {
		return new RefinementJob(inquiryId, RefinementJobKind.CLASSIFY_CRISIS, now);
	}

	/**
	 * 실행할 작업으로 확보한다. 대기 중이거나, 실행 중이지만 임대가 끝난 작업만. 임대 만료 시각은 DB 정밀도(마이크로초)로 맞춰 두어,
	 * 반영할 때 "아직 내가 잡은 작업인지"를 이 값으로 비교할 수 있게 한다.
	 */
	public void claim(Instant lockedUntil) {
		require(status == RefinementJobStatus.PENDING || status == RefinementJobStatus.RUNNING);
		this.status = RefinementJobStatus.RUNNING;
		this.lockedUntil = lockedUntil.truncatedTo(ChronoUnit.MICROS);
		this.claimCount++;
	}

	/** 이 임대로 확보한 작업이 아직 그대로인지(다른 실행기가 임대 만료 뒤 다시 잡지 않았는지). */
	public boolean isHeldBy(Instant lease) {
		return status == RefinementJobStatus.RUNNING && lease != null && lease.equals(lockedUntil);
	}

	/** 시도 한 번을 센다(자동·자동 재시도·교사). */
	public void countAttempt() {
		this.attemptCount++;
	}

	public void markDone() {
		require(status == RefinementJobStatus.RUNNING);
		this.status = RefinementJobStatus.DONE;
		this.lockedUntil = null;
	}

	public void markFailed() {
		require(status == RefinementJobStatus.RUNNING);
		this.status = RefinementJobStatus.FAILED;
		this.lockedUntil = null;
	}

	/** 교사 재시도로 다시 연다. 실패한 정리 작업이고 아직 교사 재시도를 쓰지 않았을 때만. */
	public void reopenForTeacherRetry(Instant now) {
		require(canTeacherRetry());
		this.status = RefinementJobStatus.PENDING;
		this.teacherRetryUsed = true;
		this.scheduledAt = now;
	}

	public boolean canTeacherRetry() {
		return kind == RefinementJobKind.REFINE && status == RefinementJobStatus.FAILED && !teacherRetryUsed;
	}

	private static void require(boolean allowed) {
		if (!allowed) {
			throw new IllegalStateException("illegal refinement job transition");
		}
	}

	public UUID getInquiryId() {
		return inquiryId;
	}

	public RefinementJobKind getKind() {
		return kind;
	}

	public RefinementJobStatus getStatus() {
		return status;
	}

	public Instant getScheduledAt() {
		return scheduledAt;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public int getClaimCount() {
		return claimCount;
	}

	/** 교사 재시도를 썼는지. 다시 열린 작업을 실행 중이면 이번 실행이 교사 재시도다. */
	public boolean isTeacherRetryUsed() {
		return teacherRetryUsed;
	}

}
