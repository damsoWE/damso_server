package damso.account.application.port.in;

import damso.account.application.service.TokenResult;

/**
 * 세션 갱신: 갱신 토큰을 1회 회전하고 세션 만료를 14일 연장한다.
 * 이미 쓰인 토큰이 다시 오면 그 세션(기기)만 폐기한다. 없음·만료·폐기·재사용 모두 UNAUTHORIZED다(사유 구분 없음).
 */
public interface RefreshUseCase {

	TokenResult refresh(String refreshToken);

}
