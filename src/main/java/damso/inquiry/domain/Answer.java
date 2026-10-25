package damso.inquiry.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import damso.common.crypto.SensitiveText;
import damso.common.entity.BaseEntity;

/**
 * 교사가 보낸 답변. 원문은 암호화해 저장하고 수정·삭제하지 않는다 (원칙 III, FR-001·003).
 * threadRootId는 이 답변이 속한 스레드(문의)의 루트 id다 — 경로 파라미터는 항상 루트 id이므로 재조회 없이 그대로 쓴다.
 * reviewed는 "점검을 요청했는지"만 기록한다. F3 전까지 점검은 항상 실패를 반환하는 스텁이라
 * reviewed=true라도 실질적인 점검 효과는 없다(FR-012).
 */
@Entity
@Table(name = "answer",
		indexes = @Index(name = "idx_answer_thread", columnList = "thread_root_id, sent_at"))
public class Answer extends BaseEntity {

	@Column(name = "thread_root_id", nullable = false, updatable = false)
	private UUID threadRootId;

	@Column(name = "teacher_id", nullable = false, updatable = false)
	private UUID teacherId;

	@Column(columnDefinition = "TEXT", nullable = false, updatable = false)
	private SensitiveText content;

	@Column(nullable = false, updatable = false)
	private boolean reviewed;

	@Column(name = "sent_at", nullable = false, updatable = false)
	private Instant sentAt;

	protected Answer() {
	}

	private Answer(UUID threadRootId, UUID teacherId, String content, boolean reviewed, Instant sentAt) {
		this.threadRootId = threadRootId;
		this.teacherId = teacherId;
		this.content = SensitiveText.of(content);
		this.reviewed = reviewed;
		this.sentAt = sentAt;
	}

	/** 전송. 본문 규칙은 InquiryContentPolicy가 먼저 검사한다. */
	public static Answer send(UUID threadRootId, UUID teacherId, String content, boolean reviewed, Instant now) {
		return new Answer(threadRootId, teacherId, content, reviewed, now);
	}

	public UUID getThreadRootId() {
		return threadRootId;
	}

	public UUID getTeacherId() {
		return teacherId;
	}

	public SensitiveText getContent() {
		return content;
	}

	public boolean isReviewed() {
		return reviewed;
	}

	public Instant getSentAt() {
		return sentAt;
	}

}
