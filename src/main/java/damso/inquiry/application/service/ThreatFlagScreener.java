package damso.inquiry.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import damso.inquiry.application.port.out.ThreatExpressionSource;
import damso.inquiry.domain.ThreatRule;

/**
 * 접수 때 일반 문의(최초 문의·재문의)의 위협·폭언 표시를 정한다(spec 005 research R7). 접수 서비스는 screenSafely만 부른다 —
 * 판정이 실패해도 접수는 막히지 않고 표시 없이 진행된다. screen은 protected라 다른 서비스가 직접 불러 이 보호를 건너뛸 수 없다.
 * 테스트가 screen을 바꿔 끼워 실패 상황을 만들 수 있도록 final로 두지 않는다.
 */
@Component
public class ThreatFlagScreener {

	private static final Logger log = LoggerFactory.getLogger(ThreatFlagScreener.class);

	private final ThreatRule rule;

	public ThreatFlagScreener(ThreatExpressionSource expressions) {
		this.rule = new ThreatRule(expressions.expressions());
	}

	protected boolean screen(String content) {
		return rule.isFlagged(content);
	}

	/**
	 * 판정이 실패하면 표시 없이 접수를 계속한다(spec 005 FR-005b, 오탐 최소화). 표시 기능이 학부모의 문의권을 막으면 안 된다.
	 * 예외 메시지에 본문이 섞일 수 있으므로 예외는 로그에 넘기지 않는다(원칙 VI).
	 */
	public boolean screenSafely(String content) {
		try {
			return screen(content);
		}
		catch (RuntimeException ex) {
			log.warn("위협·폭언 판정에 실패해 표시 없이 접수합니다");
			return false;
		}
	}

}
