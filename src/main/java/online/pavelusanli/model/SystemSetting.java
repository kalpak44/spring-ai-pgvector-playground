package online.pavelusanli.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    @Column(length = 64)
    private String key;

    @Column(columnDefinition = "text")
    private String value;
}