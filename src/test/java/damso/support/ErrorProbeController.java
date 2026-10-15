package damso.support;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import damso.common.crypto.SensitiveText;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

/** 테스트 전용: 여러 종류의 오류를 일부러 일으킨다. 운영 코드에는 없다. */
@RestController
@RequestMapping("/test/errors")
public class ErrorProbeController {

	@GetMapping("/runtime")
	String runtime() {
		throw new RuntimeException("unexpected failure");
	}

	@GetMapping("/leaky")
	String leaky() {
		throw new IllegalStateException("failed at /home/app/secret.java while running SELECT * FROM users");
	}

	@GetMapping("/not-found")
	String notFound() {
		throw new BusinessException(ErrorCode.NOT_FOUND);
	}

	@GetMapping("/conflict")
	String conflict() {
		throw new OptimisticLockingFailureException("row was updated by another transaction");
	}

	@GetMapping("/get-only")
	String getOnly() {
		return "ok";
	}

	@PostMapping(path = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
	String body(@RequestBody ProbeBody body) {
		throw new RuntimeException("probe failure");
	}

	/** 개발자가 민감 값을 예외 메시지에 넣는 실수를 흉내 낸다 — SensitiveText라면 [MASKED]로만 남아야 한다. */
	@PostMapping(path = "/sensitive-body", consumes = MediaType.APPLICATION_JSON_VALUE)
	String sensitiveBody(@RequestBody ProbeBody body) {
		throw new IllegalStateException("failed to handle " + SensitiveText.of(body.content()));
	}

	public record ProbeBody(String content) {
	}

}
