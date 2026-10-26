package damso.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.notification.domain.ParentNotificationSetting;

interface SpringDataParentNotificationSettingRepository extends JpaRepository<ParentNotificationSetting, UUID> {

	Optional<ParentNotificationSetting> findByParentId(UUID parentId);

	/**
	 * 행이 없으면 insert, 있으면 update — 유니크 제약(parent_id)과 원자적으로 경쟁한다. find-or-create는
	 * 같은 학부모의 첫 저장 요청 두 건이 동시에 오면 둘 다 "없음"을 보고 각자 insert해 하나가 유니크 제약
	 * 위반(409)으로 실패했다(PR #10 리뷰). version·updated_at도 이 쿼리 안에서 함께 갱신한다.
	 */
	@Query(value = "insert into parent_notification_setting "
			+ "(id, created_at, updated_at, version, parent_id, answer_notify_enabled) "
			+ "values (:id, now(), now(), 0, :parentId, :enabled) "
			+ "on conflict (parent_id) do update set "
			+ "answer_notify_enabled = excluded.answer_notify_enabled, updated_at = now(), "
			+ "version = parent_notification_setting.version + 1 " + "returning answer_notify_enabled",
			nativeQuery = true)
	boolean upsertAnswerNotifyEnabled(@Param("id") UUID id, @Param("parentId") UUID parentId,
			@Param("enabled") boolean enabled);

}
