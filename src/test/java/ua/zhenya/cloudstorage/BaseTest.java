package ua.zhenya.cloudstorage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import ua.zhenya.cloudstorage.service.MinioService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.zhenya.cloudstorage.testdata.TestConstants.USER_DIRECTORY_PATH;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

    public <T, R> void assertContainsOnly(List<T> actualItems,
                                          Function<T, R> actualMapper,
                                          List<R> expectedValues,
                                          BiPredicate<R, R> condition) {
        List<R> actualValues = actualItems.stream()
                .map(actualMapper)
                .toList();

        for (R actualValue : actualValues) {
            boolean matchFound = expectedValues.stream()
                    .anyMatch(expectedValue -> condition.test(actualValue, expectedValue));
            assertTrue(matchFound, "Expected value not found for: " + actualValue);
        }
    }

    public <T, U, R> void assertElementsInOrder(List<T> items,
                                                Function<T, R> actualMapper,
                                                List<U> expectedItems,
                                                Function<U, R> expectedMapper,
                                                BiPredicate<R, R> condition) {
        List<R> actualValues = items.stream().map(actualMapper).toList();
        List<R> expectedValues = expectedItems.stream().map(expectedMapper).toList();

        if (actualValues.size() != expectedValues.size()) {
            throw new AssertionError("Lists have different sizes");
        }

        for (int i = 0; i < actualValues.size(); i++) {
            R actual = actualValues.get(i);
            R expected = expectedValues.get(i);

            if (!condition.test(actual, expected)) {
                throw new AssertionError(String.format("Mismatch at index %d: actual=%s, expected=%s", i, actual, expected));
            }
        }
    }

    protected Map<String, byte[]> extractZipContents(InputStream zipInputStream) throws IOException {
        Map<String, byte[]> filesMap = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, length);
                }
                filesMap.put(entry.getName(), baos.toByteArray());
                zis.closeEntry();
            }
        }
        return filesMap;
    }

    protected Long createTestResource(String absolutePath, MultipartFile multipartFile) throws Exception {
        if (absolutePath.endsWith("/")) {
            minioService.createDirectory(absolutePath);
            return null;
        } else {
            minioService.uploadObject(absolutePath, multipartFile.getInputStream(), multipartFile.getSize(), multipartFile.getContentType());
            return multipartFile.getSize();
        }
    }

    protected void checkIntermediateFoldersExist(String targetPath) throws Exception {
        String[] pathParts = targetPath.split("/");
        StringBuilder currentPath = new StringBuilder(USER_DIRECTORY_PATH);

        for (String part : pathParts) {
            if (part.isEmpty()) {
                continue;
            }
            currentPath.append(part).append("/");
            assertTrue(minioService.objectExists(currentPath.toString()));
        }
    }

    protected void uploadContentToDirectory(String relativePath, MultipartFile[] multipartFiles) throws Exception {
        for (MultipartFile multipartFile : multipartFiles) {
            createIntermediateDirectoriesIfNeeded(relativePath + multipartFile.getOriginalFilename());
            minioService.uploadObject(relativePath + multipartFile.getOriginalFilename(), multipartFile.getInputStream(), multipartFile.getSize(), multipartFile.getContentType());
        }
    }

    protected void createIntermediateDirectoriesIfNeeded(String path) throws Exception {
        String[] pathParts = path.split("/");

        StringBuilder currentDirPath = new StringBuilder();
        for (int i = 0; i < pathParts.length - 1; i++) {
            currentDirPath.append(pathParts[i]).append("/");
            String directoryPath = currentDirPath.toString();
            createEmptyObjectIfNotExist(directoryPath);
        }
    }

    protected void createEmptyObjectIfNotExist(String path) throws Exception {
        if (!minioService.objectExists(path)) {
            minioService.createDirectory(path);
        }
    }

    protected void checkObjectsExistence(boolean checkExistence, String relativePath, MultipartFile... multipartFiles) {
        for (MultipartFile multipartFile : multipartFiles) {
            String absolutePath = relativePath + multipartFile.getOriginalFilename();
            if (checkExistence) {
                assertTrue(minioService.objectExists(absolutePath));
            } else {
                assertFalse(minioService.objectExists(absolutePath));
            }
        }
    }

    protected String buildPath(String path) {
        return USER_DIRECTORY_PATH + path;
    }

    protected void checkContentMoved(String sourcePath, String targetPath, MultipartFile[] files) throws Exception {
        checkObjectsExistence(false, USER_DIRECTORY_PATH + sourcePath, files);
        checkObjectsExistence(true, USER_DIRECTORY_PATH + targetPath, files);
    }
}
