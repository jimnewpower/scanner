package dev.newpower.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for JSF component references
 */
public class JsfComponentScanner extends AbstractScanner {
    private static final String SCANNER_ID = "JSF";

    @Override
    public String getScannerId() {
        return SCANNER_ID;
    }

    @Override
    public void scan(String baseDir) throws IOException {
        List<Path> xhtmlFiles = getFilesByExtension(baseDir, ".xhtml");
        logger.info("Found {} XHTML files", xhtmlFiles.size());

        for (Path file : xhtmlFiles) {
            logger.debug("Processing XHTML file: {}", file);
            String content = readFileContents(file);
            extractJsfComponents(content, file.toString());
        }
    }

    private void extractJsfComponents(String content, String filePath) {
        // Look for EL expressions like #{bean.property}
        Pattern elPattern = Pattern.compile("\\#\\{([\\w.]+)");
        Matcher elMatcher = elPattern.matcher(content);

        while (elMatcher.find()) {
            String elExpression = elMatcher.group(1);
            int elLine = findLineNumber(content, "#{" + elExpression);
            addSymbolWithLocation(elExpression, filePath, elLine);
            usedSymbols.add(elExpression);
            logger.debug("Found JSF component: {}", elExpression);
        }
    }
} 