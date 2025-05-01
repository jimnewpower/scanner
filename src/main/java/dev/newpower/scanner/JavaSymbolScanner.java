package dev.newpower.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
        // Scan Java files
        List<Path> javaFiles = getFilesByExtension(baseDir, ".java");
        logger.info("Found {} Java files", javaFiles.size());

        // First pass: collect all symbols
        for (Path file : javaFiles) {
            String content = readFileContents(file);
            processJavaFile(content, file);
        }
        logger.debug("All symbols: {}", allSymbols);
        
        // Scan JavaScript files
        List<Path> jsFiles = getFilesByExtension(baseDir, ".js");
        logger.info("Found {} JavaScript files", jsFiles.size());
        for (Path file : jsFiles) {
            String content = readFileContents(file);
            processJsFile(content, file);
        }

        // Scan JSP files
        List<Path> jspFiles = getFilesByExtension(baseDir, ".jsp");
        logger.info("Found {} JSP files", jspFiles.size());
        for (Path file : jspFiles) {
            String content = readFileContents(file);
            processJspFile(content, file);
        }

        // Second pass: scan for usages
        for (Path file : javaFiles) {
            String content = readFileContents(file);
            scanForUsages(content, file);
        }
        for (Path file : jsFiles) {
            String content = readFileContents(file);
            scanForUsages(content, file);
        }
        for (Path file : jspFiles) {
            String content = readFileContents(file);
            scanForUsages(content, file);
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

        // Check if this is an interface
        boolean isInterface = content.contains("interface " + className);
        
        // Add class/interface to all classes
        int classLine = findLineNumber(content, isInterface ? "interface " + className : "class " + className);
        addSymbolWithLocation(fullClassName, file.toString(), classLine);
        logger.debug("Found {}: {}", isInterface ? "interface" : "class", fullClassName);

        if (isInterface) {
            // Extract interface methods
            extractInterfaceMethods(content, fullClassName, file.toString());
        } else {
            // Extract class methods and fields
            extractMethods(content, fullClassName, file.toString());
            extractFields(content, fullClassName, file.toString());
            
            // Check for interface implementations
            extractInterfaceImplementations(content, fullClassName);
        }
    }

    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractClassName(String content, String fileName) {
        // First try to find class declaration
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }
        
        // Then try to find interface declaration
        Pattern interfacePattern = Pattern.compile("(?:public|private|protected|static|final|abstract|sealed|non-sealed)?\\s+interface\\s+(\\w+)");
        Matcher interfaceMatcher = interfacePattern.matcher(content);
        if (interfaceMatcher.find()) {
            return interfaceMatcher.group(1);
        }
        
        // Fallback to filename
        return fileName.replace(".java", "");
    }

    private void extractMethods(String content, String fullClassName, String filePath) {
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            // Skip private methods
            if (content.substring(0, methodMatcher.start()).contains("private")) {
                logger.debug("Skipping private method: {}.{}", fullClassName, methodName);
                continue;
            }
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
            // Skip private fields
            if (content.substring(0, fieldMatcher.start()).contains("private")) {
                logger.debug("Skipping private field: {}.{}", fullClassName, fieldName);
                continue;
            }
            String fieldSymbol = fullClassName + "." + fieldName;
            int fieldLine = findLineNumber(content, fieldName + ";");
            addSymbolWithLocation(fieldSymbol, filePath, fieldLine);
            logger.debug("Found field: {}", fieldSymbol);
        }
    }

    private void extractInterfaceMethods(String content, String fullClassName, String filePath) {
        // Look for interface method declarations
        Pattern interfaceMethodPattern = Pattern.compile("(?:public|private|protected|static|default)?\\s+(?:[\\w<>\\[\\]]+\\s+)*(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[^;]*)?;");
        Matcher methodMatcher = interfaceMethodPattern.matcher(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            // Skip private methods
            if (content.substring(0, methodMatcher.start()).contains("private")) {
                logger.debug("Skipping private interface method: {}.{}", fullClassName, methodName);
                continue;
            }
            if (!shouldIgnoreMethod(methodName)) {
                String methodSymbol = fullClassName + "." + methodName;
                int methodLine = findLineNumber(content, methodName + "(");
                addSymbolWithLocation(methodSymbol, filePath, methodLine);
                logger.debug("Found interface method: {}", methodSymbol);
            }
        }
    }

    private void scanForUsages(String content, Path currentFile) {
        // Get the package and class name of the current file
        String currentPackage = extractPackageName(content);
        String currentClassName = extractClassName(content, currentFile.getFileName().toString());
        String currentFullClassName = currentPackage + "." + currentClassName;

        // Process imports
        extractImportedClasses(content);

        // Scan for method calls and field accesses
        for (String symbol : allSymbols) {
            // Skip symbols from the current file
            if (symbol.startsWith(currentFullClassName)) {
                continue;
            }

            String simpleName = symbol;
            String className = symbol;
            String packageName = "";

            if (symbol.contains(".")) {
                simpleName = symbol.substring(symbol.lastIndexOf('.') + 1);
                className = symbol.substring(0, symbol.lastIndexOf('.'));
                packageName = className.substring(0, className.lastIndexOf('.'));
            }

            // Look for class usage through imports
            if (content.contains("import " + symbol + ";") || 
                content.contains("import " + symbol + ".*;")) {
                if (allSymbols.contains(symbol)) {
                    usedSymbols.add(symbol);
                    logger.debug("Found class usage through import: {}", symbol);
                }
            }

            // Look for class instantiation
            if (content.contains("new " + simpleName + "(") || 
                content.contains("new " + simpleName + "<") ||
                content.contains("new " + simpleName + "[")) {
                if (allSymbols.contains(symbol)) {
                    usedSymbols.add(symbol);
                    logger.debug("Found class usage through instantiation: {}", symbol);
                }
            }

            // Look for class extension
            if (content.contains("extends " + simpleName) || 
                content.contains("implements " + simpleName)) {
                if (allSymbols.contains(symbol)) {
                    usedSymbols.add(symbol);
                    logger.debug("Found class usage through inheritance: {}", symbol);
                }
            }

            // Look for class references in annotations
            if (content.contains("@" + simpleName) || 
                content.contains("@" + symbol)) {
                if (allSymbols.contains(symbol)) {
                    usedSymbols.add(symbol);
                    logger.debug("Found class usage in annotation: {}", symbol);
                }
            }

            // Look for class references in type declarations
            if (content.contains(": " + simpleName) || 
                content.contains(": " + symbol) ||
                content.contains("<" + simpleName + ">") ||
                content.contains("<" + symbol + ">")) {
                if (allSymbols.contains(symbol)) {
                    usedSymbols.add(symbol);
                    logger.debug("Found class usage in type declaration: {}", symbol);
                }
            }
            
            // Look for method calls
            if (content.contains("." + simpleName + "(") || // Direct method call
                content.contains("super." + simpleName + "(") || // Super method call
                content.contains("this." + simpleName + "(") || // This method call
                content.contains(simpleName + "(")) { // Local method call
                if (allSymbols.contains(symbol)) {
                    usedSymbols.add(symbol);
                    logger.debug("Found method usage: {}", symbol);
                }
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
                if (allSymbols.contains(symbol)) {
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

    private void processJsFile(String content, Path file) {
        // Extract package and class name from file path
        String packageName = extractPackageNameFromPath(file);
        String className = file.getFileName().toString().replace(".js", "");
        String fullClassName = packageName + "." + className;

        // Add class to all classes
        int classLine = findLineNumber(content, "class " + className);
        addSymbolWithLocation(fullClassName, file.toString(), classLine);
        logger.debug("Found JavaScript class: {}", fullClassName);

        // Extract methods
        extractJsMethods(content, fullClassName, file.toString());
    }

    private void processJspFile(String content, Path file) {
        // Extract package and class name from file path
        String packageName = extractPackageNameFromPath(file);
        String className = file.getFileName().toString().replace(".jsp", "");
        String fullClassName = packageName + "." + className;

        // Add class to all classes
        int classLine = findLineNumber(content, "<%@ page");
        addSymbolWithLocation(fullClassName, file.toString(), classLine);
        logger.debug("Found JSP page: {}", fullClassName);

        // Extract Java code from JSP
        extractJspJavaCode(content, fullClassName, file.toString());
    }

    private String extractPackageNameFromPath(Path file) {
        // Convert file path to package name
        String path = file.toString().replace("\\", "/");
        int srcIndex = path.indexOf("/src/");
        if (srcIndex != -1) {
            path = path.substring(srcIndex + 5);
        }
        path = path.substring(0, path.lastIndexOf("/"));
        return path.replace("/", ".");
    }

    private void extractJsMethods(String content, String fullClassName, String filePath) {
        // Look for function declarations
        Pattern functionPattern = Pattern.compile("function\\s+(\\w+)\\s*\\(");
        Matcher functionMatcher = functionPattern.matcher(content);
        while (functionMatcher.find()) {
            String methodName = functionMatcher.group(1);
            String methodSymbol = fullClassName + "." + methodName;
            int methodLine = findLineNumber(content, "function " + methodName);
            addSymbolWithLocation(methodSymbol, filePath, methodLine);
            logger.debug("Found JavaScript method: {}", methodSymbol);
        }

        // Look for class methods
        Pattern classMethodPattern = Pattern.compile("(\\w+)\\s*\\([^)]*\\)\\s*\\{");
        Matcher classMethodMatcher = classMethodPattern.matcher(content);
        while (classMethodMatcher.find()) {
            String methodName = classMethodMatcher.group(1);
            String methodSymbol = fullClassName + "." + methodName;
            int methodLine = findLineNumber(content, methodName + "(");
            addSymbolWithLocation(methodSymbol, filePath, methodLine);
            logger.debug("Found JavaScript class method: {}", methodSymbol);
        }
    }

    private void extractJspJavaCode(String content, String fullClassName, String filePath) {
        // Look for scriptlet code
        Pattern scriptletPattern = Pattern.compile("<%[^%]*%>");
        Matcher scriptletMatcher = scriptletPattern.matcher(content);
        while (scriptletMatcher.find()) {
            String scriptlet = scriptletMatcher.group();
            // Process Java code in scriptlet
            processJavaFile(scriptlet, Path.of(filePath));
        }

        // Look for expression language
        Pattern elPattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher elMatcher = elPattern.matcher(content);
        while (elMatcher.find()) {
            String expression = elMatcher.group(1);
            String symbol = fullClassName + "." + expression;
            int line = findLineNumber(content, "${" + expression);
            addSymbolWithLocation(symbol, filePath, line);
            logger.debug("Found JSP expression: {}", symbol);
        }
    }

    private void extractInterfaceImplementations(String content, String fullClassName) {
        // Look for implements clause
        Pattern implementsPattern = Pattern.compile("implements\\s+([^\\s{]+)");
        Matcher implementsMatcher = implementsPattern.matcher(content);
        while (implementsMatcher.find()) {
            String interfaceList = implementsMatcher.group(1);
            // Split on commas and trim whitespace
            String[] interfaces = interfaceList.split("\\s*,\\s*");
            for (String iface : interfaces) {
                // If interface is in the same package, add package name
                if (!iface.contains(".")) {
                    String packageName = fullClassName.substring(0, fullClassName.lastIndexOf("."));
                    iface = packageName + "." + iface;
                }
                // Only add to usedSymbols if the interface exists in allSymbols
                if (allSymbols.contains(iface)) {
                    usedSymbols.add(iface);
                    logger.debug("Found interface implementation: {} implements {}", fullClassName, iface);
                } else {
                    logger.debug("Skipping unknown interface: {}", iface);
                }
            }
        }
    }
} 