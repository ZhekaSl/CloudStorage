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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import ua.zhenya.cloudstorage.service.MinioService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.zhenya.cloudstorage.testdata.TestConstants.JPEG;
import static ua.zhenya.cloudstorage.testdata.TestConstants.USER_DIRECTORY_PATH;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTest {
    static final MinIOContainer minioContainer = new MinIOContainer("minio/minio")
            .withUserName("username")
            .withPassword("password");

    @Autowired
    protected MinioClient minioClient;

    @Autowired
    protected MinioService minioService;

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

    @BeforeAll
    void prepareMinio() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }

        boolean objectExists = minioService.objectExists(USER_DIRECTORY_PATH);
        if (!objectExists) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(USER_DIRECTORY_PATH)
                    .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                    .build());
        }
    }

    @AfterEach
    void clearMinio() throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(USER_DIRECTORY_PATH).recursive(true).build()
        );

        for (Result<Item> result : results) {
            String objectName = result.get().objectName();
            if (!objectName.equals(USER_DIRECTORY_PATH)) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build()
                );
            }
        }
    }

    public <T, R> void assertElementsInOrder(List<T> items, Function<T, R> mapper, List<R> expected) {
        List<R> actualFields = items.stream().map(mapper).toList();
        assertThat(actualFields).containsExactlyElementsOf(expected);
    }

    public <T, R> void assertElementsInOrder(List<T> items, Function<T, R> mapper, MultipartFile[] expectedFiles, Function<MultipartFile, R> expectedMapper) {
        List<R> actualFields = items.stream().map(mapper).toList();
        List<R> expectedFields = Arrays.stream(expectedFiles).map(expectedMapper).toList();

        assertThat(actualFields).containsExactlyElementsOf(expectedFields);
    }

    public <T, R> void assertElementsInOrder(List<T> items, Function<T, R> mapper, R[] expectedFiles, Integer id) {
        List<R> actualFields = items.stream().map(mapper).toList();
        List<R> expectedFields = Arrays.stream(expectedFiles).toList();

        assertThat(actualFields).containsExactlyElementsOf(expectedFields);
    }
}
