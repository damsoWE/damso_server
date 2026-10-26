package damso.notification.domain;

import java.time.Duration;
import java.time.Instant;
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
 * 알림 요청(트랜잭셔널 아웃박스). 문의와 같은 트랜잭션에서 저장되므로 FCM·Redis가 죽어도 요청은 남는다 (원칙 IV).
 * - 받는 사람은 요청의 교사가 아니라 보낼 때의 현재 담임으로 다시 정한다(긴급·재알림·일반). 묶음은 요청의 교사에게 간다.
 * - 대기 중 묶음은 교사당 하나: active_digest_key(대기 중이면 "DIGEST:{교사 UUID}", 끝나면 NULL)가 유니크다.
 * - 상태 전이는 허용된 것만: PENDING → SENDING/CANCELLED/SKIPPED, SENDING → SENT/PENDING/CANCELLED/SKIPPED,
 *   SENDING → SENDING(임대 만료 후 다시 확보).
 * - 알림 내용은 저장하지 않는다. 종류·문의 UUID·건수만 있고 내용은 보낼 때 PushMessage가 고정 문구로 만든다.
 */
@Entity
@Table(name = "notification_request",
		uniqueConstraints = @UniqueConstraint(name = "uk_notification_active_digest", columnNames = "active_digest_key"),
		indexes = {
				@Index(name = "idx_notification_due", columnList = "status, scheduled_at"),
				@Index(name = "idx_notification_inquiry", columnList = "inquiry_id") })
public class NotificationRequest extends BaseEntity {

	private static final String DIGEST_KEY_PREFIX = "DIGEST:";

	@Column(name = "recipient_id", nullable = false, updatable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private NotificationKind kind;

	@Column(name = "inquiry_id", updatable = false)
	private UUID inquiryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status = NotificationStatus.PENDING;

	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "digest_count")
	private Integer digestCount;

	@Column(name = "active_digest_key", length = 60)
	private String activeDigestKey;

	protected NotificationRequest() {
	}

	private NotificationRequest(UUID recipientId, NotificationKind kind, UUID inquiryId, Instant scheduledAt) {
		this.recipientId = recipientId;
		this.kind = kind;
		this.inquiryId = inquiryId;
		this.scheduledAt = scheduledAt;
		if (kind == NotificationKind.GENERAL_DIGEST) {
			this.activeDigestKey = DIGEST_KEY_PREFIX + recipientId;
		}
	}

	public static NotificationRequest urgent(UUID recipientId, UUID inquiryId, Instant scheduledAt) {
		return new NotificationRequest(recipientId, NotificationKind.URGENT, inquiryId, scheduledAt);
	}

	public static NotificationRequest urgentReminder(UUID recipientId, UUID inquiryId, Instant scheduledAt) {
		return new NotificationRequest(recipientId, NotificationKind.URGENT_REMINDER, inquiryId, scheduledAt);
	}

	public static NotificationRequest general(UUID recipientId, UUID inquiryId, Instant scheduledAt) {
		return new NotificationRequest(recipientId, NotificationKind.GENERAL, inquiryId, scheduledAt);
	}

	public static NotificationRequest digest(UUID recipientId, Instant scheduledAt) {
		return new NotificationRequest(recipientId, NotificationKind.GENERAL_DIGEST, null, scheduledAt);
	}

	/** 답변 도착 알림(F4). recipientId는 학부모다 — 담임처럼 "보낼 때의 현재" 값으로 다시 찾지 않는다. */
	public static NotificationRequest answer(UUID recipientId, UUID inquiryId, Instant scheduledAt) {
		return new NotificationRequest(recipientId, NotificationKind.ANSWER, inquiryId, scheduledAt);
	}

	/** 발송기가 이 요청을 임대한다. 임대가 만료된 SENDING도 다시 확보할 수 있다. */
	public void claim(Instant lockedUntil) {
		requireOpen();
		this.status = NotificationStatus.SENDING;
		this.lockedUntil = lockedUntil;
	}

	public void markSent(Instant now) {
		requireSending();
		this.status = NotificationStatus.SENT;
		this.sentAt = now;
		this.lockedUntil = null;
		this.activeDigestKey = null;
	}

	/** 실패했다. 횟수를 올리고 delay 뒤로 미룬다. */
	public void retryAfter(Instant now, Duration delay) {
		requireSending();
		this.status = NotificationStatus.PENDING;
		this.attempts += 1;
		this.scheduledAt = now.plus(delay);
		this.lockedUntil = null;
	}

	/** 보낼 곳이 없어(활성 기기 없음 등) 횟수를 올리지 않고 다음 주기에 다시 본다. */
	public void release() {
		requireSending();
		this.status = NotificationStatus.PENDING;
		this.lockedUntil = null;
	}

	public void cancel() {
		requireOpen();
		close(NotificationStatus.CANCELLED);
	}

	public void skip() {
		requireOpen();
		close(NotificationStatus.SKIPPED);
	}

	/** 묶음 알림에 포함된 건수를 남긴다. */
	public void recordDigestCount(int count) {
		this.digestCount = count;
	}

	private void close(NotificationStatus end) {
		this.status = end;
		this.lockedUntil = null;
		this.activeDigestKey = null;
	}

	private void requireOpen() {
		if (!status.isOpen()) {
			throw new IllegalStateException("illegal notification transition");
		}
	}

	private void requireSending() {
		if (status != NotificationStatus.SENDING) {
			throw new IllegalStateException("illegal notification transition");
		}
	}

	public UUID getRecipientId() {
		return recipientId;
	}

	public NotificationKind getKind() {
		return kind;
	}

	public UUID getInquiryId() {
		return inquiryId;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public Instant getScheduledAt() {
		return scheduledAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Integer getDigestCount() {
		return digestCount;
	}

	public String getActiveDigestKey() {
		return activeDigestKey;
	}

}
