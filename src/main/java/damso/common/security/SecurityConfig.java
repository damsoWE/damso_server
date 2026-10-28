package damso.common.security;

import java.util.Arrays;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 접근 통제: 기본 거부, 헤더 토큰만, 세션·쿠키 없음.
 * CSRF 보호를 끄는 이유: 쿠키를 쓰지 않아 브라우저가 자격증명을 자동 첨부할 경로가 없다.
 */
@Configuration
public class SecurityConfig {

	/** 인증 없이 허용되는 경로. 기능에서 공개 경로가 필요하면 여기에 명시적으로 추가한다. */
	static final String[] PUBLIC_PATHS = {
			"/actuator/health",
			// 로그인 전 단계만 하나씩 명시한다 (와일드카드 금지, 로그아웃은 인증 필요)
			"/api/v1/auth/invite-codes/verify",
			"/api/v1/auth/signup",
			"/api/v1/auth/signup/verify",
			"/api/v1/auth/signup/resend",
			"/api/v1/auth/login",
			"/api/v1/auth/token/refresh",
			"/api/v1/auth/password-reset",
			"/api/v1/auth/password-reset/verify",
			"/api/v1/auth/password-reset/confirm" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			JsonAuthenticationEntryPoint authenticationEntryPoint,
			JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// 오류 디스패치는 원래 응답 코드를 유지한다 (인증된 요청의 500이 401로 바뀌지 않도록)
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(PUBLIC_PATHS).permitAll()
				// 역할별 경로. 건별 소유권은 서비스에서 한 번 더 검증한다
				.requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
				.requestMatchers("/api/v1/teacher/**").hasRole("TEACHER")
				.requestMatchers("/api/v1/parent/**").hasRole("PARENT")
				.anyRequest().authenticated())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.oauth2ResourceServer(oauth2 -> oauth2
				.bearerTokenResolver(publicPathAwareTokenResolver())
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
		return http.build();
	}

	/** 공개 경로에서는 토큰을 읽지 않는다 — 잘못된 토큰이 붙어도 공개 경로는 정상 처리 (spec Edge Case). */
	private static BearerTokenResolver publicPathAwareTokenResolver() {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		RequestMatcher publicPaths = new OrRequestMatcher(Arrays.stream(PUBLIC_PATHS)
				.map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path))
				.toList());
		return request -> publicPaths.matches(request) ? null : delegate.resolve(request);
	}

}
