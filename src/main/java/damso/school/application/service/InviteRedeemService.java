package damso.school.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.crypto.CodeHasher;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.InviteRedeemUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.InviteCodePort;
import damso.school.application.port.out.LinkHistoryPort;
import damso.school.application.port.out.StudentPort;
import damso.school.application.port.out.TeacherProfilePort;
import damso.school.domain.Classroom;
import damso.school.domain.InviteCode;
import damso.school.domain.InviteKind;
import damso.school.domain.LinkHistory;
import damso.school.domain.Student;
import damso.school.domain.TeacherProfile;

@Service
@Transactional
public class InviteRedeemService implements InviteRedeemUseCase {

	private final InviteCodePort invites;

	private final ClassroomPort classrooms;

	private final StudentPort students;

	private final TeacherProfilePort profiles;

	private final LinkHistoryPort histories;

	private final CodeHasher hasher;

	private final Clock clock;

	public InviteRedeemService(InviteCodePort invites, ClassroomPort classrooms, StudentPort students,
			TeacherProfilePort profiles, LinkHistoryPort histories, CodeHasher hasher, Clock clock) {
		this.invites = invites;
		this.classrooms = classrooms;
		this.students = students;
		this.profiles = profiles;
		this.histories = histories;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public InviteTarget validate(String rawCode) {
		InviteCode code = invites.findInviteByCodeHash(hasher.hash(normalize(rawCode)))
				.orElseThrow(InviteRedeemService::invalid);
		return requireUsable(code, clock.instant());
	}

	@Override
	@Transactional(readOnly = true)
	public InviteTarget resolve(UUID inviteCodeId) {
		InviteCode code = invites.findInviteById(inviteCodeId).orElseThrow(InviteRedeemService::invalid);
		return requireUsable(code, clock.instant());
	}

	@Override
	public void redeem(UUID inviteCodeId, UUID accountId, String accountName) {
		Instant now = clock.instant();
		InviteCode code = invites.findInviteById(inviteCodeId).orElseThrow(InviteRedeemService::invalid);
		requireUsable(code, now);
		try {
			code.markUsed(accountId, now);
			invites.saveInvite(code);
			if (code.getKind() == InviteKind.TEACHER) {
				linkTeacher(code, accountId, accountName, now);
			}
			else {
				linkParent(code, accountId, now);
			}
		}
		catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrentUse) {
			// 같은 코드를 동시에 쓴 다른 요청이 먼저 확정했다 — 한 건만 성공한다 (SC-008)
			throw invalid();
		}
	}

	@Override
	public UUID redeemForExistingParent(String rawCode, UUID parentId) {
		Instant now = clock.instant();
		InviteCode code = invites.findInviteByCodeHash(hasher.hash(normalize(rawCode)))
				.orElseThrow(InviteRedeemService::invalid);
		InviteTarget target = requireUsable(code, now);
		if (target.kind() != InviteKind.PARENT) {
			throw invalid();
		}
		try {
			code.markUsed(parentId, now);
			invites.saveInvite(code);
			linkParent(code, parentId, now);
		}
		catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrentUse) {
			throw invalid();
		}
		return code.getStudentId();
	}

	private void linkTeacher(InviteCode code, UUID teacherId, String displayName, Instant now) {
		Classroom classroom = classrooms.findClassroomById(code.getClassroomId()).orElseThrow(InviteRedeemService::invalid);
		classroom.assignTeacher(teacherId);
		classrooms.saveClassroom(classroom);
		profiles.saveProfile(new TeacherProfile(teacherId, displayName));
		histories.append(LinkHistory.linkedToClassroom(teacherId, classroom.getId(), now));
	}

	private void linkParent(InviteCode code, UUID parentId, Instant now) {
		Student student = students.findStudentById(code.getStudentId()).orElseThrow(InviteRedeemService::invalid);
		student.assignGuardian(parentId);
		students.saveStudent(student);
		histories.append(LinkHistory.linkedToStudent(parentId, student.getId(), now));
	}

	/** 코드가 쓸 수 있는 상태이고 연결 대상이 아직 비어 있어야 한다. 아니면 사유 구분 없이 INVALID_INVITE_CODE. */
	private InviteTarget requireUsable(InviteCode code, Instant now) {
		if (!code.isUsable(now)) {
			throw invalid();
		}
		boolean targetFree = switch (code.getKind()) {
			case TEACHER -> classrooms.findClassroomById(code.getClassroomId())
					.map(classroom -> !classroom.hasTeacher()).orElse(false);
			case PARENT -> students.findStudentById(code.getStudentId())
					.map(student -> !student.hasGuardian()).orElse(false);
		};
		if (!targetFree) {
			throw invalid();
		}
		return new InviteTarget(code.getId(), code.getKind(), code.targetId());
	}

	/** 소문자·공백 입력을 대문자로 맞춘다. */
	static String normalize(String rawCode) {
		return rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
	}

	private static BusinessException invalid() {
		return new BusinessException(ErrorCode.INVALID_INVITE_CODE);
	}

}
