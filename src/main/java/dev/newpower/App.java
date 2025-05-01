package dev.newpower;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for the unused symbols scanner
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Missing required argument: project path");
            printUsage();
            System.exit(1);
        }

        String projectPath = args[0];
        try {
            logger.info("Starting scanner for project: {}", projectPath);
            new DetectorRunner().run(projectPath);
        } catch (Exception e) {
            logger.error("Error running scanner: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar scanner.jar <project-path>");
        System.out.println("  project-path: Path to the Java/Spring project to scan");
        System.out.println("\nExample:");
        System.out.println("  java -jar scanner.jar /path/to/my/project");
    }
}
