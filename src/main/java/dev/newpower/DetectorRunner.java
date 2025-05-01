package dev.newpower;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runner class for the UnusedSymbolsDetector
 */
public class DetectorRunner {
    private static final Logger logger = LoggerFactory.getLogger(DetectorRunner.class);

    public void run(String projectDir) {
        // Expand relative path to absolute path
        Path absolutePath = Paths.get(projectDir).toAbsolutePath().normalize();
        logger.info("Scanning project directory: {}", absolutePath);

        try {
            UnusedSymbolsDetector detector = new UnusedSymbolsDetector(absolutePath.toString());
            detector.analyze();
        } catch (Exception e) {
            logger.error("Error scanning project: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}