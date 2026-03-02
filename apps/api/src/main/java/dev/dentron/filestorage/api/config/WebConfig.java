package dev.dentron.filestorage.api.config;

import dev.dentron.filestorage.api.security.jwt.JwtAuthenticationFilter;
import dev.dentron.filestorage.common.util.DownloadTokenUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
@EnableScheduling
public class WebConfig {
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

    @Bean
    @ConditionalOnBean(JwtAuthenticationFilter.class)
    public FilterRegistrationBean<JwtAuthenticationFilter> filterRegistrationBean(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setEnabled(false);
        return registration;
    }
}
