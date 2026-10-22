package com.servpp.service.runtime;

import com.servpp.service.Service;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratedExampleRunnerTest {
    public static class Facts { @com.fasterxml.jackson.annotation.JsonProperty("value") public int value; }
    public static class Header {
        private String token;
        public String getToken() { return token; }
        public void setToken(String value) { token = value; }
    }
    public static class Request {
        private Facts facts; private Header appHeader;
        @com.fasterxml.jackson.annotation.JsonProperty("facts")
        public Facts getFacts() { return facts; }
        public void setFacts(Facts value) { facts = value; }
        public Header getAppHeader() { return appHeader; }
        public void setAppHeader(Header value) { appHeader = value; }
    }
    public static class Sample implements Service, ScopeMetadataProvider {
        public String getName() { return "sample"; }
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return "header"; }
        public String getOutputHeaderName() { return null; }
        public String getIdempotencyKeyFieldName() { return "token"; }
        public Object execute(Object... values) {
            Request request = (Request) values[values.length - 1];
            if (request.getFacts() == null || request.getFacts().value != 0
                    || request.getAppHeader() == null || !request.getAppHeader().getToken().startsWith("sample:")) {
                throw new AssertionError("Runner must create empty participants and a technical header");
            }
            request.getFacts().value = 7;
            if (Boolean.getBoolean("example.fail")) throw new IllegalStateException("DECLARED_ASSERTION_FAILED");
            return null;
        }
    }
    public static class Setup implements Service, ScopeMetadataProvider {
        static int local;
        public String getName() { return "setup"; }
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return "header"; }
        public String getOutputHeaderName() { return null; }
        public String getIdempotencyKeyFieldName() { return "token"; }
        public Object execute(Object... values) {
            Request request = (Request) values[values.length - 1];
            if (local++ != 0 || request.getFacts().value != 0) throw new AssertionError("Fresh stage required");
            if (!"preserved".equals(System.getProperty("fixture.option"))) throw new AssertionError("Application configuration must reach child JVM");
            if (Boolean.getBoolean("example.fail.setup")) throw new IllegalStateException("SETUP_FAILED");
            try (var connection = java.sql.DriverManager.getConnection(System.getProperty("example.fixture.url"));
                 var statement = connection.createStatement()) {
                statement.execute("create table fixture_stage(id int primary key, creator bigint, stored_value int)");
                statement.execute("insert into fixture_stage values (1," + ProcessHandle.current().pid() + ",41)");
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            request.getFacts().value = 41;
            return null;
        }
    }
    public static class Resume extends Setup {
        public String getName() { return "resume"; }
        public Object execute(Object... values) {
            Request request = (Request) values[values.length - 1];
            if (local != 0 || request.getFacts().value != 0) throw new AssertionError("No static or participant state may cross restart");
            if (!"preserved".equals(System.getProperty("fixture.option"))) throw new AssertionError("Application configuration must reach child JVM");
            if (Boolean.getBoolean("example.fail.resume")) throw new IllegalStateException("RESUME_FAILED");
            try (var connection = java.sql.DriverManager.getConnection(System.getProperty("example.fixture.url"));
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("select creator,stored_value from fixture_stage where id=1")) {
                if (!result.next() || result.getLong(1) == ProcessHandle.current().pid() || result.getInt(2) != 41)
                    throw new AssertionError("Another JVM must read actual stored facts");
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            System.out.println("FRESH_PROCESS_STORAGE_VERIFIED");
            return null;
        }
    }
    private record Result(int code, String output) {}
    private Result run(boolean fail, String... arguments) throws Exception {
        Path root = Files.createTempDirectory("spp-runner-catalog");
        Path catalogs = root.resolve("META-INF/spp"); Files.createDirectories(catalogs);
        Files.writeString(catalogs.resolve("examples.properties"), "example.count=1\nexample.0.service=sample\nexample.0.request=" + Request.class.getName() + "\nexample.0.participants=facts\n");
        Files.writeString(catalogs.resolve("services.properties"), "sample=" + Sample.class.getName() + "\n");
        Path output = root.resolve("output.txt");
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        command.add("-Dexample.fail=" + fail);
        command.add("-Dspp.examples.evidenceDirectory=" + root.resolve("evidence")); command.add("-cp");
        command.add(root + java.io.File.pathSeparator + System.getProperty("java.class.path"));
        command.add(GeneratedExampleRunner.class.getName()); command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Runner timed out"); }
        if (arguments.length == 0 || java.util.List.of(arguments).contains("sample")) {
            var evidence = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve("evidence/sample.json").toFile());
            assertEquals(fail ? "FAIL" : "PASS", evidence.path("status").asText());
            assertEquals(7, evidence.path("participants").path("facts").path("value").asInt());
            if (fail) assertEquals("DECLARED_ASSERTION_FAILED", evidence.path("failure").path("message").asText());
            assertTrue(Files.exists(root.resolve("evidence/sample.md")));
        }
        return new Result(process.exitValue(), Files.readString(output));
    }
    @Test public void createsEmptyFactsAndHeaderAndRunsSelectedService() throws Exception {
        var result = run(false, "sample");
        assertEquals(result.output(), 0, result.code());
        assertTrue(result.output(), result.output().contains("PASS sample"));
    }
    @Test public void reportsFailuresAndNonzeroExit() throws Exception {
        var result = run(true);
        assertNotEquals(result.output(), 0, result.code());
        assertTrue(result.output(), result.output().contains("DECLARED_ASSERTION_FAILED"));
        assertTrue(result.output(), result.output().contains("0 passed, 1 failed"));
    }
    @Test public void refusesUndeclaredSelection() throws Exception {
        var result = run(false, "missing");
        assertNotEquals(result.output(), 0, result.code());
        assertTrue(result.output(), result.output().contains("Undeclared APP examples"));
    }
    private Result restart(boolean failSetup, boolean failResume) throws Exception {
        Path root = Files.createTempDirectory("spp-restart-catalog");
        Path catalogs = root.resolve("META-INF/spp"); Files.createDirectories(catalogs);
        Files.writeString(catalogs.resolve("examples.properties"), "example.count=1\nexample.0.service=setup\nexample.0.request=" + Request.class.getName()
            + "\nexample.0.participants=facts\nexample.0.restart.service=resume\nexample.0.restart.request=" + Request.class.getName()
            + "\nexample.0.restart.participants=facts\nexample.0.restart.datasourceProperty=example.fixture.url\n");
        Files.writeString(catalogs.resolve("services.properties"), "setup=" + Setup.class.getName() + "\nresume=" + Resume.class.getName() + "\n");
        Path output = root.resolve("output.txt"), stages = root.resolve("stages");
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        command.add("-Dexample.fail.setup=" + failSetup); command.add("-Dexample.fail.resume=" + failResume);
        command.add("-Dfixture.option=preserved");
        command.add("-Dspp.examples.evidenceDirectory=" + root.resolve("evidence"));
        command.add("-Dspp.examples.output=" + stages); command.add("-cp");
        command.add(root + java.io.File.pathSeparator + System.getProperty("java.class.path"));
        command.add(GeneratedExampleRunner.class.getName());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Restart runner timed out"); }
        String logs = Files.readString(output);
        try (var paths = Files.walk(stages)) {
            var stageFiles = paths.filter(path -> path.getFileName().toString().matches("stage-[01]\\.log")).toList();
            if (failSetup) assertEquals("Second stage must not start after first failure", 1, stageFiles.size());
            for (Path file : stageFiles) logs += Files.readString(file);
        }
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var summary = json.readTree(root.resolve("evidence/setup__restart.json").toFile());
        assertEquals(failSetup || failResume ? "FAIL" : "PASS", summary.path("status").asText());
        var setupEvidence = json.readTree(root.resolve("evidence/setup.json").toFile());
        assertEquals(failSetup ? "FAIL" : "PASS", setupEvidence.path("status").asText());
        assertEquals(failSetup ? 0 : 41, setupEvidence.path("participants").path("facts").path("value").asInt());
        if (failSetup) { assertFalse(Files.exists(root.resolve("evidence/resume.json"))); }
        else {
            var resumeEvidence = json.readTree(root.resolve("evidence/resume.json").toFile());
            assertEquals(failResume ? "FAIL" : "PASS", resumeEvidence.path("status").asText());
        }
        return new Result(process.exitValue(), logs);
    }
    @Test public void restartUsesDistinctProcessesEmptyParticipantsAndActualPersistedFacts() throws Exception {
        Result result = restart(false, false);
        assertEquals(result.output(), 0, result.code());
        assertTrue(result.output(), result.output().contains("FRESH_PROCESS_STORAGE_VERIFIED"));
        assertTrue(result.output(), result.output().contains("two fresh JVM stages"));
    }
    @Test public void firstStageFailureStopsBeforeSecondAndPreservesError() throws Exception {
        Result result = restart(true, false);
        assertNotEquals(result.output(), 0, result.code());
        assertTrue(result.output(), result.output().contains("SETUP_FAILED"));
    }
    @Test public void secondStageFailureCannotReportSuccess() throws Exception {
        Result result = restart(false, true);
        assertNotEquals(result.output(), 0, result.code());
        assertTrue(result.output(), result.output().contains("RESUME_FAILED"));
    }

}
