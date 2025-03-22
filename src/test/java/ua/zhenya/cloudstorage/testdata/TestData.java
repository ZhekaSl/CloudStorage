package ua.zhenya.cloudstorage.testdata;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TestData {

    public static MultipartFile getRandomMultipartFile() {
        String uniqueFileName = UUID.randomUUID() + ".txt";
        return new MockMultipartFile("file", uniqueFileName, "text/plain", getNewResourceContent());
    }

    public static MultipartFile getRandomMultipartFile(String pathInFilename) {
        String uniqueFileName = UUID.randomUUID() + ".txt";
        return new MockMultipartFile("file", pathInFilename + uniqueFileName, "text/plain", getNewResourceContent());
    }

    public static MultipartFile createRandomMultipartFileWithFilename(String filename) {
        return new MockMultipartFile("file", filename, "text/plain", getNewResourceContent());
    }

    public static byte[] getNewResourceContent() {
        return UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    }

}
