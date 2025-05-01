package dev.newpower.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Spring configuration files
 */
public class SpringConfigScanner extends AbstractScanner {
    private static final String SCANNER_ID = "SPRING";

    @Override
    public String getScannerId() {
        return SCANNER_ID;
    }

    @Override
    public void scan(String baseDir) throws IOException {
        List<Path> xmlFiles = getFilesByExtension(baseDir, ".xml");
        logger.info("Found {} XML files", xmlFiles.size());

        for (Path file : xmlFiles) {
            String content = readFileContents(file);
            if (isSpringConfigFile(content)) {
                logger.debug("Processing Spring config file: {}", file);
                extractSpringBeans(content, file.toString());
            }
        }
    }

    private boolean isSpringConfigFile(String content) {
        return content.contains("<beans") || content.contains("http://www.springframework.org");
    }

    private void extractSpringBeans(String content, String filePath) {
        Pattern beanPattern = Pattern.compile("<bean\\s+(?:.*?)class\\s*=\\s*\"([\\w.]+)\"");
        Matcher beanMatcher = beanPattern.matcher(content);

        while (beanMatcher.find()) {
            String className = beanMatcher.group(1);
            int beanLine = findLineNumber(content, "class=\"" + className + "\"");
            addSymbolWithLocation(className, filePath, beanLine);
            logger.debug("Found Spring bean: {}", className);
        }
    }
} 