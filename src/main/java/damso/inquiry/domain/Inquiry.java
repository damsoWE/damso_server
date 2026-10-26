package damso.inquiry.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.crypto.SensitiveText;
import damso.common.entity.BaseEntity;

/**
 * 학부모가 제출한 문의. 원문은 암호화해 저장하고 수정·삭제하지 않는다 (원칙 III, FR-005·006).
 * - 접수 시점의 학생·학급·담임은 변경할 수 없다(알림 대상은 보낼 때 현재 담임으로 다시 확인한다).
 * - content·urgent·threat_flagged는 updatable = false이고 setter가 없다. 바뀌는 것은 단계·위기 종류·알림 표시·정리 상태와,
 *   비어 있던 제목을 정리 결과로 한 번 채우는 것뿐이다(F3).
 * - 단계 전이는 InquiryStage의 허용표로만 하고, 동시 전이는 @Version이 막는다.
 * - request_hash는 같은 Idempotency-Key가 다른 내용으로 재사용됐는지 비교하는 값이며 원문은 담지 않는다.
 * - 본문·제목은 toString·로그에 나오지 않는다(SensitiveText가 가린다).
 */
@Entity
@Table(name = "inquiry",
		uniqueConstraints = @UniqueConstraint(name = "uk_inquiry_parent_key",
				columnNames = { "parent_id", "idempotency_key" }),
		indexes = {
				@Index(name = "idx_inquiry_classroom_urgent", columnList = "classroom_id, urgent, submitted_at"),
				@Index(name = "idx_inquiry_parent", columnList = "parent_id, submitted_at"),
				@Index(name = "idx_inquiry_deferred",
						columnList = "teacher_id, notification_deferred, deferred_notified_at"),
				@Index(name = "idx_inquiry_thread_root", columnList = "thread_root_id") })
public class Inquiry extends BaseEntity {

	/** 최초 문의는 NULL(자기 자신이 스레드 루트). 재문의는 스레드 최초 문의의 id (F4, FR-014~016). */
	@Column(name = "thread_root_id", updatable = false)
	private UUID threadRootId;

	@Column(name = "parent_id", nullable = false, updatable = false)
	private UUID parentId;

	@Column(name = "student_id", nullable = false, updatable = false)
	private UUID studentId;

	@Column(name = "classroom_id", nullable = false, updatable = false)
	private UUID classroomId;

	@Column(name = "teacher_id", nullable = false, updatable = false)
	private UUID teacherId;

	@Column(columnDefinition = "TEXT", nullable = false, updatable = false)
	private SensitiveText content;

	@Column(columnDefinition = "TEXT")
	private SensitiveText title;

	@Column(nullable = false, updatable = false)
	private boolean urgent;

	/** 접수 때 정한 위협·폭언 표현 표시. 긴급 문의는 항상 false. 바뀌지 않는다(spec 005 FR-005a·007). */
	// 기본값을 컬럼에 둔다 — 행이 있는 DB에 자동 스키마 갱신으로 NOT NULL 컬럼을 더할 때 실패하지 않게(deferred_notice와 같은 이유)
	@Column(name = "threat_flagged", nullable = false, updatable = false, columnDefinition = "boolean default false")
	private boolean threatFlagged;

	@Enumerated(EnumType.STRING)
	@Column(name = "crisis_type", nullable = false, length = 20)
	private CrisisType crisisType = CrisisType.UNCLASSIFIED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryStage stage = InquiryStage.RECEIVED;

	/** 일반 문의의 정리 상태(F3). 긴급 문의는 null — 정리 대상이 아니다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "refinement_status", length = 20)
	private RefinementStatus refinementStatus;

	/** 접수 때 학부모에게 "응답 가능 시간에 전달됩니다"를 보였는지. 학부모 응답(첫 응답·재전송·상세)은 이 값으로 만든다(F3 R10). */
	// 기본값을 컬럼에 둔다 — 행이 있는 DB에 자동 스키마 갱신으로 NOT NULL 컬럼을 더할 때 실패하지 않게(자체 코드 리뷰)
	@Column(name = "deferred_notice", nullable = false, updatable = false, columnDefinition = "boolean default false")
	private boolean deferredNotice;

	@Column(name = "notification_deferred", nullable = false)
	private boolean notificationDeferred;

	@Column(name = "deferred_notified_at")
	private Instant deferredNotifiedAt;

	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private UUID idempotencyKey;

	@Column(name = "request_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
	private String requestHash;

	@Column(name = "submitted_at", nullable = false, updatable = false)
	private Instant submittedAt;

	protected Inquiry() {
	}

	private Inquiry(UUID threadRootId, UUID parentId, UUID studentId, UUID classroomId, UUID teacherId,
			String content, boolean urgent, boolean threatFlagged, UUID idempotencyKey, String requestHash,
			Instant submittedAt) {
		this.threadRootId = threadRootId;
		this.parentId = parentId;
		this.studentId = studentId;
		this.classroomId = classroomId;
		this.teacherId = teacherId;
		this.content = SensitiveText.of(content);
		this.urgent = urgent;
		// 긴급이 먼저다 — 긴급 문의에는 위협·폭언 표시를 붙이지 않는다(spec 005 FR-005a)
		this.threatFlagged = !urgent && threatFlagged;
		this.refinementStatus = urgent ? null : RefinementStatus.PENDING;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.submittedAt = submittedAt;
	}

	/** 접수(스레드 최초 문의). 본문 규칙은 InquiryContentPolicy가 먼저 검사한다. 단계는 RECEIVED로 시작한다. */
	public static Inquiry receive(UUID parentId, UUID studentId, UUID classroomId, UUID teacherId, String content,
			boolean urgent, boolean threatFlagged, UUID idempotencyKey, String requestHash, Instant now) {
		return new Inquiry(null, parentId, studentId, classroomId, teacherId, content, urgent, threatFlagged,
				idempotencyKey, requestHash, now);
	}

	/**
	 * 같은 스레드에 이어서 제출하는 재문의. receive와 같지만 threadRootId를 받는다(F4, research R5).
	 * 대상(학급·담임)은 매번 다시 확인한 현재 값을 받는다 — 스레드 시작 시점으로 고정하지 않는다.
	 * 위협·폭언 표시도 재문의마다 접수 때 정한다(spec 005).
	 */
	public static Inquiry followUp(UUID threadRootId, UUID parentId, UUID studentId, UUID classroomId,
			UUID teacherId, String content, boolean urgent, boolean threatFlagged, UUID idempotencyKey,
			String requestHash, Instant now) {
		return new Inquiry(threadRootId, parentId, studentId, classroomId, teacherId, content, urgent, threatFlagged,
				idempotencyKey, requestHash, now);
	}

	/** 허용된 전이만 한다. 아니면 IllegalStateException. */
	public void moveTo(InquiryStage next) {
		if (!stage.canMoveTo(urgent, next)) {
			throw new IllegalStateException("illegal inquiry stage transition");
		}
		this.stage = next;
	}

	/**
	 * 위기 종류를 정한다. 처음 분류된 값만 유지한다 — 이미 분류됐거나(UNCLASSIFIED가 아님) 분류 결과가 UNCLASSIFIED면 바꾸지 않는다.
	 * 바뀌는 것은 crisis_type뿐이다(원문·긴급 표시는 불변).
	 * @return 값이 바뀌었으면 true
	 */
	public boolean applyCrisisType(CrisisType type) {
		if (type == null || type == CrisisType.UNCLASSIFIED || crisisType != CrisisType.UNCLASSIFIED) {
			return false;
		}
		this.crisisType = type;
		return true;
	}

	/** 응답 가능 시간 밖이라 알림이 미뤄진 일반 문의로 표시한다(묶음 알림 건수 집계 대상). */
	public void markNotificationDeferred() {
		this.notificationDeferred = true;
	}

	/** 접수 때 학부모에게 응답 가능 시간 안내를 보였다고 남긴다. 저장 전(접수 트랜잭션)에만 의미가 있다 — 이후에는 바뀌지 않는다. */
	public void markDeferredNotice() {
		this.deferredNotice = true;
	}

	/**
	 * 검증된 정리 결과를 반영한다(F3). 제목은 비어 있을 때만 채운다. 정리 상태는 REFINED가 되고, 단계가 아직 접수(RECEIVED)면
	 * 정제(REFINING)로 간다. 이미 교사 확인 이후면 단계는 그대로다(단계는 앞으로만 간다).
	 * @return 단계가 REFINING으로 바뀌었으면 true
	 */
	public boolean applyRefinement(String refinedTitle) {
		requireGeneral();
		if (this.title == null) {
			this.title = SensitiveText.of(refinedTitle);
		}
		this.refinementStatus = RefinementStatus.REFINED;
		if (stage == InquiryStage.RECEIVED) {
			moveTo(InquiryStage.REFINING);
			return true;
		}
		return false;
	}

	/** 자동 정리가 실패로 확정됐다(F3 FR-015). 단계는 그대로다. */
	public void markNeedsOriginal() {
		requireGeneral();
		this.refinementStatus = RefinementStatus.NEEDS_ORIGINAL;
	}

	/**
	 * 원문 확인 필요(번역본 없음) 일반 문의를 교사가 원문으로 처음 확인했다(F3 FR-025): RECEIVED → TEACHER_CONFIRMED.
	 * 그 밖의 상태에서는 IllegalStateException — 번역본이 있는 문의는 번역본 첫 열람이 기준이다.
	 */
	public void confirmByOriginalView() {
		if (refinementStatus != RefinementStatus.NEEDS_ORIGINAL
				|| !stage.canSkipRefinementTo(urgent, InquiryStage.TEACHER_CONFIRMED)) {
			throw new IllegalStateException("illegal inquiry stage transition");
		}
		this.stage = InquiryStage.TEACHER_CONFIRMED;
	}

	/** 교사 재시도로 다시 정리 중이 된다. 원문 확인 필요 상태에서만. */
	public void reopenRefinement() {
		if (refinementStatus != RefinementStatus.NEEDS_ORIGINAL) {
			throw new IllegalStateException("refinement is not waiting for the original check");
		}
		this.refinementStatus = RefinementStatus.PENDING;
	}

	private void requireGeneral() {
		if (urgent) {
			throw new IllegalStateException("urgent inquiries are not refined");
		}
	}

	/** 묶음 알림에 포함되어 나간 시각을 남긴다. */
	public void markDeferredNotified(Instant at) {
		this.deferredNotifiedAt = at;
	}

	public UUID getThreadRootId() {
		return threadRootId;
	}

	public UUID getParentId() {
		return parentId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public UUID getClassroomId() {
		return classroomId;
	}

	public UUID getTeacherId() {
		return teacherId;
	}

	public SensitiveText getContent() {
		return content;
	}

	public SensitiveText getTitle() {
		return title;
	}

	public boolean isUrgent() {
		return urgent;
	}

	public boolean isThreatFlagged() {
		return threatFlagged;
	}

	public CrisisType getCrisisType() {
		return crisisType;
	}

	public InquiryStage getStage() {
		return stage;
	}

	public boolean isNotificationDeferred() {
		return notificationDeferred;
	}

	public RefinementStatus getRefinementStatus() {
		return refinementStatus;
	}

	public boolean isDeferredNotice() {
		return deferredNotice;
	}

	public Instant getDeferredNotifiedAt() {
		return deferredNotifiedAt;
	}

	public UUID getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

}
