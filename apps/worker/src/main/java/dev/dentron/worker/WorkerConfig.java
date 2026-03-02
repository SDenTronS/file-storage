package dev.dentron.worker;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dentron.filestorage.common.util.DownloadTokenUtils;
import org.apache.tika.Tika;
import org.flywaydb.core.internal.util.ObjectMapperFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class WorkerConfig {

    @Bean
    public Tika tika() {
        return new Tika();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    }

    @Bean(destroyMethod = "close")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        return new VirtualThreadTaskExecutor("app-vt-");
    }

    @Bean
    public DownloadTokenUtils tokenUtils(@Value("${download.token.secret}") String secret) {
        return new DownloadTokenUtils(secret);
    }
}
