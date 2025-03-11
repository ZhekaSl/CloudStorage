package ua.zhenya.cloudstorage.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.zhenya.cloudstorage.properties.MinioProperties;
import ua.zhenya.cloudstorage.service.MinioService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void init() {
        createBucket();
    }

    @Override
    public void createBucket() {
        String bucketName = minioProperties.getBucketName();

        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }

    @Override
    public void createDirectory(String fullPath) {
        try (InputStream inputStream = new ByteArrayInputStream(new byte[0])) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(fullPath)
                    .stream(inputStream, 0, -1)
                    .build());
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }
}
