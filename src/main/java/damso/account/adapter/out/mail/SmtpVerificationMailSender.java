package damso.account.adapter.out.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import damso.account.application.port.out.VerificationMailPort;

/**
 * SMTP(JavaMailSender)로 인증 메일을 보낸다 (research R9).
 * 호출은 이벤트만 발행하고, 실제 발송은 트랜잭션 커밋 후 별도 스레드에서 한다(트랜잭션이 없으면 바로).
 * 본문은 고정 템플릿 — 코드 6자리 외에 이름·학생 정보를 넣지 않는다.
 * 발송 실패 로그에는 메일 종류와 예외 종류만 남긴다(이메일 주소·코드 미기록, 원칙 VI).
 */
@Component
class SmtpVerificationMailSender implements VerificationMailPort {

	private static final Logger log = LoggerFactory.getLogger(SmtpVerificationMailSender.class);

	private final ApplicationEventPublisher events;

	private final JavaMailSender mailSender;

	private final String from;

	SmtpVerificationMailSender(ApplicationEventPublisher events, JavaMailSender mailSender,
			@Value("${damso.mail.from}") String from) {
		this.events = events;
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void sendSignupCode(String email, String code) {
		events.publishEvent(new MailRequest(Kind.SIGNUP_CODE, email, code));
	}

	@Override
	public void sendAlreadyRegistered(String email) {
		events.publishEvent(new MailRequest(Kind.ALREADY_REGISTERED, email, null));
	}

	@Override
	public void sendPasswordResetCode(String email, String code) {
		events.publishEvent(new MailRequest(Kind.PASSWORD_RESET_CODE, email, code));
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void deliver(MailRequest request) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(from);
			message.setTo(request.email());
			message.setSubject(request.kind().subject);
			message.setText(request.kind().body(request.code()));
			mailSender.send(message);
		}
		catch (RuntimeException ex) {
			log.warn("verification mail failed: kind={}, cause={}", request.kind(), ex.getClass().getSimpleName());
		}
	}

	enum Kind {

		SIGNUP_CODE("[담소] 가입 인증 코드",
				"가입 인증 코드: %s\n\n10분 안에 앱에 입력해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시해 주세요."),
		PASSWORD_RESET_CODE("[담소] 비밀번호 재설정 코드",
				"비밀번호 재설정 코드: %s\n\n10분 안에 앱에 입력해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시해 주세요."),
		ALREADY_REGISTERED("[담소] 이미 가입된 계정입니다",
				"이 이메일로 이미 가입된 계정이 있습니다.\n로그인하거나, 비밀번호가 기억나지 않으면 앱에서 비밀번호 재설정을 이용해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.");

		private final String subject;

		private final String template;

		Kind(String subject, String template) {
			this.subject = subject;
			this.template = template;
		}

		String body(String code) {
			return code == null ? template : template.formatted(code);
		}

	}

	/** 이메일·코드를 담으므로 로그에 찍히지 않게 toString을 가린다. */
	record MailRequest(Kind kind, String email, String code) {

		@Override
		public String toString() {
			return "MailRequest[kind=" + kind + "]";
		}

	}

}
