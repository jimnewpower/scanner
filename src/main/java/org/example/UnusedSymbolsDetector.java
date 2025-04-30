package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility to detect unused symbols in a Spring + JSF application.
 * Scans Java files, XHTML files, and Spring configuration files.
 */
public class UnusedSymbolsDetector {

    private final String baseDir;
    private final Set<String> allJavaClasses = new HashSet<>();
    private final Set<String> allJavaMethods = new HashSet<>();
    private final Set<String> allJavaFields = new HashSet<>();
    private final Set<String> allBeans = new HashSet<>();

    private final Set<String> usedJavaClasses = new HashSet<>();
    private final Set<String> usedJavaMethods = new HashSet<>();
    private final Set<String> usedJavaFields = new HashSet<>();
    private final Set<String> usedBeans = new HashSet<>();

    private final Set<String> jsf = new HashSet<>();
    private final Set<String> jsfUsed = new HashSet<>();

    // Ignore patterns
    private final List<String> ignorePatterns = Arrays.asList(
            "main",                // Main methods
            "equals",              // Common methods
            "hashCode",
            "toString",
            "init",
            "destroy",
            "get[A-Z].*",          // Getters
            "set[A-Z].*",          // Setters
            "is[A-Z].*",           // Boolean getters
            "on[A-Z].*",           // Event handlers
            ".*Test",              // Test classes
            ".*Exception",         // Exceptions
            ".*\\$.*"              // Inner classes
    );

    public UnusedSymbolsDetector(String baseDir) {
        this.baseDir = baseDir;
    }

    public void analyze() throws IOException {
        System.out.println("Starting analysis in directory: " + baseDir);

        // Step 1: Scan for all symbol definitions
        scanJavaFiles();
        scanSpringConfigs();
        scanJsfComponents();

        // Step 2: Scan for symbol usages
        scanForJavaUsages();
        scanForJsfUsages();

        // Step 3: Generate reports
        reportUnusedSymbols();
    }

    private void scanJavaFiles() throws IOException {
        System.out.println("Scanning Java files for symbol definitions...");

        try (Stream<Path> paths = Files.walk(Paths.get(baseDir))) {
            List<File> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            System.out.println("Found " + javaFiles.size() + " Java files");

            for (File file : javaFiles) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

                // Extract package and class name
                String packageName = extractPackageName(content);
                String className = extractClassName(content, file.getName());
                String fullClassName = packageName + "." + className;

                // Skip test classes
                if (fullClassName.contains("Test") || file.getPath().contains("/test/")) {
                    continue;
                }

                // Add class to all classes
                allJavaClasses.add(fullClassName);

                // Extract methods
                extractMethods(content, fullClassName);

                // Extract fields
                extractFields(content, fullClassName);

                // Check for Spring beans
                if (content.contains("@Component") ||
                        content.contains("@Service") ||
                        content.contains("@Repository") ||
                        content.contains("@Controller") ||
                        content.contains("@RestController") ||
                        content.contains("@Bean") ||
                        content.contains("@Named")) {
                    allBeans.add(fullClassName);
                }

                // Check for JSF managed beans
                if (content.contains("@ManagedBean") || content.contains("@Named")) {
                    extractJsfManagedBeans(content, fullClassName);
                }
            }
        }

        System.out.println("Found " + allJavaClasses.size() + " Java classes");
        System.out.println("Found " + allJavaMethods.size() + " Java methods");
        System.out.println("Found " + allJavaFields.size() + " Java fields");
        System.out.println("Found " + allBeans.size() + " Spring/CDI beans");
        System.out.println("Found " + jsf.size() + " JSF bean properties");
    }

    private String extractPackageName(String content) {
        Pattern pattern = Pattern.compile("package\\s+([\\w.]+);");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractClassName(String content, String fileName) {
        String simpleClassName = fileName.replace(".java", "");

        // Handle inner classes
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(content);
        if (classMatcher.find()) {
            simpleClassName = classMatcher.group(1);
        }

        return simpleClassName;
    }

    private void extractMethods(String content, String fullClassName) {
        // Regular expression to match method definitions
        Pattern methodPattern = Pattern.compile("(public|protected|static|\\s) +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *\\([^\\)]*\\) *(\\{?|[^;])");
        Matcher methodMatcher = methodPattern.matcher(content);

        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(2);

            // Skip methods matching ignore patterns
            if (shouldIgnoreMethod(methodName)) {
                continue;
            }

            allJavaMethods.add(fullClassName + "." + methodName);
        }
    }

    private void extractFields(String content, String fullClassName) {
        // Regular expression to match field definitions
        Pattern fieldPattern = Pattern.compile("(protected|public|static|final|\\s)* +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *;");
        Matcher fieldMatcher = fieldPattern.matcher(content);

        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(2);
            allJavaFields.add(fullClassName + "." + fieldName);
        }
    }

    private void extractJsfManagedBeans(String content, String fullClassName) {
        // For JSF beans, we need to extract the bean name and its properties
        String beanName = extractBeanName(content, fullClassName);

        // Extract getter/setter methods to identify properties
        Pattern getterPattern = Pattern.compile("public\\s+[\\w\\<\\>\\[\\]]+\\s+get(\\w+)\\(\\)");
        Matcher getterMatcher = getterPattern.matcher(content);

        while (getterMatcher.find()) {
            String propertyName = getterMatcher.group(1);
            propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
            jsf.add(beanName + "." + propertyName);
        }
    }

    private String extractBeanName(String content, String fullClassName) {
        // Try to extract bean name from annotations
        Pattern namedPattern = Pattern.compile("@(Named|ManagedBean)\\(\"?(\\w+)\"?\\)");
        Matcher namedMatcher = namedPattern.matcher(content);

        if (namedMatcher.find()) {
            return namedMatcher.group(2);
        }

        // If no explicit name, use class name with first letter lowercase
        String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        return Character.toLowerCase(simpleClassName.charAt(0)) + simpleClassName.substring(1);
    }

    private void scanSpringConfigs() throws IOException {
        System.out.println("Scanning Spring configuration files...");

        try (Stream<Path> paths = Files.walk(Paths.get(baseDir))) {
            List<File> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            for (File file : xmlFiles) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

                // Check if it's a Spring configuration file
                if (content.contains("<beans") || content.contains("http://www.springframework.org")) {
                    extractSpringBeans(content);
                }
            }
        }
    }

    private void extractSpringBeans(String content) {
        // Extract bean definitions
        Pattern beanPattern = Pattern.compile("<bean\\s+(?:.*?)class\\s*=\\s*\"([\\w.]+)\"");
        Matcher beanMatcher = beanPattern.matcher(content);

        while (beanMatcher.find()) {
            String className = beanMatcher.group(1);
            allBeans.add(className);
        }
    }

    private void scanJsfComponents() throws IOException {
        System.out.println("Scanning XHTML files for JSF components...");

        try (Stream<Path> paths = Files.walk(Paths.get(baseDir))) {
            List<File> xhtmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xhtml"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            System.out.println("Found " + xhtmlFiles.size() + " XHTML files");
        }
    }

    private void scanForJavaUsages() throws IOException {
        System.out.println("Scanning for Java symbol usages...");

        try (Stream<Path> paths = Files.walk(Paths.get(baseDir))) {
            List<File> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            for (File file : javaFiles) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

                // Process imports to mark classes as used
                extractImportedClasses(content);

                // Scan for method calls
                scanForMethodCalls(content);

                // Scan for field accesses
                scanForFieldAccesses(content);

                // Scan for bean injections
                scanForBeanInjections(content);
            }
        }
    }

    private void extractImportedClasses(String content) {
        Pattern importPattern = Pattern.compile("import\\s+([\\w.]+);");
        Matcher importMatcher = importPattern.matcher(content);

        while (importMatcher.find()) {
            String importedClass = importMatcher.group(1);

            // Handle wildcard imports
            if (importedClass.endsWith(".*")) {
                String packagePrefix = importedClass.substring(0, importedClass.length() - 2);
                for (String className : allJavaClasses) {
                    if (className.startsWith(packagePrefix)) {
                        usedJavaClasses.add(className);
                    }
                }
            } else {
                usedJavaClasses.add(importedClass);
            }
        }
    }

    private void scanForMethodCalls(String content) {
        // This is a simplified approach - a full parser would be more accurate
        for (String method : allJavaMethods) {
            String methodName = method.substring(method.lastIndexOf('.') + 1);

            // Look for method calls like "object.methodName(" or "ClassName.methodName("
            if (content.contains("." + methodName + "(")) {
                usedJavaMethods.add(method);
            }
        }
    }

    private void scanForFieldAccesses(String content) {
        for (String field : allJavaFields) {
            String fieldName = field.substring(field.lastIndexOf('.') + 1);

            // Look for field accesses like "object.fieldName" or "this.fieldName"
            if (content.contains("." + fieldName + " ") ||
                    content.contains("." + fieldName + ";") ||
                    content.contains("." + fieldName + ")") ||
                    content.contains("." + fieldName + ",")) {
                usedJavaFields.add(field);
            }
        }
    }

    private void scanForBeanInjections(String content) {
        // Look for @Autowired, @Inject annotations and constructor injections
        for (String bean : allBeans) {
            String simpleClassName = bean.substring(bean.lastIndexOf('.') + 1);

            if (content.contains("@Autowired") || content.contains("@Inject")) {
                if (content.contains(simpleClassName + " ") || content.contains(simpleClassName + ";")) {
                    usedBeans.add(bean);
                }
            }
        }
    }

    private void scanForJsfUsages() throws IOException {
        System.out.println("Scanning for JSF component usages...");

        try (Stream<Path> paths = Files.walk(Paths.get(baseDir))) {
            List<File> xhtmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xhtml"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            for (File file : xhtmlFiles) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

                // Look for EL expressions like #{bean.property}
                Pattern elPattern = Pattern.compile("\\#\\{([\\w.]+)");
                Matcher elMatcher = elPattern.matcher(content);

                while (elMatcher.find()) {
                    String elExpression = elMatcher.group(1);
                    jsfUsed.add(elExpression);

                    // If this is a bean property reference, mark the bean as used
                    if (elExpression.contains(".")) {
                        String beanName = elExpression.substring(0, elExpression.indexOf('.'));
                        for (String bean : allBeans) {
                            String simpleBeanName = bean.substring(bean.lastIndexOf('.') + 1);
                            simpleBeanName = Character.toLowerCase(simpleBeanName.charAt(0)) + simpleBeanName.substring(1);

                            if (simpleBeanName.equals(beanName)) {
                                usedBeans.add(bean);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void reportUnusedSymbols() {
        System.out.println("\n=== UNUSED SYMBOLS REPORT ===\n");

        // Report unused classes
        Set<String> unusedClasses = new HashSet<>(allJavaClasses);
        unusedClasses.removeAll(usedJavaClasses);

        System.out.println("Found " + unusedClasses.size() + " unused Java classes:");
        for (String unusedClass : unusedClasses) {
            System.out.println("  - " + unusedClass);
        }

        // Report unused methods
        Set<String> unusedMethods = new HashSet<>(allJavaMethods);
        unusedMethods.removeAll(usedJavaMethods);

        System.out.println("\nFound " + unusedMethods.size() + " unused Java methods:");
        for (String unusedMethod : unusedMethods) {
            System.out.println("  - " + unusedMethod);
        }

        // Report unused fields
        Set<String> unusedFields = new HashSet<>(allJavaFields);
        unusedFields.removeAll(usedJavaFields);

        System.out.println("\nFound " + unusedFields.size() + " unused Java fields:");
        for (String unusedField : unusedFields) {
            System.out.println("  - " + unusedField);
        }

        // Report unused beans
        Set<String> unusedBeans = new HashSet<>(allBeans);
        unusedBeans.removeAll(usedBeans);

        System.out.println("\nFound " + unusedBeans.size() + " unused Spring/CDI beans:");
        for (String unusedBean : unusedBeans) {
            System.out.println("  - " + unusedBean);
        }

        // Report unused JSF bean properties
        Set<String> unusedJsf = new HashSet<>(jsf);
        unusedJsf.removeAll(jsfUsed);

        System.out.println("\nFound " + unusedJsf.size() + " unused JSF bean properties:");
        for (String unusedProperty : unusedJsf) {
            System.out.println("  - " + unusedProperty);
        }
    }

    private boolean shouldIgnoreMethod(String methodName) {
        for (String pattern : ignorePatterns) {
            if (methodName.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java UnusedSymbolsDetector <baseDirectory>");
            System.exit(1);
        }

        try {
            UnusedSymbolsDetector detector = new UnusedSymbolsDetector(args[0]);
            detector.analyze();
        } catch (IOException e) {
            System.err.println("Error analyzing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}