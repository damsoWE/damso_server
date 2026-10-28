package damso.school.application.port.in;

import java.util.UUID;

/**
 * 교사 공개 프로필: 표시 이름 + 소개 문구(최대 60자). 본인 프로필만 다룬다 — 대상은 토큰의 사용자 UUID로만 정해진다.
 * 학부모가 보는 담임 프로필은 ParentChildrenUseCase를 통해 나간다.
 */
public interface TeacherProfileUseCase {

	ProfileView get(UUID teacherId);

	/** 표시 이름은 1~20자, 소개 문구는 0~60자. 범위 밖이면 InvalidInputException. */
	ProfileView update(UUID teacherId, String displayName, String introduction);

	record ProfileView(String displayName, String introduction) {
	}

}
