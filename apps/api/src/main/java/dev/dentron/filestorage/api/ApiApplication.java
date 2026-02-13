package dev.dentron.filestorage.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "dev.dentron")
@ConfigurationPropertiesScan(basePackages = "dev.dentron")
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "dev.dentron.filestorage.persistence.jpa.repository")
@EntityScan(basePackages = "dev.dentron.filestorage.persistence.jpa.entity")
@EnableKafka
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

}
