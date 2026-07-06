package online.pavelusanli.repo;

import online.pavelusanli.model.entity.ChunkingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChunkingProfileRepository extends JpaRepository<ChunkingProfile, Long> {
    List<ChunkingProfile> findAllByOrderByCreatedAtAsc();
}