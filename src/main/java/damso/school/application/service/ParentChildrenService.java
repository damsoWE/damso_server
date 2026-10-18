package damso.school.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.InviteRedeemUseCase;
import damso.school.application.port.in.ParentChildrenUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.application.port.out.TeacherProfilePort;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.school.domain.TeacherProfile;

@Service
@Transactional
public class ParentChildrenService implements ParentChildrenUseCase {

	private final StudentPort students;

	private final ClassroomPort classrooms;

	private final TeacherProfilePort profiles;

	private final InviteRedeemUseCase redeem;

	private final AttemptLimitUseCase attempts;

	public ParentChildrenService(StudentPort students, ClassroomPort classrooms, TeacherProfilePort profiles,
			InviteRedeemUseCase redeem, AttemptLimitUseCase attempts) {
		this.students = students;
		this.classrooms = classrooms;
		this.profiles = profiles;
		this.redeem = redeem;
		this.attempts = attempts;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChildView> list(UUID parentId) {
		return toViews(students.findStudentsByGuardian(parentId));
	}

	@Override
	public ChildView link(UUID parentId, String rawInviteCode) {
		String subject = parentId.toString();
		attempts.ensureAllowed(AttemptKind.INVITE_CODE, subject);
		UUID studentId;
		try {
			studentId = redeem.redeemForExistingParent(rawInviteCode, parentId);
		}
		catch (BusinessException invalidCode) {
			if (invalidCode.errorCode() == ErrorCode.INVALID_INVITE_CODE) {
				attempts.recordFailure(AttemptKind.INVITE_CODE, subject);
			}
			throw invalidCode;
		}
		attempts.reset(AttemptKind.INVITE_CODE, subject);
		Student student = students.findStudentById(studentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
		return toViews(List.of(student)).get(0);
	}

	/** 학생들의 학급과 담임 공개 프로필을 한꺼번에 모아 화면용으로 바꾼다. */
	private List<ChildView> toViews(List<Student> children) {
		Set<UUID> classroomIds = children.stream().map(Student::getClassroomId).collect(Collectors.toSet());
		Map<UUID, Classroom> classroomsById = classroomIds.stream()
				.map(classrooms::findClassroomById)
				.flatMap(java.util.Optional::stream)
				.collect(Collectors.toMap(Classroom::getId, Function.identity()));
		Set<UUID> teacherIds = classroomsById.values().stream()
				.map(Classroom::getHomeroomTeacherId)
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toSet());
		Map<UUID, TeacherProfile> profilesByTeacher = profiles.findProfilesByTeachers(teacherIds).stream()
				.collect(Collectors.toMap(TeacherProfile::getTeacherId, Function.identity()));

		return children.stream().map(student -> {
			Classroom classroom = classroomsById.get(student.getClassroomId());
			TeacherProfile profile = classroom.getHomeroomTeacherId() == null ? null
					: profilesByTeacher.get(classroom.getHomeroomTeacherId());
			return new ChildView(student.getId(), student.getName().reveal(),
					new ClassroomRef(classroom.getId(), classroom.getGrade(), classroom.getClassNumber()),
					profile == null ? null : new TeacherProfileView(profile.getDisplayName(), profile.getIntroduction()));
		}).toList();
	}

}
