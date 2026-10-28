package damso.school.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.school.domain.InviteCode;
import damso.school.domain.InviteKind;

/** 초대 코드 발행·재발행·발행 현황. 코드 원문은 발행 결과에만 있다. */
public interface InviteIssueUseCase {

	/** 담임이 이미 있으면 CLASSROOM_HAS_TEACHER, 없는 학급이면 NOT_FOUND. 이전 미사용 코드는 즉시 무효. */
	IssuedInvite issueTeacherInvite(UUID classroomId, UUID adminId);

	/** 보호자가 이미 있으면 STUDENT_HAS_GUARDIAN, 없는 학생이면 NOT_FOUND. 이전 미사용 코드는 즉시 무효. */
	IssuedInvite issueParentInvite(UUID studentId, UUID adminId);

	/** 학급의 교사 코드와 그 학급 학생들의 학부모 코드 현황. 원문은 없다. 없는 학급이면 NOT_FOUND. */
	List<InviteStatusView> listInvites(UUID classroomId);

	/** code 원문은 이 결과에서만 보이므로 toString에서 가린다. */
	record IssuedInvite(UUID inviteCodeId, String code, Instant expiresAt) {

		@Override
		public String toString() {
			return "IssuedInvite[inviteCodeId=" + inviteCodeId + "]";
		}

	}

	record InviteStatusView(
			UUID inviteCodeId,
			InviteKind kind,
			UUID studentId,
			String studentName,
			InviteCode.DisplayStatus status,
			Instant issuedAt,
			Instant expiresAt,
			Instant usedAt) {
	}

}
