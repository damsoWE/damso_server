package damso.common.config;

import java.io.IOException;

/** 요청 본문을 읽는 중 최대 크기를 넘었을 때. 스트림 읽기에서 발생하므로 IOException이다. */
public class PayloadTooLargeException extends IOException {

	public PayloadTooLargeException() {
		super("request body exceeds limit");
	}

}
