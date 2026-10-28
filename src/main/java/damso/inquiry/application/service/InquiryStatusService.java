package damso.inquiry.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.in.InquiryStatusQuery;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryPort.ThreadStageCount;
import damso.inquiry.domain.InquiryStage;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 슈퍼 관리자 문의 현황 (F5 US3, research R6). 단계를 그대로 세면 DELIVERED가 곧 긴급 건수가 되므로 세 묶음으로만 나눈다.
 * 학급 순서는 관리자 학급 목록과 같은 학년·반 순이다. 본문·제목은 읽지 않는다. 감사 기록 대상이 아니다.
 */
@Service
@Transactional(readOnly = true)
public class InquiryStatusService implements InquiryStatusQuery {

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	public InquiryStatusService(InquiryPort inquiries, InquiryTargetQuery targets) {
		this.inquiries = inquiries;
		this.targets = targets;
	}

	@Override
	public List<ClassroomInquiryStatus> statusByClassroom() {
		Map<UUID, long[]> buckets = new HashMap<>();
		for (ThreadStageCount count : inquiries.countThreadsByClassroomAndStage()) {
			buckets.computeIfAbsent(count.classroomId(), id -> new long[3])[bucketOf(count)] += count.count();
		}
		return targets.classroomIdsInOrder().stream().map(classroomId -> {
			long[] counts = buckets.getOrDefault(classroomId, new long[3]);
			return new ClassroomInquiryStatus(classroomId, counts[0], counts[1], counts[2]);
		}).toList();
	}

	/** 0 = waiting, 1 = confirmed, 2 = answered. 답변을 보냈는지를 먼저 본다. */
	private static int bucketOf(ThreadStageCount count) {
		if (count.answerSent() || count.stage() == InquiryStage.ANSWERED) {
			return 2;
		}
		return count.stage() == InquiryStage.TEACHER_CONFIRMED ? 1 : 0;
	}

}
