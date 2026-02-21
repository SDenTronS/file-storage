package dev.dentron.filestorage.persistence;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "dev.dentron.filestorage.persistence.jpa.repository")
@ComponentScan("dev.dentron.filestorage.persistence")
@EntityScan(basePackages = "dev.dentron.filestorage.persistence.jpa.entity")
public class TestJpaConfiguration {
}
