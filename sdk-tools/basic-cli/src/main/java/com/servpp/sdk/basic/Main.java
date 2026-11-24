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
        var json = new ObjectMapper();
        int status = 0;
        boolean lines = arguments.length > 0 && arguments[0].equals("check")
                && containsPair(arguments, "--format", "json");
        try {
            var result = run(arguments);
            if (lines) {
                var diagnostics = (com.fasterxml.jackson.databind.JsonNode) result.get("diagnostics");
                var counts = new HashMap<String,Integer>();
                for (var d : diagnostics) counts.merge(d.path("code").asText(), 1, Integer::sum);
                for (var d : diagnostics) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode)d).put("count", counts.get(d.path("code").asText()));
                    protocol.println(json.writeValueAsString(d));
                }
                if (Boolean.FALSE.equals(result.get("checked"))) status = 2;
            } else if (result.containsKey("page")) {
                protocol.print(result.get("page"));
            } else {
                protocol.println(json.writeValueAsString(Map.of("ok", true, "result", result)));
            }
        } catch (Exception failure) {
            String code = failure instanceof CompilationException known ? known.code() : "COMPILATION_FAILED";
            status = failure instanceof CompilationException ? 2 : 1;
            String message = Objects.toString(failure.getMessage(), "Compilation failed.");
            Object error = lines ? com.servpp.lang.lint.RepairDiagnostic.error(code, "", 0, 0, message,
                    "Check the command arguments and source directory.")
                    : Map.of("ok", false, "error", Map.of("code", code, "message", message));
            protocol.println(json.writeValueAsString(error));
        }
        System.exit(status);
    }

    private static boolean containsPair(String[] args, String option, String value) {
        for (int i = 1; i < args.length; i++) if (args[i-1].equals(option) && args[i].equals(value)) return true;
        return false;
    }

    static Map<String,Object> run(String[] arguments)throws Exception {
        if(arguments.length==0 || Arrays.equals(arguments,new String[]{"--help"})) {
            var info=new LinkedHashMap<>(version());
            info.put("usage","spp-basic explain CODE | fix (--code CODE|--all-safe) | <status|check|generate> [--format json|text] [--strict] [--source DIR] [--output DIR] [--package NAME] [--profile NAME] [--environment NAME] [--timeout-seconds 1..120]");return info;
        }
        if(Arrays.equals(arguments,new String[]{"--version"}))return version();
        if(arguments[0].equals("status") && arguments.length==1)return version();
        if (arguments[0].equals("explain") && arguments.length == 2) {
            return Map.of("page", com.servpp.lang.lint.DiagnosticPages.explain(arguments[1])
                    .orElseThrow(() -> invalid("Unknown diagnostic code.")));
        }
        boolean fix = arguments[0].equals("fix");
        if(!Set.of("check","generate","fix").contains(arguments[0]))throw invalid("Unknown command; use --help.");
        var options=new LinkedHashMap<String,String>();
        for(int i=1;i<arguments.length;i++) {
            String option=arguments[i];
            if ((option.equals("--strict") || (fix && option.equals("--all-safe"))) && options.put(option, "true") == null) continue;
            if ((option.equals("--format") && !arguments[0].equals("check")) || (option.equals("--code") && !fix))
                throw invalid("Invalid option for this command.");
            if(!Set.of("--format","--code","--source","--output","--package","--profile","--environment","--timeout-seconds").contains(option)
                    || options.containsKey(option) || i+1==arguments.length || arguments[i+1].startsWith("--"))
                throw invalid("Unknown, duplicate option or missing value.");
            options.put(option,arguments[++i]);
        }
        long seconds;
        try{seconds=Long.parseLong(options.getOrDefault("--timeout-seconds","120"));}
        catch(NumberFormatException invalid){throw invalid("Timeout must be an integer from 1 to 120 seconds.");}
        if(seconds<1 || seconds>120)throw invalid("Timeout must be an integer from 1 to 120 seconds.");
        var compiler=new LocalCompiler(Duration.ofSeconds(seconds),staging -> LocalCompiler.startWorker(BasicCompilerWorker.class,staging));
        String format = options.getOrDefault("--format", "text");
        if (!Set.of("text","json").contains(format)) throw invalid("Choose text or json format.");
        if (fix && options.containsKey("--code") == options.containsKey("--all-safe"))
            throw invalid("Choose exactly one of --all-safe and --code CODE.");
        if (fix && options.containsKey("--code") && com.servpp.lang.lint.DiagnosticPages.explain(options.get("--code")).isEmpty())
            throw invalid("Unknown diagnostic code.");
        Path source = Path.of(options.getOrDefault("--source","src/main/spp"));
        SafeFix.Check check = () -> compiler.execute(fix ? "check" : arguments[0], source,
                Path.of(options.getOrDefault("--output","target/spp-basic")),options.getOrDefault("--package","com.servpp.generated"),
                options.get("--profile"),options.getOrDefault("--environment",System.getProperty("spp.environment","develop")),
                Map.of("releaseVersion","1.0-SNAPSHOT","strict",options.containsKey("--strict"),
                        "diagnostics",fix || format.equals("json")));
        var report = new LinkedHashMap<>(fix ? SafeFix.apply(source.toRealPath(), options.get("--code"), check) : check.run());
        report.put("edition","basic");return report;
    }
    private static Map<String,Object> version(){return Map.of("version","1.0-SNAPSHOT","edition","basic","authorizationRequired",false);}
    private static CompilationException invalid(String message){return new CompilationException("INVALID_ARGUMENT",message);}
}
