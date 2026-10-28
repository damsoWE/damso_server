package damso.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F5 운영자 조회문(specs/006-audit-log-history/contracts/operator-queries.md)을 문서에서 그대로 읽어 실행한다.
 * 문서와 테스트가 같은 SQL을 쓰게 하려는 것이다. psql 변수(`\set` 줄)는 지우고 `:'변수'`를 바인딩 자리로 바꾼다.
 */
public final class OperatorQueries {

	private static final Path DOCUMENT = Path.of("specs/006-audit-log-history/contracts/operator-queries.md");

	private static final Pattern SQL_BLOCK = Pattern.compile("```sql\\n(.*?)```", Pattern.DOTALL);

	private OperatorQueries() {
	}

	/** Q1. 스레드 하나의 전 과정. 컬럼: occurred_at, seq, actor_kind, actor_id, event, message_id, detail. */
	public static List<Map<String, Object>> thread(JdbcTemplate jdbc, UUID threadRootId) {
		return run(jdbc, section("## Q1."), "thread_root_id", threadRootId);
	}

	/** Q2. 계정 하나가 한 행위. 컬럼: occurred_at, event, message_id. */
	public static List<Map<String, Object>> account(JdbcTemplate jdbc, UUID accountId) {
		return run(jdbc, section("## Q2."), "account_id", accountId);
	}

	/** Q1의 event 열만 순서대로. */
	public static List<String> threadEvents(JdbcTemplate jdbc, UUID threadRootId) {
		return thread(jdbc, threadRootId).stream().map(row -> (String) row.get("event")).toList();
	}

	private static List<Map<String, Object>> run(JdbcTemplate jdbc, String sql, String variable, UUID value) {
		String placeholder = ":'" + variable + "'";
		List<Object> args = new ArrayList<>();
		int from = 0;
		while ((from = sql.indexOf(placeholder, from)) >= 0) {
			args.add(value);
			from += placeholder.length();
		}
		return jdbc.queryForList(sql.replace(placeholder, "?"), args.toArray());
	}

	private static String section(String heading) {
		String document;
		try {
			document = Files.readString(DOCUMENT);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		int start = document.indexOf(heading);
		if (start < 0) {
			throw new IllegalStateException("operator-queries.md has no section " + heading);
		}
		Matcher block = SQL_BLOCK.matcher(document);
		if (!block.find(start)) {
			throw new IllegalStateException("operator-queries.md section has no sql block " + heading);
		}
		return block.group(1).lines().filter(line -> !line.startsWith("\\set")).reduce("", (a, b) -> a + b + "\n")
			.trim()
			.replaceAll(";$", "");
	}

}
