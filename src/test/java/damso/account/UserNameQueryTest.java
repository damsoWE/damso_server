package damso.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import damso.account.application.port.in.UserNameQuery;
import damso.account.domain.UserAccount;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/** 가명화 사전의 보호자·교사 이름. 없는 계정·탈퇴한 계정은 결과에 없다. */
class UserNameQueryTest extends IntegrationTestSupport {

	@Autowired
	private UserNameQuery query;

	@Test
	void returnsNamesOfActiveAccountsOnly() throws Exception {
		Classroom classroom = newClassroom();
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");
		SignedUp parent = signUpWith(issueParentCode(newStudent(classroom, "김민준")), "박부모");
		UUID unknown = UUID.randomUUID();
		UserAccount withdrawn = accountPort.findById(parent.userId()).orElseThrow();

		Map<UUID, String> before = query.namesOf(List.of(teacher.userId(), parent.userId(), unknown));
		withdrawn.withdraw(clock.instant());
		accountPort.save(withdrawn);
		Map<UUID, String> after = query.namesOf(List.of(teacher.userId(), parent.userId()));

		assertThat(before).containsEntry(teacher.userId(), "최선생").containsEntry(parent.userId(), "박부모")
				.doesNotContainKey(unknown);
		assertThat(after).containsOnlyKeys(teacher.userId());
		assertThat(query.namesOf(List.of())).isEmpty();
	}

}
