package damso.inquiry.application.port.out;

import java.util.List;

/** 위협·폭언 판정에 쓰는 표현 목록(spec 005). 비어 있지 않음을 구현이 보장한다(못 읽거나 비면 서버가 기동하지 않는다). */
public interface ThreatExpressionSource {

	List<String> expressions();

}
