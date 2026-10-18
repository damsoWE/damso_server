package damso.school.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.crypto.CodeHasher;
import damso.common.crypto.SecureCodes;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.InviteIssueUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.InviteCodePort;
import damso.school.application.port.out.StudentPort;
import damso.school.domain.Classroom;
import damso.school.domain.InviteCode;
import damso.school.domain.InviteKind;
import damso.school.domain.Student;

@Service
@Transactional
public class InviteIssueService implements InviteIssueUseCase {

	private static final int MAX_GENERATION_ATTEMPTS = 5;

	private final ClassroomPort classrooms;

	private final StudentPort students;

	private final InviteCodePort invites;

	private final CodeHasher hasher;

	private final Clock clock;

	public InviteIssueService(ClassroomPort classrooms, StudentPort students, InviteCodePort invites,
			CodeHasher hasher, Clock clock) {
		this.classrooms = classrooms;
		this.students = students;
		this.invites = invites;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Override
	public IssuedInvite issueTeacherInvite(UUID classroomId, UUID adminId) {
		Classroom classroom = classrooms.findClassroomById(classroomId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (classroom.hasTeacher()) {
			throw new BusinessException(ErrorCode.CLASSROOM_HAS_TEACHER);
		}
		invites.revokeActiveByTargetKey(InviteCode.activeTargetKey(InviteKind.TEACHER, classroomId));
		return issue(hash -> InviteCode.forTeacher(classroom, hash, adminId, clock.instant()));
	}

	@Override
	public IssuedInvite issueParentInvite(UUID studentId, UUID adminId) {
		Student student = students.findStudentById(studentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (student.hasGuardian()) {
			throw new BusinessException(ErrorCode.STUDENT_HAS_GUARDIAN);
		}
		invites.revokeActiveByTargetKey(InviteCode.activeTargetKey(InviteKind.PARENT, studentId));
		return issue(hash -> InviteCode.forParent(student, hash, adminId, clock.instant()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<InviteStatusView> listInvites(UUID classroomId) {
		classrooms.findClassroomById(classroomId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		Map<UUID, Student> studentsById = students.findStudentsByClassroom(classroomId).stream()
				.collect(Collectors.toMap(Student::getId, Function.identity()));
		Instant now = clock.instant();
		return invites.findInvitesForClassroom(classroomId, studentsById.keySet()).stream()
				.map(code -> {
					Student student = code.getStudentId() == null ? null : studentsById.get(code.getStudentId());
					return new InviteStatusView(code.getId(), code.getKind(), code.getStudentId(),
							student == null ? null : student.getName().reveal(), code.displayStatus(now),
							code.getCreatedAt(), code.getExpiresAt(), code.getUsedAt());
				})
				.toList();
	}

	/** 원문을 만들고 해시만 저장한다. 해시가 겹치면(사실상 없음) 다시 만든다. 원문은 반환값으로만 나간다. */
	private IssuedInvite issue(Function<String, InviteCode> factory) {
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			String raw = SecureCodes.inviteCode();
			String hash = hasher.hash(raw);
			if (invites.existsInviteByCodeHash(hash)) {
				continue;
			}
			InviteCode saved = invites.saveInvite(factory.apply(hash));
			return new IssuedInvite(saved.getId(), raw, saved.getExpiresAt());
		}
		throw new BusinessException(ErrorCode.INTERNAL_ERROR);
	}

}
