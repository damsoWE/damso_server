package damso.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 선언적 입력 검증 확인용 경로. 운영 코드에는 없다. */
@RestController
public class ValidationProbeController {

	@PostMapping("/test/validation")
	String validate(@Valid @RequestBody ProbeRequest request) {
		return "ok";
	}

	public record ProbeRequest(
			@NotBlank @Size(max = 2000) String content,
			@Min(1) Integer count) {
	}

}
