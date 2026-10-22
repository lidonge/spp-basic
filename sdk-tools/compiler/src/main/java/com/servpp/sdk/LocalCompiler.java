package com.servpp.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class LocalCompiler {
    public static final Duration MAX_EXECUTION_TIME=Duration.ofSeconds(120);
    private final Duration limit;
    private final WorkerLauncher launcher;

    @FunctionalInterface public interface WorkerLauncher { Process start(Path staging)throws IOException; }
    public LocalCompiler(Duration limit,WorkerLauncher launcher) {
        if(limit==null || limit.isNegative() || limit.isZero() || limit.compareTo(MAX_EXECUTION_TIME)>0)
            throw new IllegalArgumentException("Execution limit must be positive and at most 120 seconds.");
        this.limit=limit;this.launcher=Objects.requireNonNull(launcher);
    }
    public static Process startWorker(Class<?> workerClass,Path staging)throws IOException {
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String own;
        try{own=Path.of(workerClass.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();}
        catch(java.net.URISyntaxException invalid){throw new IOException("Cannot locate the compiler worker.",invalid);}
        return new ProcessBuilder(javaExecutable,"-cp",own+java.io.File.pathSeparator+System.getProperty("java.class.path"),workerClass.getName())
            .redirectOutput(staging.resolve(".worker-result.json").toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    }
    public Map<String,Object> execute(String operation,Path source,Path output,String basePackage,String profile,
                                      String environment,Map<String,?> workerOptions)throws Exception {
        if(!Set.of("check","generate").contains(operation))throw new CompilationException("INVALID_ARGUMENT","Unknown compiler operation.");
        validateEnvironment(environment);
        long deadline=System.nanoTime()+limit.toNanos();
        if(Thread.currentThread().isInterrupted())throw new CompilationException("CANCELLED","Conversion cancelled.");
        if(!JavaProjectWriter.isValidPackage(basePackage))throw new CompilationException("INVALID_ARGUMENT","Provide a dotted Java base package.");
        String release=Objects.toString(workerOptions.get("releaseVersion"),"1.0-SNAPSHOT");
        Path destination=output.toAbsolutePath().normalize();
        if(operation.equals("generate")) {
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new CompilationException("OUTPUT_EXISTS","Output already exists; choose a new directory.");
            if(destination.getParent()==null)throw new CompilationException("INVALID_ARGUMENT","Choose an output directory below a parent.");
            Files.createDirectories(destination.getParent());
            if(Files.isSymbolicLink(destination.getParent()))throw new CompilationException("INVALID_ARGUMENT","Output parent must not be a symbolic link.");
            destination=destination.getParent().toRealPath().resolve(destination.getFileName());
        }
        Path staging=operation.equals("generate")?Files.createTempDirectory(destination.getParent(),".spp-sdk-"):Files.createTempDirectory("spp-sdk-check-");
        Process worker=null;Thread shutdown=null;boolean interrupted=false;
        try {
            remaining(deadline);worker=launcher.start(staging);Process active=worker;
            shutdown=new Thread(()->{stop(active);try{deleteTree(staging);}catch(IOException failed){System.err.println("Interrupted SDK staging cleanup requires local inspection.");}},"spp-worker-shutdown");Runtime.getRuntime().addShutdownHook(shutdown);
            var request=new LinkedHashMap<String,Object>(workerOptions);request.put("operation",operation);request.put("source",source.toAbsolutePath().normalize().toString());request.put("staging",staging.toString());request.put("destination",destination.toString());request.put("basePackage",basePackage);request.put("profile",profile);request.put("environment",environment);request.put("releaseVersion",release);
            byte[] payload=new ObjectMapper().writeValueAsBytes(request);
            if(payload.length>16384)throw new CompilationException("INVALID_ARGUMENT","Compiler request metadata exceeds its size limit.");
            // A JVM stuck before reading stdin must not block the caller outside the execution deadline.
            var delivered=java.util.concurrent.CompletableFuture.runAsync(()->{
                try(var pipe=active.getOutputStream()){pipe.write(payload);}
                catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
            });
            try{delivered.get(remaining(deadline),TimeUnit.NANOSECONDS);}
            catch(java.util.concurrent.TimeoutException expired){throw timeout();}
            catch(java.util.concurrent.ExecutionException failed){throw new IOException("Cannot deliver the local compiler request.",failed);}
            if(!worker.waitFor(remaining(deadline),TimeUnit.NANOSECONDS))throw timeout();
            remaining(deadline);
            Path result=staging.resolve(".worker-result.json");
            if(!Files.isRegularFile(result,LinkOption.NOFOLLOW_LINKS) || Files.size(result)>16384)throw new IOException("Compiler worker returned no bounded result.");
            var report=new ObjectMapper().readTree(Files.readString(result));Files.delete(result);
            if(worker.exitValue()!=0 || !report.path("ok").asBoolean()) {
                if(report.path("code").asText().equals("COMPILATION_FAILED"))throw new org.apache.maven.plugin.MojoExecutionException("Check local compiler diagnostics and source files.");
                String code=report.path("code").asText();if(!code.matches("[A-Z][A-Z_]{0,63}"))throw new IOException("Invalid compiler worker result.");
                throw new CompilationException(code,report.path("message").asText());
            }
            if(!report.path("project").isObject())throw new IOException("Compiler worker did not report project inspection.");
            if(operation.equals("check"))return Map.of("checked",true,"source",report.path("source").asText(),"releaseVersion",release,"environment",environment,"project",report.path("project"));
            // Serialize SDK publishers of the same destination. Only a completed tree is made visible.
            Path lock=destination.getParent().resolve(".spp-publish-"+fingerprint(destination.toString())+".lock");
            if(Files.isSymbolicLink(lock))throw new CompilationException("INVALID_ARGUMENT","Output lock must not be a symbolic link.");
            try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                FileLock held;
                try{held=channel.tryLock();}catch(OverlappingFileLockException busy){held=null;}
                if(held==null)throw new CompilationException("OUTPUT_BUSY","Another SDK process is publishing this output.");
                try(var acquired=held) {
                    remaining(deadline);
                    if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new CompilationException("OUTPUT_EXISTS","Output already exists; choose a new directory.");
                    try{Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);}
                    catch(AtomicMoveNotSupportedException unsupported){throw new CompilationException("ATOMIC_OUTPUT_UNSUPPORTED","Choose a local filesystem supporting atomic directory publication.");}
                }
            }
            return Map.of("generated",true,"output",destination.toString(),"releaseVersion",release,"environment",environment,"project",report.path("project"),"artifacts",new String[]{"pom.xml","src/main/java","src/main/resources","openapi","spp-generation.json"});
        }catch(InterruptedException cancelled){interrupted=true;throw new CompilationException("CANCELLED","Conversion cancelled.");}
        finally {
            if(worker!=null)stop(worker);
            if(shutdown!=null)try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException closing){/* JVM shutdown owns the hook. */}
            try{deleteTree(staging);}finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    public static void validateEnvironment(String environment)throws CompilationException {
        if(environment==null || environment.isEmpty() || !Character.isJavaIdentifierStart(environment.codePointAt(0))
                || !environment.codePoints().allMatch(Character::isJavaIdentifierPart))
            throw new CompilationException("INVALID_ARGUMENT","Choose an APP environment identifier, such as develop or testing.");
    }
    private static String fingerprint(String value) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static long remaining(long deadline)throws CompilationException {long left=deadline-System.nanoTime();if(left<=0)throw timeout();return left;}
    private static CompilationException timeout(){return new CompilationException("COMPILATION_TIMEOUT","Conversion exceeded its execution limit; no new output was published. Reduce the input or retry on a faster environment.");}
    private static void stop(Process worker) {
        if(!worker.isAlive())return;
        worker.descendants().forEach(ProcessHandle::destroyForcibly);worker.destroyForcibly();
        try{if(!worker.waitFor(5,TimeUnit.SECONDS))throw new IllegalStateException("Compiler worker did not terminate.");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
    private static void deleteTree(Path directory)throws IOException {
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return;
        try(var files=Files.walk(directory)){for(Path entry:files.sorted(Comparator.reverseOrder()).toList())Files.delete(entry);}
    }
}
