package damso.gateway.application.port.out;

import java.util.List;

/** 학교 이름과 표기 변형(1차 가명화 사전의 학교 용어, research R2). 게이트웨이 서비스만 읽는다. */
public interface SchoolTermsSource {

	List<String> terms();

}
