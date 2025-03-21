package ua.zhenya.cloudstorage.testdata;

import org.springframework.core.io.ClassPathResource;
import ua.zhenya.cloudstorage.utils.Constants;

public class TestConstants {
    public static final String NATURE_JPG_PATH = "files/nature.jpg";
    public static final String RANDOM_TXT_PATH = "files/random.txt";
    public static final ClassPathResource TXT = new ClassPathResource(RANDOM_TXT_PATH);
    public static final ClassPathResource JPEG = new ClassPathResource(NATURE_JPG_PATH);
    public static final int USER_1_ID = 1;
    public static final String USER_DIRECTORY_PATH = Constants.USER_DIRECTORY_PATH.formatted(USER_1_ID);

}
