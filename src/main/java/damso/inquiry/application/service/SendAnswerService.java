package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
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
 * 답변 작성·전송. 점검 여부·문의 단계와 무관하게 동작한다 — 일반 문의가 아직
 * TEACHER_CONFIRMED에 닿지 않아도(F3 전) 답변은 보낼 수 있다. 다만 스레드가 이미
 * "답변 완료"로 종료됐으면 거부한다(THREAD_CLOSED). 교사가 보지 못한 재문의가 있으면 거부한다(이슈 #28, NEW_FOLLOW_UP).
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
	public AnswerSentResult send(UUID teacherId, UUID inquiryId, String content, boolean reviewed,
			UUID lastSeenFollowUpId) {
		// 쓰기 잠금으로 조회한다 — 종료(CloseInquiryService)가 같은 행을 쓰는 동안 이 트랜잭션이 기다리게 해,
		// "검사(stage 확인) 뒤 사용(답변 저장)" 사이에 종료가 끼어드는 TOCTOU를 막는다.
		Inquiry inquiry = inquiries.findInquiryByIdForUpdate(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 답변할 수 없다.
				.filter(found -> found.getThreadRootId() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (inquiry.getStage() == InquiryStage.ANSWERED) {
			throw new BusinessException(ErrorCode.THREAD_CLOSED);
		}
		// 재문의 접수(SubmitFollowUpService)도 같은 루트 행을 잠그므로, 이 검사와 답변 저장 사이에 재문의가 끼어들지 못한다.
		Optional<UUID> latestFollowUp = inquiries.findLatestFollowUpId(inquiry.getId());
		if (!Objects.equals(latestFollowUp.orElse(null), lastSeenFollowUpId)) {
			throw new BusinessException(ErrorCode.NEW_FOLLOW_UP);
		}
		InquiryContentPolicy.validate(content);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Answer saved = answers.save(Answer.send(inquiry.getId(), teacherId, content, reviewed, now));
		notifications.requestAnswerNotification(inquiry.getId(), inquiry.getParentId());
		return new AnswerSentResult(saved.getId(), inquiry.getId(), saved.getSentAt());
	}

}
