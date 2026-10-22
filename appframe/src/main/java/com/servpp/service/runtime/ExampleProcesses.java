package com.servpp.service.runtime;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Launches declared example stages, never copies business participants between processes. */
final class ExampleProcesses {
    private ExampleProcesses() {}
    static void run(Properties catalog, int index) throws Exception {
        String prefix = "example." + index + ".";
        String property = catalog.getProperty(prefix + "restart.datasourceProperty");
        if (property == null || property.isBlank()) throw new IllegalArgumentException("Restart datasource property is missing");
        Path output = Path.of(System.getProperty("spp.examples.output", "target/spp-examples"));
        Files.createDirectories(output);
        Path directory = Files.createTempDirectory(output, "restart-");
        String url = System.getProperty(property);
        // Keep the generated file database open within each child process. JDBC
        // connections still close normally; JVM shutdown closes the database
        // before the next stage opens it, without reopening it for every query.
        if (url == null) url = "jdbc:h2:file:" + directory.resolve("storage").toAbsolutePath() + ";DB_CLOSE_DELAY=-1";
        if (url.isBlank() || url.startsWith("jdbc:h2:mem:")) throw new IllegalArgumentException("Restart requires persistent shared storage");
        for (int stage = 0; stage < 2; stage++) {
            Path log = directory.resolve("stage-" + stage + ".log");
            var command = new ArrayList<String>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            for (String key : System.getProperties().stringPropertyNames().stream().sorted().toList()) {
                if (key.equals(property) || !(key.startsWith("java.") || key.startsWith("jdk.") || key.startsWith("sun.")
                    || key.startsWith("user.") || key.startsWith("os.") || key.equals("file.separator")
                    || key.equals("path.separator") || key.equals("line.separator"))) {
                    command.add("-D" + key + "=" + System.getProperty(key));
                }
            }
            command.add("-D" + property + "=" + url);
            command.add("-Dspp.runtime.jdbc.url=" + url);
            command.add("-cp"); command.add(classpath());
            command.add(GeneratedExampleRunner.class.getName());
            command.add("--spp-example-phase"); command.add(Integer.toString(index)); command.add(Integer.toString(stage));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean ended;
            try { ended = process.waitFor(60, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) {
                process.destroyForcibly(); Thread.currentThread().interrupt(); throw interrupted;
            }
            if (!ended) {
                process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("EXAMPLE_RESTART_TIMEOUT: stage " + stage + ", log " + log);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("EXAMPLE_RESTART_FAILED: stage " + stage + ", log " + log + "\n" + Files.readString(log));
            }
        }
        System.out.println("RESTART " + catalog.getProperty(prefix + "service") + ": two fresh JVM stages, logs " + directory);
    }
    private static String classpath() throws Exception {
        var entries = new LinkedHashSet<String>();
        for (ClassLoader loader = Thread.currentThread().getContextClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) if ("file".equals(url.getProtocol())) entries.add(Path.of(url.toURI()).toString());
            }
        }
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) entries.add(entry);
        }
        return String.join(File.pathSeparator, entries);
    }
}
