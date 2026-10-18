package damso.school.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.InviteCodePort;
import damso.school.application.port.out.LinkHistoryPort;
import damso.school.application.port.out.StudentPort;
import damso.school.application.port.out.TeacherProfilePort;
import damso.school.domain.Classroom;
import damso.school.domain.InviteCode;
import damso.school.domain.InviteStatus;
import damso.school.domain.LinkHistory;
import damso.school.domain.Student;
import damso.school.domain.TeacherProfile;

/** school 도메인의 출력 포트를 Spring Data JPA로 구현한다. */
@Component
class SchoolPersistenceAdapter
		implements ClassroomPort, StudentPort, InviteCodePort, TeacherProfilePort, LinkHistoryPort {

	private final SpringDataClassroomRepository classrooms;

	private final SpringDataStudentRepository students;

	private final SpringDataInviteCodeRepository invites;

	private final SpringDataTeacherProfileRepository profiles;

	private final LinkHistoryRepository histories;

	SchoolPersistenceAdapter(SpringDataClassroomRepository classrooms, SpringDataStudentRepository students,
			SpringDataInviteCodeRepository invites, SpringDataTeacherProfileRepository profiles,
			LinkHistoryRepository histories) {
		this.classrooms = classrooms;
		this.students = students;
		this.invites = invites;
		this.profiles = profiles;
		this.histories = histories;
	}

	// ClassroomPort

	@Override
	public Classroom saveClassroom(Classroom classroom) {
		return classrooms.saveAndFlush(classroom);
	}

	@Override
	public Optional<Classroom> findClassroomById(UUID id) {
		return classrooms.findById(id);
	}

	@Override
	public boolean existsClassroom(int grade, int classNumber) {
		return classrooms.existsByGradeAndClassNumber(grade, classNumber);
	}

	@Override
	public List<Classroom> findAllClassrooms() {
		return classrooms.findAllByOrderByGradeAscClassNumberAsc();
	}

	@Override
	public Optional<Classroom> findClassroomByHomeroomTeacher(UUID teacherId) {
		return classrooms.findByHomeroomTeacherId(teacherId);
	}

	// StudentPort

	@Override
	public Student saveStudent(Student student) {
		return students.save(student);
	}

	@Override
	public List<Student> saveStudents(List<Student> toSave) {
		return students.saveAll(toSave);
	}

	@Override
	public Optional<Student> findStudentById(UUID id) {
		return students.findById(id);
	}

	@Override
	public List<Student> findStudentsByClassroom(UUID classroomId) {
		return students.findByClassroomId(classroomId).stream()
				.sorted(Comparator.comparing(Student::getCreatedAt))
				.toList();
	}

	@Override
	public Map<UUID, Long> countStudentsByClassroom() {
		Map<UUID, Long> counts = new HashMap<>();
		for (Object[] row : students.countGroupedByClassroom()) {
			counts.put((UUID) row[0], (Long) row[1]);
		}
		return counts;
	}

	@Override
	public List<Student> findStudentsByGuardian(UUID guardianId) {
		return students.findByGuardianId(guardianId);
	}

	@Override
	public List<Student> findStudentsByIds(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : students.findAllById(ids);
	}

	// InviteCodePort

	@Override
	public InviteCode saveInvite(InviteCode code) {
		return invites.saveAndFlush(code);
	}

	@Override
	public Optional<InviteCode> findInviteById(UUID id) {
		return invites.findById(id);
	}

	@Override
	public Optional<InviteCode> findInviteByCodeHash(String codeHash) {
		return invites.findByCodeHash(codeHash);
	}

	@Override
	public boolean existsInviteByCodeHash(String codeHash) {
		return invites.existsByCodeHash(codeHash);
	}

	@Override
	public int revokeActiveByTargetKey(String activeTargetKey) {
		return invites.revokeActive(activeTargetKey, InviteStatus.REVOKED);
	}

	@Override
	public List<InviteCode> findInvitesForClassroom(UUID classroomId, Collection<UUID> studentIds) {
		List<InviteCode> result = new ArrayList<>(invites.findByClassroomIdOrderByCreatedAtAsc(classroomId));
		if (!studentIds.isEmpty()) {
			result.addAll(invites.findByStudentIdInOrderByCreatedAtAsc(studentIds));
		}
		result.sort(Comparator.comparing(InviteCode::getCreatedAt));
		return result;
	}

	// TeacherProfilePort

	@Override
	public TeacherProfile saveProfile(TeacherProfile profile) {
		return profiles.save(profile);
	}

	@Override
	public Optional<TeacherProfile> findProfileByTeacher(UUID teacherId) {
		return profiles.findByTeacherId(teacherId);
	}

	@Override
	public List<TeacherProfile> findProfilesByTeachers(Collection<UUID> teacherIds) {
		return teacherIds.isEmpty() ? List.of() : profiles.findByTeacherIdIn(teacherIds);
	}

	@Override
	public void deleteProfileByTeacher(UUID teacherId) {
		profiles.deleteByTeacher(teacherId);
	}

	// LinkHistoryPort

	@Override
	public void append(LinkHistory history) {
		histories.save(history);
	}

	@Override
	public List<LinkHistory> findByAccount(UUID accountId) {
		return histories.findByAccountIdOrderByOccurredAt(accountId);
	}

}
