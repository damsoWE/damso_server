package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** 스레드 타임라인에 포함되는 답변 하나. 학부모·교사 조회에서 공용으로 쓴다. */
public record AnswerView(UUID answerId, String content, boolean reviewed, Instant sentAt) {

	/** 답변 본문이 로그·예외에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "AnswerView[answerId=" + answerId + "]";
	}

}
