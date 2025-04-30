package dev.newpower;

/**
 * Runner class to execute the unused symbols detector
 */
public class DetectorRunner {
    public void run(String projectPath) {
        try {
            System.out.println("Starting unused symbols detection for project: " + projectPath);
            System.out.println("==========================================");

            UnusedSymbolsDetector detector = new UnusedSymbolsDetector(projectPath);
            detector.analyze();

            System.out.println("==========================================");
            System.out.println("Detection completed. Review the output above for unused symbols.");
        } catch (Exception e) {
            System.err.println("Error running detector: " + e.getMessage());
            e.printStackTrace();
        }
    }
}