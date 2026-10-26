package damso.gateway.application.service;

import java.util.Optional;

import damso.gateway.domain.PseudonymMap;
import damso.gateway.domain.RefinementResult.Valid;

/** 테스트 전용: 게이트웨이의 JSON 읽기·검증(package-private)을 다른 패키지의 테스트가 부르게 한다. 운영 코드의 공개 범위는 그대로다. */
public final class RefineInquiryGatewayServiceParseProbe {

	private RefineInquiryGatewayServiceParseProbe() {
	}

	public static Optional<Valid> parse(String json, PseudonymMap map) {
		return RefineInquiryGatewayService.parse(json, map);
	}

}
