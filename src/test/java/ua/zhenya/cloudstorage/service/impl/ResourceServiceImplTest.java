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

import static org.junit.jupiter.api.Assertions.*;
import static ua.zhenya.cloudstorage.testdata.TestConstants.*;

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
        MockMultipartFile multipartFile = new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream());
        String relativePath = USER_DIRECTORY_PATH + path;
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
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream()),
        };

        minioService.createDirectory(USER_DIRECTORY_PATH);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, "/", resources);

        assertEquals(2, resourceResponses.size());
        assertElementsInOrder(resourceResponses, ResourceResponse::getName, List.of(JPEG.getFilename(), TXT.getFilename()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getPath, List.of("/", "/"));
        assertElementsInOrder(resourceResponses, ResourceResponse::getSize, List.of(resources[0].getSize(), resources[1].getSize()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getType, List.of(ResourceType.FILE, ResourceType.FILE));

        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + JPEG.getFilename()));
        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + JPEG.getFilename()));
        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + JPEG.getFilename()));
    }

    @Test
    void uploadResources_uploadsResourceWithSubdirectoryInFilename() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String pathInFilenameJpeg = "somePath/Jpeg/";
        String pathInFilenameTxt = "somePath/innerFolder/Txt/";
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", pathInFilenameJpeg + JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", pathInFilenameTxt + TXT.getFilename(), "text/plain", TXT.getInputStream()),
        };

        minioService.createDirectory(USER_DIRECTORY_PATH);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(USER_1_ID, "/", resources);

        assertElementsInOrder(resourceResponses, ResourceResponse::getName, List.of(JPEG.getFilename(), TXT.getFilename()));
        assertElementsInOrder(resourceResponses, ResourceResponse::getPath, List.of(pathInFilenameJpeg, pathInFilenameTxt));
        assertElementsInOrder(resourceResponses, ResourceResponse::getSize, List.of(resources[0].getSize(), resources[1].getSize()));

        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + pathInFilenameJpeg + JPEG.getFilename()));
        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + pathInFilenameTxt + TXT.getFilename()));
    }

    @Test
    void uploadResources_throws_whenFileAlreadyExists() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
        };

        minioService.createDirectory(USER_DIRECTORY_PATH);
        resourceService.uploadResources(USER_1_ID, "/", resources);

        assertThrows(RuntimeException.class, () -> resourceService.uploadResources(USER_1_ID, "", resources));
    }

    @Test
    void uploadResources_throws_whenTargetDirectoryNotExists() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
        };

        minioService.createDirectory(USER_DIRECTORY_PATH);

        assertThrows(RuntimeException.class, () -> resourceService.uploadResources(USER_1_ID, "unknownFolder/", resources));

    }

    @Test
    void getResourceInfo_shouldReturnFileInfo_whenResourceExists() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        ClassPathResource jpeg = new ClassPathResource(TestConstants.NATURE_JPG_PATH);
        String path = "myfolder/images/";
        MultipartFile[] resources = new MultipartFile[]{
                new MockMultipartFile("file", jpeg.getFilename(), "image/jpeg", jpeg.getInputStream()),
        };

        String relativePath = path + jpeg.getFilename();
        minioService.uploadObject(USER_DIRECTORY_PATH + path + jpeg.getFilename(), resources[0].getInputStream(), resources[0].getSize(), resources[0].getContentType());
        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, relativePath);

        assertEquals(jpeg.getFilename(), resourceInfo.getName());
        assertEquals(path, resourceInfo.getPath());
        assertEquals(resources[0].getSize(), resourceInfo.getSize());
        assertEquals(ResourceType.FILE, resourceInfo.getType());
    }

    @Test
    void getResourceInfo_throwsException_whenFileNotExists() {

        String absolutePath = USER_DIRECTORY_PATH + "unknown/directory/text.txt";

        assertThrows(RuntimeException.class, () -> resourceService.getResourceInfo(USER_1_ID, absolutePath));
    }

    @Test
    void getResourceInfo_shouldReturnDirectoryInfo_whenDirectoryExists() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        String path = "mydirectory/images/";
        String absolutePath = USER_DIRECTORY_PATH + path;

        minioService.createDirectory(absolutePath);
        ResourceResponse resourceInfo = resourceService.getResourceInfo(USER_1_ID, path);

        assertEquals("images", resourceInfo.getName());
        assertEquals("mydirectory/", resourceInfo.getPath());
        assertNull(resourceInfo.getSize());
        assertEquals(ResourceType.DIRECTORY, resourceInfo.getType());
    }

    @Test
    void getResourceInfo_throwsException_whenDirectoryNotExists() {

        String absolutePath = USER_DIRECTORY_PATH + "unknown/directory/";

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

        MultipartFile multipartFileJpeg = new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream());
        Long expectedSize = createTestResource(USER_DIRECTORY_PATH + path, multipartFileJpeg);

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

        String path = "mydirectory/images/random.txt";
        String absolutePath = USER_DIRECTORY_PATH + path;
        MultipartFile multipartFile = new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream());

        createTestResource(USER_DIRECTORY_PATH + path, multipartFile);
        assertTrue(minioService.objectExists(absolutePath));

        resourceService.deleteResource(USER_1_ID, path);
        assertFalse(minioService.objectExists(absolutePath));
    }

    @Test
    public void deleteResource_shouldDeleteDirectoryRecursively() throws Exception {

        String path = "mydirectory/images/";
        String absolutePath = USER_DIRECTORY_PATH + path;
        MultipartFile[] multipartFiles = new MockMultipartFile[]{
                new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream()),
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", "newFolder/" + JPEG.getFilename(), "image/jpeg", JPEG.getInputStream())
        };

        uploadDirectoryWithContent(absolutePath, multipartFiles);

        resourceService.deleteResource(USER_1_ID, path);
        assertFalse(minioService.objectExists(absolutePath));
        checkObjectsExistence(false, absolutePath, multipartFiles);
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

        String path = "mydirectory/images/";
        String newFolder = "newfolder";
        String fullPath = path + newFolder + "/";
        String absolutePath = USER_DIRECTORY_PATH + fullPath;

        minioService.createDirectory(USER_DIRECTORY_PATH + path); //make sure that parent directory exists
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

        String path = "mydirectory/images/";
        String newFolder = "newfolder";
        String fullPath = path + newFolder + "/";
        String absolutePath = USER_DIRECTORY_PATH + fullPath;

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
            "folder1/olddir/, folder2/newdir/, folder2/, newdir, DIRECTORY",
            "folder1/folder2/oldfile.txt, newFolder/newfile.txt, newFolder/, newfile.txt, FILE",
            // Moving from root
            "random.txt, folder1/random.txt, folder1/, random.txt, FILE",
            "dir/, folder1/dir/, folder1/, dir, DIRECTORY",
    })
    public void moveResource_shouldMoveResource(String from, String to, String expectedPath, String expectedFilename, ResourceType expectedType) throws Exception {
        MultipartFile multipartFileJpeg = new MockMultipartFile("file", TXT.getFilename(), "image/jpeg", TXT.getInputStream());
        Long expectedSize = createTestResource(USER_DIRECTORY_PATH + from, multipartFileJpeg);
        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + from));

        ResourceResponse resourceInfo = resourceService.moveResource(USER_1_ID, from, to);

        assertNotNull(resourceInfo);
        assertEquals(expectedFilename, resourceInfo.getName());
        assertEquals(expectedPath, resourceInfo.getPath());
        assertEquals(expectedType, resourceInfo.getType());
        assertEquals(expectedSize, resourceInfo.getSize());

        assertFalse(minioService.objectExists(USER_DIRECTORY_PATH + from));
        assertTrue(minioService.objectExists(USER_DIRECTORY_PATH + to));
    }

    @ParameterizedTest
    @CsvSource({
            // Simple folder move one level up
            "folder1/dir/, folder2/dir/, folder2/, dir",
            // Move with renaming
            "documents/archive/, backup/new_archive/, backup/, new_archive",
            // Moving a folder with multiple nested levels
            "workspace/main/configs/, workspace/backup/configs/, workspace/backup/, configs",
            // Move a root folder into another directory
            "rootFolder/, archive/rootFolder/, archive/, rootFolder",
            // Move and rename simultaneously
            "data/old_name/, data/new_name/, data/, new_name",
            // Move a deeply nested structure
            "x/y/z/a/b/c/, new_x/new_y/new_z/a/b/c/, new_x/new_y/new_z/a/b/, c"
    })
    public void moveResource_shouldMoveFolderWithContent(String from, String to, String expectedPath, String expectedFolderName) throws Exception {
        MultipartFile[] multipartFiles = new MockMultipartFile[]{
                new MockMultipartFile("file", TXT.getFilename(), "text/plain", TXT.getInputStream()),
                new MockMultipartFile("file", JPEG.getFilename(), "image/jpeg", JPEG.getInputStream()),
                new MockMultipartFile("file", "newFolder/" + JPEG.getFilename(), "image/jpeg", JPEG.getInputStream())
        };
        uploadDirectoryWithContent(USER_DIRECTORY_PATH + from, multipartFiles);
        checkObjectsExistence(true, USER_DIRECTORY_PATH + from, multipartFiles);

        ResourceResponse response = resourceService.moveResource(USER_1_ID, from, to);

        assertNotNull(response);
        assertEquals(expectedFolderName, response.getName());
        assertEquals(expectedPath, response.getPath());
        assertEquals(ResourceType.DIRECTORY, response.getType());

        checkObjectsExistence(false, USER_DIRECTORY_PATH + from, multipartFiles);
        checkObjectsExistence(true, USER_DIRECTORY_PATH + to, multipartFiles);
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
            "folder/innerfolder/, rootFile",
            "folder/innerfolder/, folder/text.txt",
    })
    public void moveResource_throws_whenMoveDirectoryToFile(String from, String to) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioService.createDirectory(USER_DIRECTORY_PATH + from);

        assertThrows(RuntimeException.class, () -> resourceService.moveResource(USER_1_ID, from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "non-existent-file.txt, new-folder/newfile.txt",
            "non-existent-folder/, new-folder/",
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
        createTestResource(USER_DIRECTORY_PATH + from, new MockMultipartFile("file", "file1.txt", "text/plain", TXT.getInputStream()));
        createTestResource(USER_DIRECTORY_PATH + to, new MockMultipartFile("file", "file2.txt", "text/plain", TXT.getInputStream()));

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
        minioService.createDirectory(relativePath);
        for (MultipartFile multipartFile : multipartFiles) {
            minioService.uploadObject(relativePath + multipartFile.getOriginalFilename(), multipartFile.getInputStream(), multipartFile.getSize(), multipartFile.getContentType());
        }
    }

    private void checkObjectsExistence(boolean checkExistence, String relativePath, MultipartFile... multipartFiles) throws Exception {
        for (MultipartFile multipartFile : multipartFiles) {
            String absolutePath = relativePath + multipartFile.getOriginalFilename();
            if (checkExistence) {
                assertTrue(minioService.objectExists(absolutePath));
            } else {
                assertFalse(minioService.objectExists(absolutePath));
            }
        }
    }
}