package damso.support;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretNoteRepository extends JpaRepository<SecretNote, UUID> {
}
