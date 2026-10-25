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
 * - content·urgent는 updatable = false이고 setter가 없다. 바뀌는 것은 단계·위기 종류·알림 표시뿐이다.
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

	@Enumerated(EnumType.STRING)
	@Column(name = "crisis_type", nullable = false, length = 20)
	private CrisisType crisisType = CrisisType.UNCLASSIFIED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryStage stage = InquiryStage.RECEIVED;

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
			String content, boolean urgent, UUID idempotencyKey, String requestHash, Instant submittedAt) {
		this.threadRootId = threadRootId;
		this.parentId = parentId;
		this.studentId = studentId;
		this.classroomId = classroomId;
		this.teacherId = teacherId;
		this.content = SensitiveText.of(content);
		this.urgent = urgent;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.submittedAt = submittedAt;
	}

	/** 접수(스레드 최초 문의). 본문 규칙은 InquiryContentPolicy가 먼저 검사한다. 단계는 RECEIVED로 시작한다. */
	public static Inquiry receive(UUID parentId, UUID studentId, UUID classroomId, UUID teacherId, String content,
			boolean urgent, UUID idempotencyKey, String requestHash, Instant now) {
		return new Inquiry(null, parentId, studentId, classroomId, teacherId, content, urgent, idempotencyKey,
				requestHash, now);
	}

	/**
	 * 같은 스레드에 이어서 제출하는 재문의. receive와 같지만 threadRootId를 받는다(F4, research R5).
	 * 대상(학급·담임)은 매번 다시 확인한 현재 값을 받는다 — 스레드 시작 시점으로 고정하지 않는다.
	 */
	public static Inquiry followUp(UUID threadRootId, UUID parentId, UUID studentId, UUID classroomId,
			UUID teacherId, String content, boolean urgent, UUID idempotencyKey, String requestHash, Instant now) {
		return new Inquiry(threadRootId, parentId, studentId, classroomId, teacherId, content, urgent,
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

	/** 응답 가능 시간 밖이라 알림이 미뤄진 일반 문의로 표시한다. 접수 트랜잭션 안에서만 쓴다. */
	public void markNotificationDeferred() {
		this.notificationDeferred = true;
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

	public CrisisType getCrisisType() {
		return crisisType;
	}

	public InquiryStage getStage() {
		return stage;
	}

	public boolean isNotificationDeferred() {
		return notificationDeferred;
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
