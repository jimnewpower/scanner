package dev.newpower;

import dev.newpower.scanner.AbstractScanner;
import dev.newpower.scanner.JavaSymbolScanner;
import dev.newpower.scanner.JsfComponentScanner;
import dev.newpower.scanner.SpringConfigScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A utility to detect unused symbols in a Spring + JSF application.
 * Scans Java files, XHTML files, and Spring configuration files.
 */
public class UnusedSymbolsDetector {
    private static final Logger logger = LoggerFactory.getLogger(UnusedSymbolsDetector.class);
    private final String baseDir;
    private final List<AbstractScanner> scanners;

    public UnusedSymbolsDetector(String baseDir) {
        this.baseDir = baseDir;
        this.scanners = initializeScanners();
    }

    private List<AbstractScanner> initializeScanners() {
        List<AbstractScanner> scanners = new ArrayList<>();
        scanners.add(new JavaSymbolScanner());
        scanners.add(new SpringConfigScanner());
        scanners.add(new JsfComponentScanner());
        return scanners;
    }

    public void analyze() throws IOException {
        logger.info("Starting analysis in directory: {}", baseDir);

        // Run all scanners
        for (AbstractScanner scanner : scanners) {
            logger.info("Running {} scanner...", scanner.getScannerId());
            scanner.scan(baseDir);
        }

        // Generate reports
        reportUnusedSymbols();
    }

    private void reportUnusedSymbols() {
        logger.info("\n=== UNUSED SYMBOLS REPORT ===");
        
        for (AbstractScanner scanner : scanners) {
            Set<String> allSymbols = scanner.getAllSymbols();
            Set<String> usedSymbols = scanner.getUsedSymbols();
            Set<String> unusedSymbols = new java.util.HashSet<>(allSymbols);
            unusedSymbols.removeAll(usedSymbols);

            logger.info("\n{} Scanner Results:", scanner.getScannerId());
            logger.info("Found {} unused symbols:", unusedSymbols.size());
            if (!unusedSymbols.isEmpty()) {
                for (String symbol : unusedSymbols) {
                    AbstractScanner.SymbolLocation location = scanner.getSymbolLocation(symbol);
                    if (location != null) {
                        logger.info("  - {} (at {})", symbol, location);
                    } else {
                        logger.info("  - {}", symbol);
                    }
                }
            }
        }
        
        logger.info("\n==========================================");
    }
}