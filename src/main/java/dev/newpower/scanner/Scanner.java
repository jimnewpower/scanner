package dev.newpower.scanner;

import java.io.IOException;
import java.util.Set;

/**
 * Interface for all symbol scanners
 */
public interface Scanner {
    /**
     * Scan for symbols in the given base directory
     * @param baseDir The base directory to scan
     * @throws IOException if there are any file access issues
     */
    void scan(String baseDir) throws IOException;

    /**
     * Get the set of all symbols found by this scanner
     * @return Set of all symbols
     */
    Set<String> getAllSymbols();

    /**
     * Get the set of used symbols found by this scanner
     * @return Set of used symbols
     */
    Set<String> getUsedSymbols();
} 