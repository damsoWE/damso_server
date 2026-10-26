package damso.inquiry.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.account.application.port.in.UserNameQuery;
import damso.gateway.domain.Roster;
import damso.school.application.port.in.RosterQuery;
import damso.school.application.port.in.RosterQuery.ClassRoster;
import damso.school.application.port.in.RosterQuery.RosterStudent;

/**
 * 가명화 사전 재료를 모은다 (F3 research R2). 문의의 접수 시점 학급을 기준으로 학생·보호자·담임 이름과 학년·반을 school·account의
 * port.in으로 읽는다. 학교 용어는 넣지 않는다 — 게이트웨이가 자기 리소스에서 채운다(inquiry는 게이트웨이의 port.out을 모른다).
 * 이름은 이 객체 밖으로 로그에 나가지 않는다(Roster.toString은 건수만).
 */
@Component
class RosterAssembler {

	private final RosterQuery rosters;

	private final UserNameQuery names;

	RosterAssembler(RosterQuery rosters, UserNameQuery names) {
		this.rosters = rosters;
		this.names = names;
	}

	/**
	 * @param alsoMask 학급 명단 밖이지만 함께 가릴 계정(문의를 쓴 학부모, 접수 시점 담임 등)
	 */
	Roster assemble(UUID classroomId, Collection<UUID> alsoMask) {
		ClassRoster roster = rosters.rosterOf(classroomId);
		Set<UUID> guardianIds = new LinkedHashSet<>();
		roster.students().stream().map(RosterStudent::guardianId).filter(Objects::nonNull).forEach(guardianIds::add);
		Set<UUID> teacherIds = new LinkedHashSet<>();
		if (roster.homeroomTeacherId() != null) {
			teacherIds.add(roster.homeroomTeacherId());
		}
		Set<UUID> extra = new LinkedHashSet<>(alsoMask);
		extra.removeAll(guardianIds);
		extra.removeAll(teacherIds);

		Set<UUID> lookup = new LinkedHashSet<>(guardianIds);
		lookup.addAll(teacherIds);
		lookup.addAll(extra);
		Map<UUID, String> found = names.namesOf(lookup);

		List<String> guardianNames = new ArrayList<>(namesFor(guardianIds, found));
		guardianNames.addAll(namesFor(extra, found));
		List<String> teacherNames = new ArrayList<>(namesFor(teacherIds, found));
		if (roster.teacherDisplayName() != null) {
			teacherNames.add(roster.teacherDisplayName());
		}
		return Roster.of(roster.students().stream().map(RosterStudent::name).toList(), guardianNames,
				teacherNames.stream().distinct().toList(), roster.grade(), roster.classNumber());
	}

	private static List<String> namesFor(Collection<UUID> ids, Map<UUID, String> found) {
		return ids.stream().map(found::get).filter(Objects::nonNull).toList();
	}

}
