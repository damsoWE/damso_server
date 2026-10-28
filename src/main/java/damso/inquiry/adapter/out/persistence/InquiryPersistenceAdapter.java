package damso.inquiry.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;

/** inquiry 도메인의 출력 포트를 Spring Data JPA로 구현한다. */
@Component
class InquiryPersistenceAdapter implements InquiryPort, InquiryStageHistoryPort {

	private static final String LOCK_WAIT_MILLIS = "3000";

	private final SpringDataInquiryRepository inquiries;

	private final InquiryStageHistoryRepository histories;

	InquiryPersistenceAdapter(SpringDataInquiryRepository inquiries, InquiryStageHistoryRepository histories) {
		this.inquiries = inquiries;
		this.histories = histories;
	}

	// InquiryPort

	@Override
	public Inquiry saveInquiry(Inquiry inquiry) {
		return inquiries.saveAndFlush(inquiry);
	}

	@Override
	public Optional<Inquiry> findInquiryById(UUID id) {
		return inquiries.findById(id);
	}

	@Override
	public Optional<Inquiry> findInquiryByIdForUpdate(UUID id) {
		return inquiries.findByIdForUpdate(id);
	}

	@Override
	public void lockSubmission(UUID parentId, UUID idempotencyKey) {
		// 앞선 요청이 오래 걸려도 커넥션을 쥔 채 무한정 기다리지 않는다. 시간이 지나면 락 획득 실패 예외가 난다.
		inquiries.limitLockWait(LOCK_WAIT_MILLIS);
		inquiries.lockSubmission("inquiry-submit:" + parentId + ":" + idempotencyKey);
	}

	@Override
	public Optional<Inquiry> findByParentAndKey(UUID parentId, UUID idempotencyKey) {
		return inquiries.findByParentIdAndIdempotencyKey(parentId, idempotencyKey);
	}

	@Override
	public List<Inquiry> findByParent(UUID parentId) {
		return inquiries.findByParentIdAndThreadRootIdIsNullOrderBySubmittedAtDesc(parentId);
	}

	@Override
	public List<Inquiry> findByClassroom(UUID classroomId, int page, int size) {
		Sort sort = Sort.by(Sort.Order.desc("urgent"), Sort.Order.desc("submittedAt"), Sort.Order.asc("id"));
		return inquiries.findByClassroomIdAndThreadRootIdIsNull(classroomId, PageRequest.of(page, size, sort));
	}

	@Override
	public long countByClassroom(UUID classroomId) {
		return inquiries.countByClassroomIdAndThreadRootIdIsNull(classroomId);
	}

	@Override
	public List<Inquiry> findDeferredNotNotified(UUID teacherId) {
		return inquiries.findByTeacherIdAndNotificationDeferredTrueAndDeferredNotifiedAtIsNull(teacherId);
	}

	@Override
	public List<Inquiry> findThreadMessages(UUID rootId) {
		return inquiries.findThreadMessages(rootId);
	}

	@Override
	public Set<UUID> findThreadsWithThreatFlaggedFollowUp(Collection<UUID> rootIds) {
		if (rootIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(inquiries.findThreadRootIdsWithThreatFlaggedFollowUp(rootIds));
	}

	@Override
	public List<ThreadStageCount> countThreadsByClassroomAndStage() {
		List<ThreadStageCount> counts = new ArrayList<>();
		for (Object[] row : inquiries.countThreadsByClassroomAndStage()) {
			UUID classroomId = (UUID) row[0];
			InquiryStage stage = (InquiryStage) row[1];
			long total = ((Number) row[2]).longValue();
			long answered = ((Number) row[3]).longValue();
			// 문의가 없는 조합은 결과에 넣지 않는다(포트 계약)
			if (total > answered) {
				counts.add(new ThreadStageCount(classroomId, stage, false, total - answered));
			}
			if (answered > 0) {
				counts.add(new ThreadStageCount(classroomId, stage, true, answered));
			}
		}
		return counts;
	}

	// InquiryStageHistoryPort

	@Override
	public InquiryStageHistory append(InquiryStageHistory history) {
		return histories.save(history);
	}

	@Override
	public List<InquiryStageHistory> findByInquiry(UUID inquiryId) {
		// 같은 시각이면 단계가 진행하는 순서(ordinal)로 정렬한다. 단계는 항상 앞으로만 간다.
		return histories.findByInquiryIdOrderByOccurredAt(inquiryId).stream()
				.sorted(Comparator.comparing(InquiryStageHistory::getOccurredAt)
						.thenComparing(history -> history.getStage().ordinal()))
				.toList();
	}

}
