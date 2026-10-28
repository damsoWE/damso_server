package damso.inquiry.domain;

import damso.common.error.InvalidInputException;

/**
 * 문의 본문 규칙: 공백만 있으면 안 되고, 글자 수는 코드 포인트 기준 2,000자 이하다.
 * UTF-16 길이로 세면 이모지가 2자로 계산되므로 서버가 코드 포인트로 다시 센다.
 * 사유 문구는 고정이며 입력값을 담지 않는다.
 */
public final class InquiryContentPolicy {

	public static final int MAX_CODE_POINTS = 2000;

	private InquiryContentPolicy() {
	}

	public static void validate(String content) {
		if (content == null || content.isBlank()) {
			throw new InvalidInputException("content", "필수 값입니다");
		}
		if (content.codePointCount(0, content.length()) > MAX_CODE_POINTS) {
			throw new InvalidInputException("content", "최대 " + MAX_CODE_POINTS + "자입니다");
		}
	}

}
