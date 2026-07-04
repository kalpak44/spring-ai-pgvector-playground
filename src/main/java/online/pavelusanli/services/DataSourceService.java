package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.DataSource;
import online.pavelusanli.repo.DataSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class DataSourceService {

    private final DataSourceRepository repo;

    @Transactional(readOnly = true)
    public List<DataSource> findAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public DataSource create(String name, String connectorUrl, String connectorName, Map<String, String> config) {
        return repo.save(DataSource.builder()
                .name(name)
                .connectorUrl(connectorUrl)
                .connectorName(connectorName)
                .config(config)
                .build());
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}