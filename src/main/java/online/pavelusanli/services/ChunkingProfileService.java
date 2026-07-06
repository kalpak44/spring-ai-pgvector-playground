package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.common.ChunkingStrategy;
import online.pavelusanli.model.entity.ChunkingProfile;
import online.pavelusanli.repo.ChunkingProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChunkingProfileService {

    private final ChunkingProfileRepository repo;

    @Transactional(readOnly = true)
    public List<ChunkingProfile> findAll() {
        return repo.findAllByOrderByCreatedAtAsc();
    }

    public ChunkingProfile create(String name, String description, ChunkingStrategy strategy,
                                  Integer chunkSize, Integer chunkOverlap, String separator) {
        return repo.save(ChunkingProfile.builder()
                .name(name)
                .description(description)
                .strategy(strategy)
                .chunkSize(strategy == ChunkingStrategy.FIXED_TOKENS ? chunkSize : null)
                .chunkOverlap(strategy == ChunkingStrategy.FIXED_TOKENS ? chunkOverlap : null)
                .separator(strategy == ChunkingStrategy.CUSTOM_SEPARATOR ? separator : null)
                .build());
    }

    public ChunkingProfile update(Long id, String name, String description, ChunkingStrategy strategy,
                                  Integer chunkSize, Integer chunkOverlap, String separator) {
        ChunkingProfile profile = repo.findById(id).orElseThrow();
        profile.setName(name);
        profile.setDescription(description);
        profile.setStrategy(strategy);
        profile.setChunkSize(strategy == ChunkingStrategy.FIXED_TOKENS ? chunkSize : null);
        profile.setChunkOverlap(strategy == ChunkingStrategy.FIXED_TOKENS ? chunkOverlap : null);
        profile.setSeparator(strategy == ChunkingStrategy.CUSTOM_SEPARATOR ? separator : null);
        return repo.save(profile);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}