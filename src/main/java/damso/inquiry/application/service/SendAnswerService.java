package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.SendAnswerUseCase;
import damso.inquiry.application.port.out.AnswerPort;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.Answer;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryContentPolicy;
import damso.inquiry.domain.InquiryStage;
import damso.notification.application.port.in.NotifyAnswerUseCase;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 답변 작성·전송 (FR-001~007). 점검 여부·문의 단계와 무관하게 동작한다 — 일반 문의가 아직
 * TEACHER_CONFIRMED에 닿지 않아도(F3 전) 답변은 보낼 수 있다(research R3). 다만 스레드가 이미
 * "답변 완료"로 종료됐으면 거부한다(FR-021, THREAD_CLOSED).
 */
@Service
@Transactional
public class SendAnswerService implements SendAnswerUseCase {

	private final InquiryPort inquiries;

	private final AnswerPort answers;

	private final InquiryTargetQuery targets;

	private final NotifyAnswerUseCase notifications;

	private final Clock clock;

	public SendAnswerService(InquiryPort inquiries, AnswerPort answers, InquiryTargetQuery targets,
			NotifyAnswerUseCase notifications, Clock clock) {
		this.inquiries = inquiries;
		this.answers = answers;
		this.targets = targets;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Override
	public AnswerSentResult send(UUID teacherId, UUID inquiryId, String content, boolean reviewed) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 답변할 수 없다(research R1).
				.filter(found -> found.getThreadRootId() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (inquiry.getStage() == InquiryStage.ANSWERED) {
			throw new BusinessException(ErrorCode.THREAD_CLOSED);
		}
		InquiryContentPolicy.validate(content);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Answer saved = answers.save(Answer.send(inquiry.getId(), teacherId, content, reviewed, now));
		notifications.requestAnswerNotification(inquiry.getId(), inquiry.getParentId());
		return new AnswerSentResult(saved.getId(), inquiry.getId(), saved.getSentAt());
	}

}
