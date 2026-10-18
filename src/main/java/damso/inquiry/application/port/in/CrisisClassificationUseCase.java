package damso.inquiry.application.port.in;

import java.util.UUID;

/**
 * 긴급 문의의 위기 종류 분류 결과를 받는 연결 지점. F2에서는 호출하는 곳이 없고, F3가 가명화 게이트웨이를 거친 분류 결과로 호출한다.
 * 이 서비스는 외부 AI나 알림을 호출하지 않는다 — 결과를 검증해 종류 하나만 기록한다 (LLM 출력 검증·격리).
 */
public interface CrisisClassificationUseCase {

	/**
	 * rawType이 허용값(SELF_HARM·ABUSE·OTHER_SAFETY)과 정확히 같을 때만 반영한다. 그 밖의 값(null·빈 문자열·소문자·모르는 값)은
	 * 버리고 문의는 그대로 둔다(분류 안 됨 → 기본 안내). 긴급 문의에만 적용하고 일반 문의는 무시한다.
	 * 이미 분류된 문의는 첫 값을 유지한다. 결과를 문장으로 저장하거나 학부모·교사에게 전달하지 않는다.
	 */
	void apply(UUID inquiryId, String rawType);

}
