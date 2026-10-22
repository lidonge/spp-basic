package com.servpp.sdk.basic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.sdk.CompilationException;
import com.servpp.sdk.LocalCompiler;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Offline CLI using only the public compiler and basic runtime. */
public final class Main {
    public static void main(String[] arguments)throws Exception {
        var protocol=System.out;System.setOut(System.err);
        int status=0;Map<String,Object> response;
        try{response=Map.of("ok",true,"result",run(arguments));}
        catch(CompilationException failure){status=2;response=Map.of("ok",false,"error",Map.of("code",failure.code(),"message",failure.getMessage()));}
        catch(Exception failure){status=1;System.err.println(failure.getMessage());response=Map.of("ok",false,"error",Map.of("code","COMPILATION_FAILED","message","Check local compiler diagnostics and source/output paths."));}
        protocol.println(new ObjectMapper().writeValueAsString(response));System.exit(status);
    }
    static Map<String,Object> run(String[] arguments)throws Exception {
        if(arguments.length==0 || Arrays.equals(arguments,new String[]{"--help"})) {
            var info=new LinkedHashMap<>(version());
            info.put("usage","spp-basic <status|check|generate> [--source DIR] [--output DIR] [--package NAME] [--profile NAME] [--environment NAME] [--timeout-seconds 1..120]");return info;
        }
        if(Arrays.equals(arguments,new String[]{"--version"}))return version();
        if(arguments[0].equals("status") && arguments.length==1)return version();
        if(!Set.of("check","generate").contains(arguments[0]))throw invalid("Unknown command; use --help.");
        var options=new LinkedHashMap<String,String>();
        for(int i=1;i<arguments.length;i++) {
            String option=arguments[i];
            if(!Set.of("--source","--output","--package","--profile","--environment","--timeout-seconds").contains(option)
                    || options.containsKey(option) || i+1==arguments.length || arguments[i+1].startsWith("--"))
                throw invalid("Unknown, duplicate option or missing value.");
            options.put(option,arguments[++i]);
        }
        long seconds;
        try{seconds=Long.parseLong(options.getOrDefault("--timeout-seconds","120"));}
        catch(NumberFormatException invalid){throw invalid("Timeout must be an integer from 1 to 120 seconds.");}
        if(seconds<1 || seconds>120)throw invalid("Timeout must be an integer from 1 to 120 seconds.");
        var compiler=new LocalCompiler(Duration.ofSeconds(seconds),staging -> LocalCompiler.startWorker(BasicCompilerWorker.class,staging));
        var report=new LinkedHashMap<>(compiler.execute(arguments[0],Path.of(options.getOrDefault("--source","src/main/spp")),
                Path.of(options.getOrDefault("--output","target/spp-basic")),options.getOrDefault("--package","com.servpp.generated"),
                options.get("--profile"),options.getOrDefault("--environment",System.getProperty("spp.environment","develop")),
                Map.of("releaseVersion","1.0-SNAPSHOT")));
        report.put("edition","basic");return report;
    }
    private static Map<String,Object> version(){return Map.of("version","1.0-SNAPSHOT","edition","basic","authorizationRequired",false);}
    private static CompilationException invalid(String message){return new CompilationException("INVALID_ARGUMENT",message);}
}
