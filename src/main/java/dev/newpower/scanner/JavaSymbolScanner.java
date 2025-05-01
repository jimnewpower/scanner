package dev.newpower.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Java symbols (classes, methods, fields)
 */
public class JavaSymbolScanner extends AbstractScanner {
    private static final String SCANNER_ID = "JAVA";

    private static final List<String> IGNORE_PATTERNS = List.of(
            // Common Java methods
            "main",                // Main method
            "equals",              // Object methods
            "hashCode",
            "toString",
            "clone",
            "finalize",
            "wait",
            "notify",
            "notifyAll",
            
            // Common lifecycle methods
            "init",
            "destroy",
            "initialize",
            "startup",
            "shutdown",
            
            // Common accessor patterns
            "get[A-Z].*",          // Getters
            "set[A-Z].*",          // Setters
            "is[A-Z].*",           // Boolean getters
            "has[A-Z].*",          // Boolean checks
            "add[A-Z].*",          // Add methods
            "remove[A-Z].*",       // Remove methods
            "create[A-Z].*",       // Factory methods
            "build[A-Z].*",        // Builder methods
            
            // Common event handlers
            "on[A-Z].*",           // Event handlers
            "handle[A-Z].*",       // Event handlers
            "process[A-Z].*",      // Event processors
            
            // Common utility methods
            "validate.*",          // Validation methods
            "check.*",             // Check methods
            "verify.*",            // Verification methods
            "ensure.*",            // Ensure methods
            "assert.*",            // Assertion methods
            
            // Protected methods
            "findLineNumber",      // Protected utility methods
            "readFileContents",    // Protected utility methods
            "getFilesByExtension", // Protected utility methods
            
            // Spring-specific patterns
            ".*Config",            // Configuration classes
            ".*Configuration",     // Configuration classes
            ".*Properties",        // Properties classes
            ".*Application",       // Application classes
            ".*Runner",            // Runner classes
            ".*Initializer",       // Initializer classes
            ".*Listener",          // Listener classes
            ".*Interceptor",       // Interceptor classes
            ".*Filter",            // Filter classes
            ".*Controller",        // Controller classes
            ".*Service",           // Service classes
            ".*Repository",        // Repository classes
            ".*Component",         // Component classes
            ".*Bean",              // Bean classes
            ".*Factory",           // Factory classes
            ".*Builder",           // Builder classes
            
            // Common field patterns
            "logger",              // Logger fields
            "log",                 // Logger fields
            "serialVersionUID",    // Serialization
            "DEFAULT_.*",          // Default constants
            "MAX_.*",              // Maximum constants
            "MIN_.*",              // Minimum constants
            ".*_PATTERN",          // Pattern constants
            ".*Pattern",           // Pattern fields
            
            // Test and special classes
            ".*Test",              // Test classes
            ".*Tests",             // Test classes
            ".*TestCase",          // Test classes
            ".*Exception",         // Exceptions
            ".*Error",             // Errors
            ".*\\$.*",             // Inner classes
            ".*Enum",              // Enums
            ".*Constants",         // Constants classes
            ".*Util",              // Utility classes
            ".*Utils",             // Utility classes
            ".*Helper",            // Helper classes
            ".*Factory",           // Factory classes
            ".*Builder"            // Builder classes
    );

    // Patterns for different types of symbols
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public|protected|static|final|abstract|sealed|non-sealed)\\s+class\\s+(\\w+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?:public|protected|static|final|abstract|synchronized|native|strictfp)\\s+(?:[\\w<>\\[\\]]+\\s+)*(\\w+)\\s*\\([^)]*\\)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(?:public|protected|static|final|transient|volatile)\\s+(?:[\\w<>\\[\\]]+\\s+)+(\\w+)\\s*(?:=|;)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+(?:static\\s+)?([\\w.]+)(?:\\.\\*)?;");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+);");

    @Override
    public String getScannerId() {
        return SCANNER_ID;
    }

    @Override
    public void scan(String baseDir) throws IOException {
        List<Path> javaFiles = getFilesByExtension(baseDir, ".java");
        logger.info("Found {} Java files", javaFiles.size());

        // First pass: collect all symbols
        for (Path file : javaFiles) {
            String content = readFileContents(file);
            processJavaFile(content, file);
        }

        // Second pass: scan for usages
        for (Path file : javaFiles) {
            String content = readFileContents(file);
            scanForUsages(content);
        }
    }

    private void processJavaFile(String content, Path file) {
        // Extract package and class name
        String packageName = extractPackageName(content);
        String className = extractClassName(content, file.getFileName().toString());
        String fullClassName = packageName + "." + className;

        // Skip test classes
        if (fullClassName.contains("Test") || file.toString().contains("/test/")) {
            logger.debug("Skipping test class: {}", fullClassName);
            return;
        }

        // Add class to all classes
        int classLine = findLineNumber(content, "class " + className);
        addSymbolWithLocation(fullClassName, file.toString(), classLine);
        logger.debug("Found class: {}", fullClassName);

        // Extract methods
        extractMethods(content, fullClassName, file.toString());

        // Extract fields
        extractFields(content, fullClassName, file.toString());
    }

    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractClassName(String content, String fileName) {
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        return classMatcher.find() ? classMatcher.group(1) : fileName.replace(".java", "");
    }

    private void extractMethods(String content, String fullClassName, String filePath) {
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            if (!shouldIgnoreMethod(methodName)) {
                String methodSymbol = fullClassName + "." + methodName;
                int methodLine = findLineNumber(content, methodName + "(");
                addSymbolWithLocation(methodSymbol, filePath, methodLine);
                logger.debug("Found method: {}", methodSymbol);
            }
        }
    }

    private void extractFields(String content, String fullClassName, String filePath) {
        Matcher fieldMatcher = FIELD_PATTERN.matcher(content);
        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String fieldSymbol = fullClassName + "." + fieldName;
            int fieldLine = findLineNumber(content, fieldName + ";");
            addSymbolWithLocation(fieldSymbol, filePath, fieldLine);
            logger.debug("Found field: {}", fieldSymbol);
        }
    }

    private void scanForUsages(String content) {
        // Process imports
        extractImportedClasses(content);

        // Scan for method calls and field accesses
        for (String symbol : allSymbols) {
            if (symbol.contains(".")) {
                String simpleName = symbol.substring(symbol.lastIndexOf('.') + 1);
                String className = symbol.substring(0, symbol.lastIndexOf('.'));
                
                // Look for method calls
                if (content.contains("." + simpleName + "(") || // Direct method call
                    content.contains("super." + simpleName + "(") || // Super method call
                    content.contains("this." + simpleName + "(") || // This method call
                    content.contains(simpleName + "(")) { // Local method call
                    usedSymbols.add(symbol);
                    logger.debug("Found method usage: {}", symbol);
                }
                
                // Look for field accesses
                if (content.contains("." + simpleName + " ") || // Field access with space
                    content.contains("." + simpleName + ";") || // Field access with semicolon
                    content.contains("." + simpleName + ")") || // Field access with closing paren
                    content.contains("." + simpleName + ",") || // Field access with comma
                    content.contains("." + simpleName + ".") || // Field access with method call
                    content.contains("super." + simpleName) || // Super field access
                    content.contains("this." + simpleName) || // This field access
                    content.contains(simpleName + ".") || // Direct field access with method call
                    content.contains("return " + simpleName) || // Return statement
                    content.contains("= " + simpleName) || // Assignment
                    content.contains("==" + simpleName) || // Equality check
                    content.contains("!=" + simpleName) || // Inequality check
                    content.contains(" " + simpleName + " ") || // In expression
                    content.contains("(" + simpleName + ")") || // In parentheses
                    content.contains("[" + simpleName + "]") || // In array access
                    content.contains("{" + simpleName + "}")) { // In block
                    usedSymbols.add(symbol);
                    logger.debug("Found field usage: {}", symbol);
                }
            }
        }
    }

    private void extractImportedClasses(String content) {
        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            String importedClass = importMatcher.group(1);
            if (importedClass.endsWith(".*")) {
                String packagePrefix = importedClass.substring(0, importedClass.length() - 2);
                for (String symbol : allSymbols) {
                    if (symbol.startsWith(packagePrefix)) {
                        usedSymbols.add(symbol);
                        logger.debug("Found usage through wildcard import: {}", symbol);
                    }
                }
            } else {
                usedSymbols.add(importedClass);
                logger.debug("Found usage through import: {}", importedClass);
            }
        }
    }

    private boolean shouldIgnoreMethod(String methodName) {
        return IGNORE_PATTERNS.stream().anyMatch(pattern -> methodName.matches(pattern));
    }
} 