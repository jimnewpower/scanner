package dev.newpower.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Abstract base class for all scanners
 */
public abstract class AbstractScanner {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Set<String> allSymbols = new HashSet<>();
    protected final Set<String> usedSymbols = new HashSet<>();
    protected final Map<String, SymbolLocation> symbolLocations = new HashMap<>();

    /**
     * Represents the location of a symbol in the source code
     */
    public static class SymbolLocation {
        private final String filePath;
        private final int lineNumber;

        public SymbolLocation(String filePath, int lineNumber) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public String toString() {
            return filePath + ":" + lineNumber;
        }
    }

    /**
     * Get the unique identifier for this scanner
     * @return The scanner's unique identifier
     */
    public abstract String getScannerId();

    /**
     * Scan the specified directory for symbols
     * @param baseDir The base directory to scan
     * @throws IOException If an I/O error occurs
     */
    public abstract void scan(String baseDir) throws IOException;

    /**
     * Get all found symbols
     * @return Set of all found symbols
     */
    public Set<String> getAllSymbols() {
        return allSymbols;
    }

    /**
     * Get all used symbols
     * @return Set of all used symbols
     */
    public Set<String> getUsedSymbols() {
        return usedSymbols;
    }

    /**
     * Get the location for a symbol
     * @param symbol The symbol to look up
     * @return The location where the symbol was found
     */
    public SymbolLocation getSymbolLocation(String symbol) {
        return symbolLocations.get(symbol);
    }

    /**
     * Add a symbol with its location
     * @param symbol The symbol to add
     * @param filePath The file path where the symbol was found
     * @param lineNumber The line number where the symbol was found
     */
    protected void addSymbolWithLocation(String symbol, String filePath, int lineNumber) {
        allSymbols.add(symbol);
        symbolLocations.put(symbol, new SymbolLocation(filePath, lineNumber));
    }

    /**
     * Get files with the specified extension in the directory
     * @param baseDir The base directory
     * @param extension The file extension to look for
     * @return List of matching files
     * @throws IOException If an I/O error occurs
     */
    protected List<Path> getFilesByExtension(String baseDir, String extension) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walk(Path.of(baseDir))
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(extension))
            .forEach(files::add);
        return files;
    }

    /**
     * Read the contents of a file
     * @param file The file to read
     * @return The file contents as a string
     * @throws IOException If an I/O error occurs
     */
    protected String readFileContents(Path file) throws IOException {
        return Files.readString(file);
    }

    /**
     * Get the line number of a symbol in the file content
     * @param content The file content
     * @param symbol The symbol to find
     * @return The line number where the symbol appears
     */
    protected int findLineNumber(String content, String symbol) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(symbol)) {
                return i + 1; // Line numbers are 1-based
            }
        }
        return 1; // Default to line 1 if not found
    }
} 