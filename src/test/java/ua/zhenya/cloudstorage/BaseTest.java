package ua.zhenya.cloudstorage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.testcontainers.containers.MinIOContainer;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SqlGroup({
        @Sql(
                scripts = "classpath:insert-users.sql",
                executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
        ),
        @Sql(
                scripts = "classpath:clear-users.sql",
                executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
        )
})
public abstract class BaseTest {
    static final MinIOContainer minioContainer = new MinIOContainer("minio/minio")
            .withUserName("username")
            .withPassword("password");

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    static {
        minioContainer.start();
    }

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", minioContainer::getS3URL);
        registry.add("minio.access-key", minioContainer::getUserName);
        registry.add("minio.secret-key", minioContainer::getPassword);
    }

    @BeforeEach
    void createBucket() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );

        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @AfterEach
    void clearMinio() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()
        );

        for (Result<Item> result : results) {
            String objectName = result.get().objectName();
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build()
            );
        }
    }

    public <T, R> void assertElementsInOrder(List<T> items, Function<T, R> mapper, List<R> expected) {
        List<R> actualFields = items.stream().map(mapper).toList();
        assertThat(actualFields).containsExactlyElementsOf(expected);
    }
}
