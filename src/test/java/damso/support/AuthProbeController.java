package damso.support;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 인증·인가 결과 확인용 경로. 운영 코드에는 없다. */
@RestController
@RequestMapping("/test/auth")
public class AuthProbeController {

	@GetMapping("/any")
	String any() {
		return "ok";
	}

	@GetMapping("/teacher-only")
	String teacherOnly(Authentication authentication) {
		boolean teacher = authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()));
		if (!teacher) {
			throw new AccessDeniedException("teacher only");
		}
		return "ok";
	}

}
