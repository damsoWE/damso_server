package damso.inquiry.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.InvalidInputException;
import damso.inquiry.application.port.in.AnswerView;
import damso.inquiry.application.port.in.StageEventView;
import damso.inquiry.application.port.in.TeacherInquiryQuery;
import damso.inquiry.application.port.out.AnswerPort;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.application.port.out.RefinementJobPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.RefinementJobKind;
import damso.inquiry.domain.RefinementStatus;
import damso.school.application.port.in.InquiryTargetQuery;

@Service
@Transactional(readOnly = true)
public class TeacherInquiryService implements TeacherInquiryQuery {

	static final int MAX_PAGE_SIZE = 50;

	/** 쪽 번호 상한. 크기 50이어도 건너뛰는 행 수(offset)가 int 범위 안에 머물러, 큰 값이 500으로 새지 않는다. */
	static final int MAX_PAGE = 10_000;

	static final String URGENT_TITLE = "긴급 문의";

	static final String PENDING_TITLE = "정리 중";

	static final String NEEDS_ORIGINAL_TITLE = "원문 확인 필요";

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	private final AnswerPort answers;

	private final RefinementJobPort jobs;

	public TeacherInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets,
			AnswerPort answers, RefinementJobPort jobs) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
		this.answers = answers;
		this.jobs = jobs;
	}

	@Override
	public InquiryPage list(UUID teacherId, int page, int size) {
		if (page < 0 || page > MAX_PAGE) {
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
		List<UUID> rootIds = found.stream().map(Inquiry::getId).toList();
		Set<UUID> flaggedThreads = inquiries.findThreadsWithThreatFlaggedFollowUp(rootIds);
		Map<UUID, Instant> lastAnswered = answers.findLastAnsweredAt(rootIds);
		List<InquirySummary> items = found.stream()
				.map(inquiry -> summaryOf(inquiry, names, flaggedThreads, lastAnswered))
				.toList();
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
		// 재문의 자신의 id로는 이 스레드 상세를 취급하지 않는다 — 스레드 루트일 때만 묶어서 보여준다.
		boolean isThreadRoot = inquiry.getThreadRootId() == null;
		List<FollowUpView> followUps = isThreadRoot ? followUpsOf(inquiry.getId()) : List.of();
		List<AnswerView> answerViews = isThreadRoot ? answersOf(inquiry.getId()) : List.of();
		boolean retryAvailable = inquiry.getRefinementStatus() == RefinementStatus.NEEDS_ORIGINAL
				&& jobs.findByInquiryAndKind(inquiryId, RefinementJobKind.REFINE)
						.map(RefinementJob::canTeacherRetry).orElse(false);
		return new InquiryDetail(inquiry.getId(), inquiry.getStudentId(), studentName, inquiry.isUrgent(),
				displayTitle(inquiry), inquiry.getStage(), inquiry.getRefinementStatus(), retryAvailable,
				inquiry.isThreatFlagged(), inquiry.getSubmittedAt(), timeline, followUps, answerViews);
	}

	private List<FollowUpView> followUpsOf(UUID rootId) {
		return inquiries.findThreadMessages(rootId).stream()
				.filter(message -> message.getThreadRootId() != null)
				.map(message -> new FollowUpView(message.getId(), message.isUrgent(), displayTitle(message),
						message.getStage(), message.isThreatFlagged(), message.getSubmittedAt()))
				.toList();
	}

	private List<AnswerView> answersOf(UUID rootId) {
		return answers.findByThreadRootId(rootId).stream()
				.map(answer -> new AnswerView(answer.getId(), answer.getContent().reveal(), answer.isReviewed(),
						answer.getSentAt()))
				.toList();
	}

	/**
	 * flaggedThreads: 위협·폭언 표시가 붙은 재문의가 있는 스레드 루트. 루트 자신의 표시와 OR해 스레드 단위 표시를 만든다.
	 * lastAnswered: 스레드 루트별 마지막 답변 전송 시각, 답변이 없는 루트는 없다.
	 */
	private static InquirySummary summaryOf(Inquiry inquiry, Map<UUID, String> names, Set<UUID> flaggedThreads,
			Map<UUID, Instant> lastAnswered) {
		return new InquirySummary(inquiry.getId(), inquiry.getStudentId(), names.getOrDefault(inquiry.getStudentId(), ""),
				inquiry.isUrgent(), displayTitle(inquiry), inquiry.getStage(), inquiry.getRefinementStatus(),
				inquiry.isThreatFlagged(), inquiry.isThreatFlagged() || flaggedThreads.contains(inquiry.getId()),
				inquiry.getSubmittedAt(), lastAnswered.get(inquiry.getId()));
	}

	/**
	 * 화면 제목(저장하지 않는다): 긴급은 고정 문구, 정리 결과 제목이 있으면 그 제목, 자동 정리에 실패했으면 "원문 확인 필요",
	 * 아직 없으면 "정리 중".
	 */
	static String displayTitle(Inquiry inquiry) {
		if (inquiry.isUrgent()) {
			return URGENT_TITLE;
		}
		if (inquiry.getTitle() != null) {
			return inquiry.getTitle().reveal();
		}
		return inquiry.getRefinementStatus() == RefinementStatus.NEEDS_ORIGINAL ? NEEDS_ORIGINAL_TITLE : PENDING_TITLE;
	}

}
