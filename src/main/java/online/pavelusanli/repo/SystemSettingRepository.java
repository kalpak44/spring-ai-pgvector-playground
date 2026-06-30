package online.pavelusanli.repo;

import online.pavelusanli.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}