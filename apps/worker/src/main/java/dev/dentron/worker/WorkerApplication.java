package dev.dentron.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "dev.dentron")
@ConfigurationPropertiesScan(basePackages = "dev.dentron")
@EnableConfigurationProperties
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }

}
