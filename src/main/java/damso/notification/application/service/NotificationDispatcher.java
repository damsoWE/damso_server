package damso.notification.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import damso.common.security.SessionStatusChecker;
import damso.inquiry.application.port.in.DeferredInquiryCounter;
import damso.inquiry.application.port.in.UrgentReminderPolicy;
import damso.notification.application.port.out.DevicePort;
import damso.notification.application.port.out.NotificationRequestPort;
import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.application.port.out.PushSenderPort;
import damso.notification.application.port.out.PushSenderPort.SendResult;
import damso.notification.domain.AvailabilityCalendar;
import damso.notification.domain.DeviceRegistration;
import damso.notification.domain.NotificationKind;
import damso.notification.domain.NotificationRequest;
import damso.notification.domain.PushMessage;
import damso.notification.domain.TeacherNotificationSetting;

/**
 * 알림 요청을 FCM으로 보낸다 (research R7).
 * - 긴급은 커밋 직후 {@link UrgentDispatchTrigger}가 {@link #dispatchOne}으로 즉시 보내고,
 *   {@link #dispatchDue}가 10초마다 못 보낸 요청과 일반·묶음·재알림을 보내는 안전망이다.
 * - 요청은 짧은 트랜잭션에서 임대(SENDING, 60초)한 뒤 트랜잭션 밖에서 전송한다. 느린 FCM 호출이 DB 행 잠금을 쥐지 않고,
 *   서버가 중간에 멈추면 임대가 만료되어 다른 발송기가 다시 가져간다. 같은 요청을 두 곳이 동시에 잡지 못한다(SKIP LOCKED).
 * - 받는 사람은 보낼 때의 현재 담임이다. 세션이 살아 있는 활성 기기에만 보낸다(로그아웃·탈퇴하면 끊긴다).
 * - 실패하면 1분·5분·15분 뒤 다시, 그 뒤로는 1시간 간격으로 계속 시도한다(긴급은 포기하지 않는다).
 * - 알림 내용은 고정 문구뿐이고 토큰·문구·본문은 로그에 남기지 않는다. 로그에는 요청 UUID와 상태만 남긴다.
 */
@Service
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	static final Duration LEASE = Duration.ofSeconds(60);

	static final int BATCH_SIZE = 50;

	private final NotificationRequestPort requests;

	private final DevicePort devices;

	private final PushSenderPort sender;

	private final SessionStatusChecker sessions;

	private final UrgentReminderPolicy reminders;

	private final NotificationSettingPort settings;

	private final DeferredInquiryCounter deferred;

	private final Clock clock;

	private final TransactionTemplate transaction;

	private final List<Duration> retryDelays;

	private final Duration reminderInterval;

	public NotificationDispatcher(NotificationRequestPort requests, DevicePort devices, PushSenderPort sender,
			SessionStatusChecker sessions, UrgentReminderPolicy reminders, NotificationSettingPort settings,
			DeferredInquiryCounter deferred, Clock clock, PlatformTransactionManager transactionManager,
			@Value("${damso.notification.retry-delays:1m,5m,15m}") List<Duration> retryDelays,
			@Value("${damso.notification.urgent-reminder-interval:1h}") Duration reminderInterval) {
		this.requests = requests;
		this.devices = devices;
		this.sender = sender;
		this.sessions = sessions;
		this.reminders = reminders;
		this.settings = settings;
		this.deferred = deferred;
		this.clock = clock;
		// 커밋 직후 콜백(AFTER_COMMIT)에서 불려도 이미 끝난 트랜잭션에 얹히지 않도록 항상 새 트랜잭션으로 반영한다
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.retryDelays = List.copyOf(retryDelays);
		this.reminderInterval = reminderInterval;
	}

	/** 안전망. 보낼 때가 된 요청을 모두 처리하고 처리한 개수를 돌려준다. */
	@Scheduled(fixedDelayString = "${damso.notification.dispatch-interval:10s}",
			initialDelayString = "${damso.notification.dispatch-interval:10s}")
	public int dispatchDue() {
		List<NotificationRequest> claimed;
		try {
			claimed = requests.claimDue(clock.instant(), LEASE, BATCH_SIZE);
		}
		catch (RuntimeException ex) {
			log.warn("알림 발송 대상 조회 실패 (type={})", ex.getClass().getSimpleName());
			return 0;
		}
		claimed.forEach(this::process);
		return claimed.size();
	}

	/** 긴급 문의 직후 즉시 발송. 이미 처리됐거나 다른 곳이 잡은 요청이면 아무것도 하지 않는다. */
	public void dispatchOne(UUID requestId) {
		requests.claimOne(requestId, clock.instant(), LEASE).ifPresent(this::process);
	}

	private void process(NotificationRequest request) {
		try {
			deliver(request);
		}
		catch (RuntimeException ex) {
			// 임대가 만료되면 다시 가져가 재시도한다(요청은 DB에 그대로 남는다)
			log.warn("알림 처리 중 오류 (request={}, type={})", request.getId(), ex.getClass().getSimpleName());
		}
	}

	private void deliver(NotificationRequest claimed) {
		Instant now = clock.instant();
		NotificationRequest request = claimed;
		if (request.getKind() == NotificationKind.GENERAL_DIGEST) {
			Optional<NotificationRequest> prepared = prepareDigest(request, now);
			if (prepared.isEmpty()) {
				return;
			}
			request = prepared.get();
		}
		Optional<UUID> recipient = recipientOf(request);

		if (request.getKind() == NotificationKind.URGENT_REMINDER) {
			if (recipient.isEmpty() || !reminders.shouldRemind(request.getInquiryId(), recipient.get())) {
				finish(request, List.of(), Outcome.CANCEL, now, recipient);
				return;
			}
		}
		if (recipient.isEmpty()) {
			finish(request, List.of(), Outcome.RELEASE, now, recipient);
			return;
		}

		List<DeviceRegistration> live = devices.findActiveByUsers(List.of(recipient.get())).stream()
				.filter(device -> sessions.isActive(device.getSessionId()))
				.toList();
		if (live.isEmpty()) {
			// 기기가 생길 때까지 PENDING으로 두고 다음 주기에 다시 본다 (FR-032)
			finish(request, List.of(), Outcome.RELEASE, now, recipient);
			return;
		}

		PushMessage message = messageOf(request);
		int sent = 0;
		int failed = 0;
		List<DeviceRegistration> invalid = new ArrayList<>();
		for (DeviceRegistration device : live) {
			switch (send(device, message)) {
				case SENT -> sent++;
				case INVALID_TOKEN -> invalid.add(device);
				case FAILED -> failed++;
			}
		}
		Outcome outcome = failed > 0 ? Outcome.RETRY : sent > 0 ? Outcome.SENT : Outcome.RELEASE;
		finish(request, invalid, outcome, now, recipient);
	}

	/**
	 * 묶음은 보내기 직전에 교사의 일반 알림 설정을 다시 확인한다(그사이 꺼졌으면 건너뜀). 켜져 있으면 쌓인 문의를 세어 표시하고
	 * 건수를 요청에 기록한다 — 전송이 실패해 다시 시도할 때는 이미 표시된 문의를 다시 세지 않고 기록한 건수를 쓴다.
	 * 건너뛰면 비어 있는 값을 돌려주고 요청은 SKIPPED로 닫는다.
	 */
	private Optional<NotificationRequest> prepareDigest(NotificationRequest request, Instant now) {
		if (request.getDigestCount() != null) {
			return Optional.of(request);
		}
		UUID teacherId = request.getRecipientId();
		boolean enabled = settings.findSetting(teacherId).map(TeacherNotificationSetting::isGeneralEnabled).orElse(true);
		int count = 0;
		if (enabled) {
			Integer claimedCount = transaction.execute(status -> {
				int counted = deferred.claimDeferred(teacherId, now);
				if (counted > 0) {
					NotificationRequest fresh = requests.findById(request.getId()).orElseThrow();
					fresh.recordDigestCount(counted);
					requests.save(fresh);
				}
				return counted;
			});
			count = claimedCount == null ? 0 : claimedCount;
		}
		if (count == 0) {
			finish(request, List.of(), Outcome.SKIP, now, Optional.empty());
			return Optional.empty();
		}
		return requests.findById(request.getId());
	}

	/** 묶음을 보내는 사이에 새로 쌓인 문의가 있으면 다음 시작 시각에 새 묶음을 잡는다. */
	private void scheduleFollowUpDigest(UUID teacherId, Instant now) {
		boolean enabled = settings.findSetting(teacherId).map(TeacherNotificationSetting::isGeneralEnabled).orElse(true);
		if (enabled && deferred.countDeferred(teacherId) > 0) {
			requests.createDigestIfAbsent(teacherId, AvailabilityCalendar.nextStart(settings.findWindows(teacherId), now),
					now);
		}
	}

	private SendResult send(DeviceRegistration device, PushMessage message) {
		try {
			return sender.send(device.getToken(), message);
		}
		catch (RuntimeException ex) {
			log.warn("푸시 발송 오류 (type={})", ex.getClass().getSimpleName());
			return SendResult.FAILED;
		}
	}

	private Optional<UUID> recipientOf(NotificationRequest request) {
		// 묶음·답변 알림은 "현재 담임"을 다시 찾지 않는다 — 받는 사람이 이미 정해져 있다(묶음: 교사, 답변: 학부모, F4).
		if (request.getKind() == NotificationKind.GENERAL_DIGEST || request.getKind() == NotificationKind.ANSWER) {
			return Optional.of(request.getRecipientId());
		}
		return reminders.currentRecipient(request.getInquiryId());
	}

	private PushMessage messageOf(NotificationRequest request) {
		if (request.getKind() == NotificationKind.GENERAL_DIGEST) {
			return PushMessage.digest(request.getDigestCount() == null ? 1 : request.getDigestCount());
		}
		return PushMessage.forInquiry(request.getKind(), request.getInquiryId());
	}

	private enum Outcome {
		SENT,
		RETRY,
		RELEASE,
		CANCEL,
		SKIP
	}

	/** 결과를 짧은 트랜잭션 하나로 반영한다: 무효 기기 비활성화, 요청 상태, 다음 재알림. */
	private void finish(NotificationRequest request, List<DeviceRegistration> invalid, Outcome outcome, Instant now,
			Optional<UUID> recipient) {
		transaction.executeWithoutResult(status -> {
			for (DeviceRegistration device : invalid) {
				device.deactivate();
				devices.save(device);
			}
			switch (outcome) {
				case SENT -> {
					request.markSent(now);
					if (isUrgentKind(request.getKind()) && recipient.isPresent()) {
						requests.save(NotificationRequest.urgentReminder(recipient.get(), request.getInquiryId(),
								now.plus(reminderInterval)));
					}
				}
				case RETRY -> request.retryAfter(now, delayAfter(request.getAttempts()));
				case RELEASE -> request.release();
				case CANCEL -> request.cancel();
				case SKIP -> request.skip();
			}
			requests.save(request);
		});
		if (outcome == Outcome.SENT && request.getKind() == NotificationKind.GENERAL_DIGEST) {
			scheduleFollowUpDigest(request.getRecipientId(), now);
		}
	}

	private Duration delayAfter(int previousFailures) {
		return previousFailures < retryDelays.size() ? retryDelays.get(previousFailures) : reminderInterval;
	}

	private static boolean isUrgentKind(NotificationKind kind) {
		return kind == NotificationKind.URGENT || kind == NotificationKind.URGENT_REMINDER;
	}

}
