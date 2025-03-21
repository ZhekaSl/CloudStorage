package ua.zhenya.cloudstorage.service.impl;

import io.minio.errors.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.BaseIntegrationTest;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.dto.ResourceType;
import ua.zhenya.cloudstorage.service.MinioService;
import ua.zhenya.cloudstorage.service.ResourceService;
import ua.zhenya.cloudstorage.testdata.TestConstants;
import ua.zhenya.cloudstorage.utils.Constants;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static ua.zhenya.cloudstorage.testdata.TestConstants.JPEG;
import static ua.zhenya.cloudstorage.testdata.TestConstants.TXT;
import static ua.zhenya.cloudstorage.utils.Constants.USER_1_ID;

class ResourceServiceImplTest extends BaseIntegrationTest {
    @Autowired
    private ResourceService resourceService;

    @Autowired
    private MinioService minioService;

    @ParameterizedTest
    @CsvSource({
            "/, /, FILE",
            "myfolder/, myfolder/, FILE",
            "myfolder/inner/, myfolder/inner/, FILE"
    })
    void uploadResource_shouldUploadResource(String path, String expectedPath, ResourceType expectedType) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        MockMultipartFile multipartFile = new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream());
        String relativePath = userDirectoryPath + path;
        minioService.createDirectory(relativePath);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, path, new MultipartFile[]{multipartFile});

        assertEquals(1, resourceResponses.size());
        assertEquals(expectedPath, resourceResponses.getFirst().getPath());
        assertEquals(JPEG.getFilename(), resourceResponses.getFirst().getName());
        assertEquals(multipartFile.getSize(), resourceResponses.getFirst().getSize());
        assertEquals(expectedType, resourceResponses.getFirst().getType());

        String fullPath = relativePath + JPEG.getFilename();
        assertTrue(minioService.objectExists(fullPath));
    }

    @Test
    void uploadResources_uploadsMultipleResources() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream()),
        };

        minioService.createDirectory(userDirectoryPath);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, "/", resources);

        assertEquals(2, resourceResponses.size());
        assertElementsInOrder(resourceResponses, ResourceResponse::getName, List.of(JPEG.getFilename(), TXT.getFilename()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getPath, List.of("/", "/"));
        assertElementsInOrder(resourceResponses, ResourceResponse::getSize, List.of(resources[0].getSize(), resources[1].getSize()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getType, List.of(ResourceType.FILE, ResourceType.FILE));

        assertTrue(minioService.objectExists(userDirectoryPath + JPEG.getFilename()));
        assertTrue(minioService.objectExists(userDirectoryPath + JPEG.getFilename()));
        assertTrue(minioService.objectExists(userDirectoryPath + JPEG.getFilename()));
    }

    @Test
    void uploadResources_uploadsResourceWithSubdirectoryInFilename() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String pathInFilenameJpeg = "somePath/Jpeg/";
        String pathInFilenameTxt = "somePath/innerFolder/Txt/";
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", pathInFilenameJpeg + JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", pathInFilenameTxt + TXT.getFilename(), "text/plain", TXT.getInputStream()),
        };

        minioService.createDirectory(userDirectoryPath);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, "/", resources);

        assertElementsInOrder(resourceResponses, ResourceResponse::getName, List.of(JPEG.getFilename(), TXT.getFilename()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getPath, List.of(pathInFilenameJpeg, pathInFilenameTxt));
        assertElementsInOrder(resourceResponses, ResourceResponse::getSize, List.of(resources[0].getSize(), resources[1].getSize()));

        assertTrue(minioService.objectExists(userDirectoryPath + pathInFilenameJpeg + JPEG.getFilename()));
        assertTrue(minioService.objectExists(userDirectoryPath + pathInFilenameTxt + TXT.getFilename()));
    }

    @Test
    void uploadResources_throws_whenFileAlreadyExists() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
        };

        minioService.createDirectory(userDirectoryPath);
        resourceService.uploadResources(USER_1_ID, "/", resources);

        assertThrows(RuntimeException.class, () -> resourceService.uploadResources(USER_1_ID, "", resources));
    }

    @Test
    void uploadResources_throws_whenTargetDirectoryNotExists() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
        };

        minioService.createDirectory(userDirectoryPath);

        assertThrows(RuntimeException.class, () -> resourceService.uploadResources(USER_1_ID, "unknownFolder/", resources));

    }

    @Test
    void getResourceInfo_shouldReturnFileInfo_whenResourceExists() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        ClassPathResource jpeg = new ClassPathResource(TestConstants.NATURE_JPG_PATH);
        String path = "myfolder/images/";
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", jpeg.getFilename(), "image/jpeg", jpeg.getInputStream()),
        };

        String relativePath = path + jpeg.getFilename();
        minioService.uploadObject(userDirectoryPath + path + jpeg.getFilename(), resources[0].getInputStream(), resources[0].getSize(), resources[0].getContentType());
        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, relativePath);

        assertEquals(jpeg.getFilename(), resourceInfo.getName());
        assertEquals(path, resourceInfo.getPath());
        assertEquals(resources[0].getSize(), resourceInfo.getSize());
        assertEquals(ResourceType.FILE, resourceInfo.getType());
    }

    @Test
    void getResourceInfo_throwsException_whenFileNotExists() {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String absolutePath = userDirectoryPath + "unknown/directory/text.txt";

        assertThrows(RuntimeException.class, () -> resourceService.getResourceInfo(USER_1_ID, absolutePath));
    }

    @Test
    void getResourceInfo_shouldReturnDirectoryInfo_whenDirectoryExists() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String path = "mydirectory/images/";
        String absolutePath = userDirectoryPath + path;

        minioService.createDirectory(absolutePath);
        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, path);

        assertEquals("images", resourceInfo.getName());
        assertEquals("mydirectory/", resourceInfo.getPath());
        assertNull(resourceInfo.getSize());
        assertEquals(ResourceType.DIRECTORY, resourceInfo.getType());
    }

    @Test
    void getResourceInfo_throwsException_whenDirectoryNotExists() {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String absolutePath = userDirectoryPath + "unknown/directory/";

        assertThrows(RuntimeException.class, () -> resourceService.getResourceInfo(USER_1_ID, absolutePath));
    }

    @ParameterizedTest
    @CsvSource({
            "myfolder/images/nature.jpg, nature.jpg, myfolder/images/, FILE",
            "myfolder/images/, images, myfolder/, DIRECTORY",
            "rootfolder/, rootfolder, /, DIRECTORY",
            "/nature.jpg, nature.jpg, /, FILE",
    })
    void getResourceInfo_shouldReturnResourceInfo_whenResourceExists(String path, String expectedName, String expectedPath, ResourceType expectedType) throws Exception {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        MultipartFile multipartFileJpeg = new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream());
        Long expectedSize = createTestResource(userDirectoryPath + path, multipartFileJpeg);

        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, path);
        assertNotNull(resourceInfo);
        assertEquals(expectedName, resourceInfo.getName());
        assertEquals(expectedPath, resourceInfo.getPath());
        assertEquals(expectedType, resourceInfo.getType());
        assertEquals(expectedSize, resourceInfo.getSize());
    }

    @Test
    void getResourceInfo_throwsException_whenResourceNotExists() {
        String path = "resources/images/";

        assertThrows(RuntimeException.class, () -> resourceService.getResourceInfo(USER_1_ID, path));
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    void getResourceInfo_throwsException_whenPathIsEmptyOrNull(String path) {
        assertThrows(RuntimeException.class, () -> resourceService.getResourceInfo(USER_1_ID, path));
    }

    @Test
    public void deleteResource_shouldDeleteExistingFile() throws Exception {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String path = "mydirectory/images/random.txt";
        String absolutePath = userDirectoryPath + path;
        MultipartFile multipartFile = new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream());

        createTestResource(userDirectoryPath + path, multipartFile);
        assertTrue(minioService.objectExists(absolutePath));

        resourceService.deleteResource(USER_1_ID, path);
        assertFalse(minioService.objectExists(absolutePath));
    }

    @Test
    public void deleteResource_shouldDeleteDirectoryRecursively() throws Exception {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String path = "mydirectory/images/";
        String absolutePath = userDirectoryPath + path;
        MultipartFile[] multipartFiles = new MockMultipartFile[]{
                new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream()),
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", "newFolder/" + JPEG.getFilename(), "image/jpeg", JPEG.getInputStream())
        };

        minioService.createDirectory(absolutePath);
        uploadDirectoryWithContent(absolutePath, multipartFiles);

        resourceService.deleteResource(USER_1_ID, path);
        assertFalse(minioService.objectExists(absolutePath));
        checkObjectsNotExistence(absolutePath, multipartFiles);
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    @CsvSource({
            "unknown/text.txt",
            "unknown/"
    })
    public void deleteResource_throws_whenResourceNotExists(String path) {
        assertThrows(RuntimeException.class, () -> resourceService.deleteResource(USER_1_ID, path));
    }

    @Test
    public void createDirectory_shouldCreateEmptyDirectory() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String path = "mydirectory/images/";
        String newFolder = "newfolder";
        String fullPath = path + newFolder + "/";
        String absolutePath = userDirectoryPath + fullPath;

        minioService.createDirectory(userDirectoryPath + path); //make sure that parent directory exists
        ResourceResponse response = resourceService.createDirectory(USER_1_ID, fullPath);

        assertNotNull(response);
        assertEquals(path, response.getPath());
        assertEquals(newFolder, response.getName());
        assertEquals(ResourceType.DIRECTORY, response.getType());
        assertNull(response.getSize());

        assertTrue(minioService.objectExists(absolutePath));
    }

    @Test
    public void createDirectory_throws_whenDirectoryAlreadyExists() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        String path = "mydirectory/images/";
        String newFolder = "newfolder";
        String fullPath = path + newFolder + "/";
        String absolutePath = userDirectoryPath + fullPath;

        minioService.createDirectory(absolutePath);

        assertThrows(RuntimeException.class, () -> resourceService.createDirectory(USER_1_ID, fullPath));
    }

    @Test
    public void createDirectory_throws_whenParentDirectoryNotExists() {
        String path = "mydirectory/images/";
        String newFolder = "newfolder";
        String fullPath = path + newFolder + "/";

        assertThrows(RuntimeException.class, () -> resourceService.createDirectory(USER_1_ID, fullPath));
    }

    @ParameterizedTest
    @CsvSource({
            // Only move
            "folder1/random.txt, folder2/random.txt, folder2/, random.txt, FILE",
            "folder1/dir/, folder2/dir/, folder2/, dir, DIRECTORY",
            // Only rename
            "folder1/oldfile.txt, folder1/newfile.txt, folder1/, newfile.txt, FILE",
            "folder1/olddir/, folder1/newdir, folder1/, newdir, DIRECTORY",
            // Both move and rename
            "folder1/oldfile.txt, folder2/newfile.txt, folder2/, newfile.txt, FILE",
            "folder1/olddir/, folder2/newdir/, folder2/, newdir, DIRECTORY"
    })
    public void moveResource_shouldMoveResource(String from, String to, String expectedPath, String expectedFilename, ResourceType expectedType) throws Exception {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);
        MultipartFile multipartFileJpeg = new MockMultipartFile("file", TXT.getFilename(), "image/jpeg", TXT.getInputStream());
        Long expectedSize = createTestResource(userDirectoryPath + from, multipartFileJpeg);

        ResourceResponse resourceInfo = resourceService.moveResource(USER_1_ID, from, to);

        assertNotNull(resourceInfo);
        assertEquals(expectedFilename, resourceInfo.getName());
        assertEquals(expectedPath, resourceInfo.getPath());
        assertEquals(expectedType, resourceInfo.getType());
        assertEquals(expectedSize, resourceInfo.getSize());
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    public void moveResource_throws_whenFromPathIsIncorrect(String from) {
        assertThrows(RuntimeException.class, () -> resourceService.moveResource(USER_1_ID, from, "valid/text.txt"));
    }

    @ParameterizedTest
    @EmptySource
    @NullSource
    public void moveResource_throws_whenToPathIsIncorrect(String to) {
        assertThrows(RuntimeException.class, () -> resourceService.moveResource(USER_1_ID, "valid/text.txt", to));
    }

    @ParameterizedTest
    @CsvSource({
            "non-existent-file.txt, new-folder/newfile.txt",
            "non-existent-folder/, new-folder/"
    })
    public void moveResource_throws_whenResourceNotFound(String from, String to) {
        assertThrows(RuntimeException.class, () -> resourceService.moveResource(USER_1_ID, from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "folder1/existing.txt, folder2/existing.txt",
            "folder1/existing-dir/, folder2/existing-dir/"
    })
    public void moveResource_throws_whenTargetAlreadyExists(String from, String to) throws Exception {
        String userDirectoryPath = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);

        // Создаём два ресурса: `from` и `to`
        createTestResource(userDirectoryPath + from, new MockMultipartFile("file", "file1.txt", "text/plain", "test".getBytes()));
        createTestResource(userDirectoryPath + to, new MockMultipartFile("file", "file2.txt", "text/plain", "test".getBytes()));

        assertThrows(RuntimeException.class, () -> resourceService.moveResource(USER_1_ID, from, to));
    }

    @Test
    public void getDirectoryContext() {
    }

    @Test
    public void searchResources() {

    }

    @Test
    public void downloadResource() {

    }

    private Long createTestResource(String absolutePath, MultipartFile multipartFile) throws Exception {
        if (absolutePath.endsWith("/")) {
            minioService.createDirectory(absolutePath);
            return null;
        } else {
            minioService.uploadObject(absolutePath, multipartFile.getInputStream(), multipartFile.getSize(), multipartFile.getContentType());
            return multipartFile.getSize();
        }
    }

    private void uploadDirectoryWithContent(String relativePath, MultipartFile[] multipartFiles) throws Exception {
        for (MultipartFile multipartFile : multipartFiles) {
            minioService.uploadObject(relativePath + multipartFile.getOriginalFilename(), multipartFile.getInputStream(), multipartFile.getSize(), multipartFile.getContentType());
        }
    }

    private void checkObjectsNotExistence(String relativePath, MultipartFile... multipartFiles) throws Exception {
        for (MultipartFile multipartFile : multipartFiles) {
            assertFalse(minioService.objectExists(relativePath + multipartFile.getOriginalFilename()));
        }
    }
}