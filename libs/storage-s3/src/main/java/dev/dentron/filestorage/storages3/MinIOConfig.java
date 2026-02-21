package dev.dentron.filestorage.storages3;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.messages.*;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.StorageClass;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor

@Configuration
@ConditionalOnBooleanProperty(prefix = "app.minio", name = "enabled", matchIfMissing = true)
public class MinIOConfig {
    @Bean(destroyMethod = "close")
    public MinioAsyncClient minioClient(MinIOCredentials credentials){
        var builder = MinioAsyncClient.builder()
                .region(Region.EU_CENTRAL_1.toString())
                .endpoint(credentials.getEndpoint())
                .credentials(credentials.getAccessKey(), credentials.getSecretKey());

        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio.init", name = "apply-cors", havingValue = "true")
    public CORSConfiguration corsConfiguration(MinIOCredentials credentials) {
        var cors = credentials.getCors();
        var rule =  new CORSConfiguration.CORSRule(
                cors.getAllowedHeaders(),
                cors.getAllowedMethods(),
                cors.getAllowedOrigins(),
                cors.getAllowedHeaders(),
                null,
                cors.getMaxAge()
        );

        return new CORSConfiguration(List.of(rule));
    }

    @Bean
    @ConditionalOnProperty(prefix = "minio.init", name = "apply-lifecycle", havingValue = "true")
    public LifecycleConfiguration lifecycleConfiguration(MinIOCredentials credentials) {
        var lifecycle = credentials.getLifecycle();
        String prefix = lifecycle.getPrefix();

        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Prefix cannot be empty");
        }

        List<LifecycleRule> rules = new LinkedList<>();
        rules.add(
                new LifecycleRule(
                        Status.ENABLED,
                        new AbortIncompleteMultipartUpload(lifecycle.getAbortIncompleteMultipartUploadDays()),
                        null,
                        new RuleFilter(prefix),
                        "abort_mpu",
                        null,
                        null,
                        null));

        return new LifecycleConfiguration(rules);
    }

    @Bean(name = "storage-bucket")
    public ApplicationRunner storageBucket(MinioAsyncClient minioClient,
                                           MinIOCredentials credentials,
                                           @Autowired(required = false) CORSConfiguration corsConfiguration,
                                           @Autowired(required = false) LifecycleConfiguration lifecycleConfiguration
    ) {

        return applicationArguments -> {
            String bucket = credentials.getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
                    .get(30, TimeUnit.SECONDS);

            if (!exists) {
                if (!credentials.getInit().isCreateBucket()) {
                    throw new IllegalStateException("Bucket " + bucket + " does not exist");
                }

                var builder = MakeBucketArgs.builder().bucket(bucket);
                minioClient.makeBucket(builder.build()).get(30, TimeUnit.SECONDS);
            }

            if (lifecycleConfiguration != null && credentials.getInit().isApplyLifecycle()) {
                minioClient.setBucketLifecycle(SetBucketLifecycleArgs.builder()
                                .config(lifecycleConfiguration)
                                .bucket(credentials.getBucket())
                                .build()
                ).get(30, TimeUnit.SECONDS);
            }

            if (corsConfiguration != null && credentials.getInit().isApplyCors()) {
                minioClient.setBucketCors(SetBucketCorsArgs.builder()
                        .config(corsConfiguration)
                        .bucket(credentials.getBucket())
                        .build()
                ).get(30, TimeUnit.SECONDS)   ;
            }
        };
    }
}
