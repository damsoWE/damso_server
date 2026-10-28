package damso.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditLog;
import damso.support.IntegrationTestSupport;

/**
 * Hibernate는 enum(STRING) 컬럼에 허용값 CHECK 제약을 만든다. 운영 DB는 ddl-auto=update라
 * 이미 있는 제약을 넓히지 않으면 새 AuditAction 값 INSERT가 실패한다. 이 테스트는 그 동작을 실제로 확인해 둔다.
 * 제약을 F2 시점(값 3개)으로 되돌린 뒤 hbm2ddl.auto=update로 audit_log 엔티티만 띄워 본다(앱 기동과 같은 경로).
 * 다른 테스트와 스키마를 공유하므로 끝나면 제약을 현재 enum 값 전체로 되돌린다.
 */
class AuditEnumConstraintTest extends IntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	private static final String CONSTRAINT = "audit_log_action_check";

	private static final List<String> F2_ACTIONS = List.of("INQUIRY_CONTENT_VIEW_ATTEMPTED", "INQUIRY_CONTENT_VIEWED",
			"INQUIRY_CONTENT_VIEW_FAILED");

	@Test
	void freshSchemaHasAnActionCheckListingEveryAuditAction() {
		String definition = constraintDefinition();

		assertThat(definition).as("Hibernate가 action 컬럼에 허용값 CHECK를 만든다").isNotNull();
		for (AuditAction action : AuditAction.values()) {
			assertThat(definition).contains("'" + action.name() + "'");
		}
	}

	@Test
	void schemaUpdateKeepsAnExistingNarrowActionCheck() {
		try {
			replaceConstraint(F2_ACTIONS);

			runSchemaUpdateForAuditLog();

			String definition = constraintDefinition();
			assertThat(definition).as("ddl-auto=update는 기존 CHECK 제약을 넓히지 않는다").doesNotContain("'INQUIRY_CLOSED'");
			assertThatThrownBy(() -> insert(AuditAction.INQUIRY_CLOSED))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
		finally {
			replaceConstraint(Arrays.stream(AuditAction.values()).map(Enum::name).toList());
		}
		insert(AuditAction.INQUIRY_CLOSED);
	}

	private String constraintDefinition() {
		List<String> found = jdbc.queryForList(
				"select pg_get_constraintdef(c.oid) from pg_constraint c join pg_class t on t.oid = c.conrelid "
						+ "where t.relname = 'audit_log' and c.conname = ?",
				String.class, CONSTRAINT);
		return found.isEmpty() ? null : found.get(0);
	}

	private void replaceConstraint(List<String> actions) {
		String values = actions.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "));
		jdbc.execute("alter table audit_log drop constraint if exists " + CONSTRAINT);
		jdbc.execute("alter table audit_log add constraint " + CONSTRAINT + " check (action in (" + values + "))");
	}

	private void insert(AuditAction action) {
		jdbc.update("insert into audit_log (id, actor_id, action, target_type, target_id, occurred_at) "
				+ "values (?, ?, ?, 'INQUIRY', ?, ?)", UUID.randomUUID(), UUID.randomUUID(), action.name(),
				UUID.randomUUID(), java.sql.Timestamp.from(Instant.now()));
	}

	/** 운영 앱의 ddl-auto=update와 같은 경로: hbm2ddl.auto=update로 audit_log 엔티티 하나만 띄웠다 닫는다. */
	private void runSchemaUpdateForAuditLog() {
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
			.applySetting("hibernate.connection.datasource", dataSource)
			.applySetting("hibernate.hbm2ddl.auto", "update")
			.applySetting("hibernate.hbm2ddl.halt_on_error", "true")
			.build();
		try (SessionFactory ignored = new MetadataSources(registry).addAnnotatedClass(AuditLog.class)
			.buildMetadata()
			.buildSessionFactory()) {
			// 띄우는 것만으로 스키마 갱신이 끝난다
		}
		finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

}
