package com.servpp.sdk.basic;

import com.servpp.sdk.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.generator.GeneratorUtil;
import java.nio.file.*;
import java.util.Map;

/** Basic edition worker. No account, quota, usage reporting or commercial provider dependency. */
public final class BasicCompilerWorker {
    public static void main(String[] args)throws Exception {
        var protocol=System.out;System.setOut(System.err);
        // An externally terminated CLI must not leave its generator running.
        ProcessHandle.current().parent().ifPresent(parent->parent.onExit().thenRun(()->System.exit(2)));
        var json=new ObjectMapper();int status=0;Map<String,Object> response;
        try {
            byte[] bytes=System.in.readNBytes(16385);
            if(args.length!=0 || bytes.length>16384)throw new CompilationException("WORKER_PROTOCOL","Invalid internal request.");
            var request=json.readTree(bytes);
            if(request==null || !request.isObject())throw new CompilationException("WORKER_PROTOCOL","Invalid internal request.");
            Path source=Path.of(request.path("source").asText()).toRealPath(),staging=Path.of(request.path("staging").asText());
            if(!Files.isDirectory(source))throw new CompilationException("INVALID_SOURCE","Source must be a directory.");
            String operation=request.path("operation").asText(),basePackage=request.path("basePackage").asText();
            if(!java.util.Set.of("check","generate").contains(operation) || !JavaProjectWriter.isValidPackage(basePackage))throw new CompilationException("INVALID_ARGUMENT","Invalid compiler operation or package.");
            if(operation.equals("generate") && Path.of(request.path("destination").asText()).getParent().toRealPath().startsWith(source))throw new CompilationException("INVALID_ARGUMENT","Output must be outside the source directory.");
            if(!request.path("environment").isTextual())throw new CompilationException("WORKER_PROTOCOL","Missing APP environment.");
            String environment=request.path("environment").asText();LocalCompiler.validateEnvironment(environment);
            System.setProperty("spp.environment",environment);
            Path snapshot=SourceSnapshot.capture(source,staging.resolve(".spp-input"));
            try(var files=Files.walk(snapshot)){if(files.noneMatch(path->path.toString().endsWith(".spp") && Files.isRegularFile(path)))throw new CompilationException("INVALID_SOURCE","Source contains no .spp files.");}
            var project=new java.util.LinkedHashMap<String,Object>();
            Path assets=staging.resolve(".spp-assets");
            try(var resources=SourceSnapshot.resources(source,snapshot,assets)) {
                GeneratorUtil.execute(snapshot.toFile(),staging.resolve("openapi").toFile(),staging.resolve("src/main/java").toFile(),basePackage,request.path("profile").isNull()?null:request.path("profile").asText(),parsed -> {
                    project.put("domainCount",parsed.getSppDomainMap().size());
                    long scenarios=parsed.getSppDomainMap().values().stream().flatMap(d -> d.getMapsOfClass().values().stream())
                        .filter(u -> u instanceof com.servpp.lang.models.SppScenario && u.isReal()).count();
                    project.put("scenarioCount",scenarios);
                });
            }
            SourceSnapshot.remove(snapshot);if(Files.exists(assets))SourceSnapshot.remove(assets);
            if(operation.equals("generate")) {
                new JavaProjectWriter().write(staging,basePackage);
                var provenance=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(staging.resolve("spp-generation.json").toFile());
                provenance.set("project",json.valueToTree(project));
                provenance.put("edition","basic");provenance.put("sdkRelease",request.path("releaseVersion").asText());provenance.put("environment",environment);
                if(request.path("profile").isTextual())provenance.put("physicalProfile",request.path("profile").asText());json.writerWithDefaultPrettyPrinter().writeValue(staging.resolve("spp-generation.json").toFile(),provenance);
            }
            response=Map.of("ok",true,"source",source.toString(),"environment",environment,"project",project);
        }
        catch(CompilationException failure){status=2;response=Map.of("ok",false,"code",failure.code(),"message",failure.getMessage());}
        catch(org.apache.maven.plugin.MojoFailureException | org.apache.maven.plugin.MojoExecutionException failure){System.err.println(failure.getMessage());status=1;response=Map.of("ok",false,"code","COMPILATION_FAILED","message","Check local compiler diagnostics and source files.");}
        catch(Exception failure){status=1;response=Map.of("ok",false,"code","COMPILATION_FAILED","message","Check local compiler diagnostics and source files.");}
        protocol.println(json.writeValueAsString(response));System.exit(status);
    }
}
