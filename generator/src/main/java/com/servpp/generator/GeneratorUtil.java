package com.servpp.generator;

import com.servpp.generator.db.DDLGenerator;
import com.servpp.generator.openapi.MustacheClassWriter;
import com.servpp.generator.openapi.OpenApiGenerator;
import com.servpp.lang.AppCompiler;
import com.servpp.lang.lint.RedundancyLint;
import com.servpp.lang.SppCompiler;
import com.servpp.lang.general.SppGeneralHandler;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author lidong@date 2023-10-30@version 1.0
 */
public class GeneratorUtil {
    public static void main(String[] args) throws IOException, MojoExecutionException, MojoFailureException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: GeneratorUtil <source-directory> <yaml-directory> <java-directory> <base-package>");
        }
        execute(new File(args[0]), new File(args[1]), new File(args[2]), args[3]);
    }

    public static void execute(File sppSourceDirectory, File yamlOutputDirectory, File javaOutputDirectory,
                               String basePackage) throws MojoExecutionException, MojoFailureException {
        execute(sppSourceDirectory, yamlOutputDirectory, javaOutputDirectory, basePackage, null);
    }

    public static void execute(File sppSourceDirectory, File yamlOutputDirectory, File javaOutputDirectory,
                               String basePackage, String physicalProfile)
            throws MojoExecutionException, MojoFailureException {
        execute(sppSourceDirectory,yamlOutputDirectory,javaOutputDirectory,basePackage,physicalProfile,project -> {});
    }

    /** Inspect the parsed, selected project before any generated artifact is written. */
    public static void execute(File sppSourceDirectory, File yamlOutputDirectory, File javaOutputDirectory,
                               String basePackage, String physicalProfile, java.util.function.Consumer<SppProject> inspection)
            throws MojoExecutionException, MojoFailureException {
        execute(sppSourceDirectory, yamlOutputDirectory, javaOutputDirectory, basePackage, physicalProfile, inspection, null);
    }

    public static void execute(File sppSourceDirectory, File yamlOutputDirectory, File javaOutputDirectory,
                               String basePackage, String physicalProfile, java.util.function.Consumer<SppProject> inspection,
                               java.util.function.BiConsumer<List<RedundancyLint.Source>, RedundancyLint.Finding> diagnostics)
            throws MojoExecutionException, MojoFailureException {
        SppProject project = new SppProject();
        List<RedundancyLint.Source> sources = new ArrayList<>();
        SppPlugin.IAntlrCompiler parse = (file, model) -> {
            String name = file.getName();
            com.servpp.lang.AntlrCompiler compiler = name.endsWith(".spp") || name.endsWith(".enum")
                    ? new SppCompiler(file, model) : new AppCompiler(file, model);
            String path = sppSourceDirectory.toPath().relativize(file.toPath()).toString();
            return compiler.compile(tree -> sources.add(new RedundancyLint.Source(path, tree)));
        };
        var names = new com.servpp.lang.ParticipantNames();
        record SourceFile(File file, SppCompiler compiler, com.servpp.lang.antlr.SppParser.ProgramContext tree) { }
        List<SourceFile> sppFiles = new ArrayList<>();
        for (String suffix : List.of(".enum", ".spp")) {
            for (File file : sourceFiles(sppSourceDirectory, suffix, ignored -> true, Comparator.comparing(File::getName))) {
                var compiler = new SppCompiler(file, project);
                try {
                    var tree = compiler.parse();
                    names.add(tree);
                    sppFiles.add(new SourceFile(file, compiler, tree));
                } catch (IOException failure) {
                    throw new MojoExecutionException("Failed to compile S++ source file " + file + ".", failure);
                }
            }
        }
        for (var source : sppFiles) source.compiler().declareParticipantTypes(source.tree());
        java.util.Map<SppDomain, SppGeneralHandler> sppHandlers = new java.util.LinkedHashMap<>();
        for (var source : sppFiles) {
            var handler = source.compiler().compile(source.tree(), names);
            sources.add(new RedundancyLint.Source(sppSourceDirectory.toPath().relativize(source.file().toPath()).toString(),
                    source.tree()));
            if (source.file().getName().endsWith(".spp")) {
                sppHandlers.put(handler.getSppDomain(), handler);
            }
        }
        sppHandlers.forEach((domain, handler) -> {
            domain.dealEntityToRoleMaps();
            domain.generateDefaultServices(handler);
        });
        //parse app file
        List<ParseTreeListener> appTreeListeners = compileAntlr(sppSourceDirectory, project, ".app", parse);
        //parse rdb file
        appTreeListeners = compileAntlr(sppSourceDirectory, project, ".rdb", parse);
        //parse phy file
        appTreeListeners = compilePhysicalAntlr(sppSourceDirectory, project, physicalProfile, parse);
        var findings = RedundancyLint.analyze(sources, diagnostics);
        var log = new org.apache.maven.plugin.logging.SystemStreamLog();
        boolean strict = Boolean.getBoolean("spp.lint.strict");
        var counts = findings.stream().collect(java.util.stream.Collectors.groupingBy(
                RedundancyLint.Finding::code, java.util.stream.Collectors.counting()));
        String previousCode = "";
        for (var finding : findings) {
            if (!finding.code().equals(previousCode)) {
                previousCode = finding.code();
                String heading = previousCode + " (" + counts.get(previousCode) + ")";
                if (strict) log.error(heading); else log.warn(heading);
            }
            if (strict || RedundancyLint.ERROR_CODES.contains(finding.code())) log.error(finding.format());
            else log.warn(finding.format());
        }
        long hardErrors = findings.stream().filter(finding -> RedundancyLint.ERROR_CODES.contains(finding.code())).count();
        if (hardErrors > 0)
            throw new MojoFailureException("S++ participant rules reported " + hardErrors + " error(s); see the "
                    + String.join(", ", RedundancyLint.ERROR_CODES) + " findings above.");
        if (strict && diagnostics == null && !findings.isEmpty())
            throw new MojoFailureException("Strict redundancy checks reported " + findings.size() + " error(s).");
        List<String> physicalSourceFileNames = physicalSourceFileNames(sppSourceDirectory, physicalProfile);
        List<String> deploymentErrors = com.servpp.lang.models.DomainDeploymentValidator.validate(project);
        if (!deploymentErrors.isEmpty()) throw new MojoFailureException("S++ domain deployment validation failed:\n"
                + String.join("\n", deploymentErrors));

        java.util.Objects.requireNonNull(inspection,"inspection").accept(project);

        URL ddlMustacheUrl = SppPlugin.class.getResource("/ddl.mustache");
        java.util.Set<String> reportedParticipantWarnings = new java.util.HashSet<>();
        // Split sources share one domain model; validate and emit that model once.
        // Standalone specs retain the final source basename; application specs use the domain name.
        java.util.Map<String, SppGeneralHandler> domainHandlers = new java.util.LinkedHashMap<>();
        sppHandlers.forEach((domain, handler) -> domainHandlers.put(domain.getName(), handler));
        boolean multipleDomains = domainHandlers.size() > 1 || project.getSppDomainMap().values().stream()
                .anyMatch(domain -> !domain.getRuleBlock().getApplicationDeployments().isEmpty());
        if (multipleDomains) writeApplicationDeployments(project, javaOutputDirectory);
        for(ParseTreeListener parseTreeListener: domainHandlers.values()){
            SppGeneralHandler sppGeneralHandler = (SppGeneralHandler) parseTreeListener;
            SppDomain sppDomain = sppGeneralHandler.getSppDomain();
            sppDomain.dealAnnotations(sppDomain.getRuleBlock().getAppAnnotationList());
            sppDomain.checkSemanticFinally(sppGeneralHandler);
            List<String> validationErrors = sppDomain.validateAppDeclarations();
            if (!validationErrors.isEmpty()) {
                throw new MojoFailureException("S++ transaction validation failed:\n"
                        + String.join("\n", validationErrors));
            }
            for (String warning : sppDomain.unusedParticipantWarnings()) {
                if (reportedParticipantWarnings.add(sppDomain.getName() + ":" + warning)) {
                    modelWarning(sources, sppDomain.getName(), warning, "SPP_MODEL_WARNING");
                }
            }
            for (String warning : sppDomain.migrationWarnings()) {
                modelWarning(sources, sppDomain.getName(), warning, "SPP_MIGRATION_WARNING");
            }
            for (String warning : sppDomain.numericRoundingWarnings()) {
                modelWarning(sources, sppDomain.getName(), warning, "SPP_NUMERIC_ROUNDING");
            }
            List<String> physicalCatalogErrors = sppDomain.validatePhysicalRuntimeCatalogGeneration();
            if (!physicalCatalogErrors.isEmpty()) {
                throw new MojoFailureException("S++ physical runtime catalog validation failed:\n"
                        + String.join("\n", physicalCatalogErrors));
            }
            DDLGenerator ddlGenerator = new DDLGenerator(sppDomain,null);
            List<String> mapperErrors = ddlGenerator.validateMapperDeclarations();
            if (!mapperErrors.isEmpty()) {
                throw new MojoFailureException("S++ transaction validation failed:\n"
                        + String.join("\n", mapperErrors));
            }
            try {
                OpenApiGenerator.createNumericCodegenConfiguration(sppDomain, yamlOutputDirectory);
                MustacheClassWriter.generateFile(ddlMustacheUrl,ddlGenerator,yamlOutputDirectory, multipleDomains ? "sql/" + sppDomain.getName() + "/ddl.sql" : "sql/ddl.sql");
                MybatisGenerator mybatisGenerator = new MybatisGenerator(sppDomain,ddlGenerator);
                mybatisGenerator.generateManager(GeneratorUtil.class.getResource("/mustache/mybatis/manager.mustache")
                        , javaOutputDirectory,basePackage);
                mybatisGenerator.generateMapper(GeneratorUtil.class.getResource("/mustache/mybatis/mapper.mustache")
                        ,javaOutputDirectory.getParentFile(),basePackage);

                openApi(sppDomain, sppGeneralHandler.getAntlrFile(), yamlOutputDirectory, javaOutputDirectory,
                        basePackage, ddlGenerator, physicalProfile, physicalSourceFileNames, multipleDomains);
            } catch (URISyntaxException | IOException e) {
                throw new MojoExecutionException("Failed to generate S++ artifacts for domain "
                        + sppDomain.getName() + " from " + sppGeneralHandler.getAntlrFile() + ".", e);
            }

        }
    }

    private static final java.util.regex.Pattern MODEL_CODE = java.util.regex.Pattern.compile("^\\[(SPP_[A-Z_]+)\\] (.*)$");

    private static void modelWarning(List<RedundancyLint.Source> sources, String domain, String warning, String code) {
        var matcher = MODEL_CODE.matcher(warning);
        String detail = warning;
        if (matcher.matches()) {
            code = matcher.group(1);
            detail = matcher.group(2);
        }
        String hint = switch (code) {
            case "SPP_UNUSED_PARTICIPANT" -> "Review callers and remove the unused participant from the service signature.";
            case "SPP_REDUNDANT_UNIQUE" -> "Remove unique from this primary-key lookup.";
            default -> "Review the declaration and its deployment settings.";
        };
        new org.apache.maven.plugin.logging.SystemStreamLog().warn(domain + ": [" + code + "] " + detail);
        if (!com.servpp.lang.lint.CompilerDiagnostics.observed()) return;
        var location = java.util.regex.Pattern.compile("^(.+?):(\\d+):(\\d+)\\s+(.+)$").matcher(detail);
        if (location.matches()) {
            String message = location.group(4);
            com.servpp.lang.lint.CompilerDiagnostics.report(new com.servpp.lang.lint.RepairDiagnostic(code, "warning",
                    location.group(1), Integer.parseInt(location.group(2)), Integer.parseInt(location.group(3)),
                    message, message, code.equals("SPP_NUMERIC_ROUNDING") ? "Declare a rounding mode." : hint,
                    List.of(), List.of(), false));
            return;
        }
        String file = "";
        int line = 0, column = 0;
        for (var source : sources) {
            if (!(source.tree() instanceof com.servpp.lang.antlr.SppParser.ProgramContext spp)
                    || !spp.servicedomain().domainname().getText().equals(domain)) continue;
            for (var unit : spp.compilationUnits().compilationUnit()) {
                var definition = unit.scenario() != null ? unit.scenario().serviceDefinition()
                        : unit.atomicservice() != null ? unit.atomicservice().serviceDefinition() : null;
                if (definition == null) continue;
                var token = definition.Identifier().getSymbol();
                String name = com.servpp.lang.general.NameUtil.firstToLowerCase(token.getText(), true);
                if (detail.startsWith(name + ":") || detail.startsWith("Scenario " + name + " ")) {
                    file = source.path(); line = token.getLine(); column = token.getCharPositionInLine() + 1;
                    break;
                }
            }
            if (!file.isEmpty()) break;
        }
        com.servpp.lang.lint.CompilerDiagnostics.report(new com.servpp.lang.lint.RepairDiagnostic(code, "warning",
                file, line, column, detail, detail, hint, List.of(), List.of(), false));
    }

    private static void writeApplicationDeployments(SppProject project, File javaRoot) throws MojoExecutionException {
        var properties = new java.util.Properties();
        int index = 0;
        for (var domain : project.getSppDomainMap().values().stream().sorted(java.util.Comparator.comparing(SppDomain::getName)).toList()) {
            for (var application : domain.getRuleBlock().getApplicationDeployments().entrySet()) {
                String prefix = "application." + index++ + ".";
                properties.setProperty(prefix + "name", application.getKey());
                properties.setProperty(prefix + "entryDomain", domain.getName());
                properties.setProperty(prefix + "domains", String.join(",", application.getValue()));
                var rules = domain.getRuleBlock();
                if (rules.getDeploymentEnvironment() != null) {
                    properties.setProperty(prefix + "environment", rules.getDeploymentEnvironment());
                    properties.setProperty(prefix + "transactionMode", rules.getDeploymentTransactionMode());
                    if (rules.getDefaultUpdateResource() != null) properties.setProperty(prefix + "transactionResource", rules.getDefaultUpdateResource());
                    if (rules.getDeploymentQuantumSystem() != null) properties.setProperty(prefix + "quantumSystem", rules.getDeploymentQuantumSystem());
                    if (rules.getDeploymentQuantumResource() != null) properties.setProperty(prefix + "quantumResource", rules.getDeploymentQuantumResource());
                }
            }
        }
        properties.setProperty("sourceDomains", String.join(",", project.getSppDomainMap().keySet().stream().sorted().toList()));
        properties.setProperty("application.count", Integer.toString(index));
        File destination = new File(javaRoot.getParentFile(), "resources/META-INF/spp/applications.properties");
        if (!destination.getParentFile().isDirectory() && !destination.getParentFile().mkdirs())
            throw new MojoExecutionException("Cannot create application catalog directory");
        try (var output = new java.io.FileOutputStream(destination)) {
            storeStable(properties, output, "Generated APP local application deployments");
        } catch (IOException failure) { throw new MojoExecutionException("Cannot write application deployments", failure); }
    }

    /**
     * Writes properties like {@link java.util.Properties#store} without the date stamp it puts on
     * the line after the comment, so equal sources generate byte-identical files (reproducible
     * jars). The comment must be a single line. Line separators are normalized to {@code \n}.
     */
    public static void storeStable(java.util.Properties properties, OutputStream output, String comment)
            throws IOException {
        if (comment == null || comment.isBlank() || comment.contains("\n") || comment.contains("\r")) {
            throw new IllegalArgumentException("comment must be a single non-blank line");
        }
        var buffer = new ByteArrayOutputStream();
        properties.store(buffer, comment);
        String[] lines = buffer.toString(java.nio.charset.StandardCharsets.ISO_8859_1).replace("\r\n", "\n").split("\n", -1);
        StringBuilder stable = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == 1 && lines[i].startsWith("#")) continue; // the date line
            if (i == lines.length - 1 && lines[i].isEmpty()) break;
            stable.append(lines[i]).append('\n');
        }
        output.write(stable.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    private static List<ParseTreeListener> compilePhysicalAntlr(File sppSourceDirectory, SppProject sppProject,
                                                                String physicalProfile,
                                                                SppPlugin.IAntlrCompiler antlrCompiler)
            throws MojoExecutionException, MojoFailureException {
        boolean matchedProfile = physicalProfile == null || physicalProfile.isBlank()
                || hasMatchingPhysicalProfile(sppSourceDirectory, physicalProfile);
        List<ParseTreeListener> listeners = compileAntlr(sppSourceDirectory, sppProject, ".phy",
                file -> matchesPhysicalProfile(file, physicalProfile),
                physicalFileComparator(physicalProfile), antlrCompiler);
        if (!matchedProfile) {
            throw new MojoFailureException("S++ physical profile " + physicalProfile
                    + " does not match any .phy file in " + sppSourceDirectory + ".");
        }
        return listeners;
    }

    private static boolean hasMatchingPhysicalProfile(File sourceDirectory, String profile)
            throws MojoExecutionException {
        return sourceFiles(sourceDirectory, ".phy", file -> matchesExplicitPhysicalProfile(file, profile),
                physicalFileComparator(profile)).length > 0;
    }

    private static boolean matchesPhysicalProfile(File file, String physicalProfile) {
        return com.servpp.lang.general.PhysicalProfiles.matches(file.getName(), physicalProfile);
    }

    private static boolean matchesExplicitPhysicalProfile(File file, String physicalProfile) {
        return com.servpp.lang.general.PhysicalProfiles.matchesExplicit(file.getName(), physicalProfile);
    }

    private static boolean isProfileQualifiedPhysicalFile(File file) {
        return com.servpp.lang.general.PhysicalProfiles.isQualified(file.getName());
    }

    private static List<String> physicalSourceFileNames(File sppSourceDirectory, String physicalProfile)
            throws MojoExecutionException {
        File[] files = sourceFiles(sppSourceDirectory, ".phy",
                file -> matchesPhysicalProfile(file, physicalProfile), physicalFileComparator(physicalProfile));
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(sppSourceDirectory.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/'));
        }
        return List.copyOf(names);
    }

    private static Comparator<File> physicalFileComparator(String physicalProfile) {
        if (physicalProfile == null || physicalProfile.isBlank()) {
            return Comparator.comparing(File::getName);
        }
        return Comparator
                .comparingInt((File file) -> matchesExplicitPhysicalProfile(file, physicalProfile) ? 1 : 0)
                .thenComparing(File::getName);
    }

    private static List<ParseTreeListener> compileAntlr(File sppSourceDirectory, SppProject sppProject, String suffix,
                                                        SppPlugin.IAntlrCompiler antlrCompiler)
            throws MojoExecutionException {
        return compileAntlr(sppSourceDirectory, sppProject, suffix, file -> true, antlrCompiler);
    }

    private static List<ParseTreeListener> compileAntlr(File sppSourceDirectory, SppProject sppProject, String suffix,
                                                        FileFilter extraFilter,
                                                        SppPlugin.IAntlrCompiler antlrCompiler)
            throws MojoExecutionException {
        return compileAntlr(sppSourceDirectory, sppProject, suffix, extraFilter,
                Comparator.comparing(File::getName), antlrCompiler);
    }

    private static List<ParseTreeListener> compileAntlr(File sppSourceDirectory, SppProject sppProject, String suffix,
                                                        FileFilter extraFilter, Comparator<File> fileComparator,
                                                        SppPlugin.IAntlrCompiler antlrCompiler)
            throws MojoExecutionException {
        File[] files = sourceFiles(sppSourceDirectory, suffix, extraFilter, fileComparator);
        List<ParseTreeListener> list = new ArrayList<>();
        for (File sppFile: files){
            try {
                ParseTreeListener parseTreeListener = antlrCompiler.parse(sppFile, sppProject);
                list.add(parseTreeListener);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to compile S++ source file " + sppFile + ".", e);
            }
        }
        return list;
    }


    /** Domains may live in subdirectories; keep the existing filename order with a stable path tie-break. */
    private static File[] sourceFiles(File directory, String suffix, FileFilter filter, Comparator<File> order)
            throws MojoExecutionException {
        if (directory == null || !directory.isDirectory())
            throw new MojoExecutionException("S++ source directory is not a readable directory: " + directory);
        try (var paths = java.nio.file.Files.walk(directory.toPath())) {
            return paths.filter(java.nio.file.Files::isRegularFile).map(java.nio.file.Path::toFile)
                    .filter(file -> file.getName().endsWith(suffix) && filter.accept(file))
                    .sorted(order.thenComparing(file -> directory.toPath().relativize(file.toPath()).toString()))
                    .toArray(File[]::new);
        } catch (IOException | java.io.UncheckedIOException failure) {
            throw new MojoExecutionException("Cannot list S++ source directory " + directory + ".", failure);
        }
    }


    private static void openApi(SppDomain sppDomain, File sppFile, File yamlOutPath,
                                File javaGenRoot, String basePackage,
                                DDLGenerator ddlGenerator, String physicalProfile,
                                List<String> physicalSourceFileNames, boolean multipleDomains) throws IOException, URISyntaxException {
        String domainName = sppDomain.getName();
        File domainPath = new File(new File(javaGenRoot, basePackage.replaceAll("\\.",File.separator)), domainName);
        if (!domainPath.exists())
            domainPath.mkdirs();
        OpenApiGenerator openApiGenerator = new OpenApiGenerator();
        if (multipleDomains) openApiGenerator.setCatalogNamespace(domainName);
        OpenAPI openAPI = openApiGenerator.generate(sppDomain,
                domainPath, basePackage);
//        Template template = Mustache.compiler().compile(new InputStreamReader(mustache));
        if(!yamlOutPath.exists())
            yamlOutPath.mkdirs();
        File outFile = new File(yamlOutPath, multipleDomains ? domainName + ".spp.yaml" : sppFile.getName() + ".yaml");
        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileOutputStream(outFile));
            String yamlText = Yaml.pretty().writeValueAsString(openAPI);
            out.println(yamlText);
//            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
//            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//
//// Write object as YAML file
//            mapper.writeValue(outFile, openAPI);

            //            out = new PrintWriter(System.out);
//            out.println(CodeFormator.formatCode(template.execute(openAPI)));
        }finally {
            out.close();
        }
        openApiGenerator.createJavaSpringMustache(yamlOutPath);
        openApiGenerator.createServicesProperties(javaGenRoot);
        openApiGenerator.createConstantProperties(sppDomain, javaGenRoot);
        openApiGenerator.createPhysicalRuntimeProperties(sppDomain, javaGenRoot,
                physicalProfile, physicalSourceFileNames);
        openApiGenerator.createAppDdlProperties(ddlGenerator, javaGenRoot);
        openApiGenerator.createRoleParticipantProperties(sppDomain, javaGenRoot);
        openApiGenerator.createAppSemanticMigrationProperties(sppDomain, javaGenRoot);
    }
}
