package damso.support;

import java.util.concurrent.atomic.AtomicInteger;

import damso.inquiry.application.port.out.ThreatExpressionSource;
import damso.inquiry.application.service.ThreatFlagScreener;

/**
 * 실제 위협·폭언 판정을 그대로 하되, 테스트가 판정 실패를 끼워 넣고 호출 횟수를 셀 수 있다(spec 005).
 * 공용 설정(TestSupportConfig)에 두어 테스트 클래스마다 컨텍스트가 늘지 않게 한다. 테스트마다 reset()으로 되돌린다.
 */
public class FailableThreatFlagScreener extends ThreatFlagScreener {

	private final AtomicInteger calls = new AtomicInteger();

	private volatile boolean failing;

	public FailableThreatFlagScreener(ThreatExpressionSource expressions) {
		super(expressions);
	}

	@Override
	public boolean screen(String content) {
		calls.incrementAndGet();
		if (failing) {
			// 예외 메시지에 본문을 일부러 넣는다 — 로그에 남지 않아야 한다
			throw new IllegalStateException("screening failed for " + content);
		}
		return super.screen(content);
	}

	public void failNext(boolean failing) {
		this.failing = failing;
	}

	public int calls() {
		return calls.get();
	}

	public void reset() {
		failing = false;
		calls.set(0);
	}

}
