package damso.school.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.school.domain.InviteCode;

public interface InviteCodePort {

	/** 저장하고 바로 반영(flush)한다 — 동시에 같은 코드를 쓰는 요청의 낙관적 락 충돌을 호출한 곳에서 잡기 위해서다. */
	InviteCode saveInvite(InviteCode code);

	Optional<InviteCode> findInviteById(UUID id);

	Optional<InviteCode> findInviteByCodeHash(String codeHash);

	boolean existsInviteByCodeHash(String codeHash);

	/**
	 * 같은 대상의 활성(ISSUED) 코드를 즉시 무효로 바꾸고 바꾼 수를 돌려준다.
	 * 벌크 UPDATE라 바로 실행되어, 뒤이어 같은 대상의 새 코드를 저장해도 유니크 충돌이 없다.
	 */
	int revokeActiveByTargetKey(String activeTargetKey);

	/** 학급의 교사 코드와 그 학급 학생들의 학부모 코드를 발행 순서로. */
	List<InviteCode> findInvitesForClassroom(UUID classroomId, Collection<UUID> studentIds);

}
