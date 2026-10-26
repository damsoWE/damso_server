package damso.inquiry.application.service;

import org.springframework.stereotype.Component;

import damso.inquiry.application.port.out.ThreatExpressionSource;
import damso.inquiry.domain.ThreatRule;

/**
 * 접수 때 일반 문의의 위협·폭언 표시를 정한다(spec 004 research R7). 예외 처리는 호출하는 쪽(SubmitInquiryService)이 한다 —
 * 판정이 실패해도 접수는 막히지 않고 표시 없이 진행된다. 테스트가 실패 상황을 끼워 넣을 수 있도록 final로 두지 않는다.
 */
@Component
public class ThreatFlagScreener {

	private final ThreatRule rule;

	public ThreatFlagScreener(ThreatExpressionSource expressions) {
		this.rule = new ThreatRule(expressions.expressions());
	}

	public boolean screen(String content) {
		return rule.isFlagged(content);
	}

}
