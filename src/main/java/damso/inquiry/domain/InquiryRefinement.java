package damso.inquiry.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import damso.common.crypto.SensitiveText;

/**
 * 검증을 통과한 정리 결과(번역본). 성공했을 때 문의당 한 번만 생기고 append-only다.
 * - 사실관계·요청사항은 처리 중 메모리에서 복원한 값을 원문처럼 암호화해 저장한다(대응표는 저장하지 않는다).
 * - 정서강도·라벨은 허용값 코드다(게이트웨이가 검증). 제목은 목록 표시를 위해 inquiry.title에 둔다.
 * - 본문은 toString·로그에 나오지 않는다(SensitiveText가 가린다).
 */
@Entity
@Immutable
@Table(name = "inquiry_refinement",
		uniqueConstraints = @UniqueConstraint(name = "uk_inquiry_refinement_inquiry", columnNames = "inquiry_id"))
public class InquiryRefinement implements Persistable<UUID> {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "inquiry_id", nullable = false, updatable = false)
	private UUID inquiryId;

	@Column(columnDefinition = "TEXT", nullable = false, updatable = false)
	private SensitiveText facts;

	@Column(columnDefinition = "TEXT", nullable = false, updatable = false)
	private SensitiveText requests;

	@Column(name = "emotion_level", nullable = false, updatable = false, length = 10)
	private String emotionLevel;

	@Column(name = "emotion_labels", nullable = false, updatable = false, length = 80)
	private String emotionLabels;

	@Column(name = "refined_at", nullable = false, updatable = false)
	private Instant refinedAt;

	@Transient
	private boolean isNew = true;

	protected InquiryRefinement() {
	}

	private InquiryRefinement(UUID inquiryId, String facts, String requests, String emotionLevel,
			List<String> emotionLabels, Instant refinedAt) {
		this.id = UUID.randomUUID();
		this.inquiryId = inquiryId;
		this.facts = SensitiveText.of(facts);
		this.requests = SensitiveText.of(requests);
		this.emotionLevel = emotionLevel;
		this.emotionLabels = String.join(",", emotionLabels);
		this.refinedAt = refinedAt;
	}

	public static InquiryRefinement of(UUID inquiryId, String facts, String requests, String emotionLevel,
			List<String> emotionLabels, Instant refinedAt) {
		return new InquiryRefinement(inquiryId, facts, requests, emotionLevel, emotionLabels, refinedAt);
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

	public SensitiveText getFacts() {
		return facts;
	}

	public SensitiveText getRequests() {
		return requests;
	}

	public String getEmotionLevel() {
		return emotionLevel;
	}

	public List<String> getEmotionLabels() {
		return emotionLabels.isEmpty() ? List.of() : Arrays.asList(emotionLabels.split(","));
	}

	public Instant getRefinedAt() {
		return refinedAt;
	}

	@Override
	public String toString() {
		return "InquiryRefinement[id=" + id + "]";
	}

}
