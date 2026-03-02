package dev.dentron.filestorage.persistence.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories(basePackages = "dev.dentron.filestorage.persistence.jpa.repository")
@EntityScan(basePackages = "dev.dentron.filestorage.persistence.jpa.entity")
@Configuration
public class JpaConfig {
}
