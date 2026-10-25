package damso.inquiry.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.InvalidInputException;
import damso.inquiry.application.port.in.StageEventView;
import damso.inquiry.application.port.in.TeacherInquiryQuery;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.Inquiry;
import damso.school.application.port.in.InquiryTargetQuery;

@Service
@Transactional(readOnly = true)
public class TeacherInquiryService implements TeacherInquiryQuery {

	static final int MAX_PAGE_SIZE = 50;

	static final String URGENT_TITLE = "긴급 문의";

	static final String PENDING_TITLE = "정리 중";

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	public TeacherInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories,
			InquiryTargetQuery targets) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
	}

	@Override
	public InquiryPage list(UUID teacherId, int page, int size) {
		if (page < 0) {
			throw new InvalidInputException("page", "허용 범위를 벗어났습니다");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidInputException("size", "허용 범위를 벗어났습니다");
		}
		Optional<UUID> classroomId = targets.homeroomClassroomOf(teacherId);
		if (classroomId.isEmpty()) {
			return new InquiryPage(List.of(), page, size, 0);
		}
		List<Inquiry> found = inquiries.findByClassroom(classroomId.get(), page, size);
		Map<UUID, String> names = targets.studentNames(found.stream().map(Inquiry::getStudentId).distinct().toList());
		List<InquirySummary> items = found.stream().map(inquiry -> summaryOf(inquiry, names)).toList();
		return new InquiryPage(items, page, size, inquiries.countByClassroom(classroomId.get()));
	}

	@Override
	public InquiryDetail get(UUID teacherId, UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		String studentName = targets.studentNames(List.of(inquiry.getStudentId())).getOrDefault(inquiry.getStudentId(),
				"");
		List<StageEventView> timeline = histories.findByInquiry(inquiryId).stream()
				.map(history -> new StageEventView(history.getStage(), history.getOccurredAt()))
				.toList();
		return new InquiryDetail(inquiry.getId(), inquiry.getStudentId(), studentName, inquiry.isUrgent(),
				displayTitle(inquiry), inquiry.getStage(), inquiry.getSubmittedAt(), timeline);
	}

	private static InquirySummary summaryOf(Inquiry inquiry, Map<UUID, String> names) {
		return new InquirySummary(inquiry.getId(), inquiry.getStudentId(), names.getOrDefault(inquiry.getStudentId(), ""),
				inquiry.isUrgent(), displayTitle(inquiry), inquiry.getStage(), inquiry.getSubmittedAt());
	}

	/** 화면 제목(저장하지 않는다): 긴급은 고정 문구, 아니면 정제 결과 제목(F3), 아직 없으면 "정리 중". */
	static String displayTitle(Inquiry inquiry) {
		if (inquiry.isUrgent()) {
			return URGENT_TITLE;
		}
		return inquiry.getTitle() != null ? inquiry.getTitle().reveal() : PENDING_TITLE;
	}

}
