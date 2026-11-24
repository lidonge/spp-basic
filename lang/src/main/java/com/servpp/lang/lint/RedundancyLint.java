package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-level diagnostics shared by batch compilation and editor overlays. */
public final class RedundancyLint {
    private RedundancyLint() { }

    public record Source(String path, ParserRuleContext tree) {
        String domain() {
            if (tree instanceof SppParser.ProgramContext spp && spp.servicedomain() != null)
                return spp.servicedomain().domainname().getText();
            if (tree instanceof AppParser.ProgramContext app && app.serviceDomain() != null)
                return app.serviceDomain().domainName().getText();
            return null;
        }
    }

    @FunctionalInterface
    public interface Reporter {
        void warn(Source source, Token token, String code, String message);
    }

    /** Half-open UTF-16 offsets into the exact parsed source. */
    public record Edit(String path, int start, int end, String replacement) { }

    public record Finding(String path, Token token, String code, String message) {
        public String format() {
            return path + ":" + token.getLine() + ":" + (token.getCharPositionInLine() + 1)
                    + " " + code + " " + message;
        }
    }

    public static List<Finding> analyze(Collection<Source> sources) {
        return analyze(sources, null);
    }

    public static List<Finding> analyze(Collection<Source> sources,
            java.util.function.BiConsumer<List<Source>, Finding> observer) {
        Map<String, List<Source>> domains = new LinkedHashMap<>();
        for (Source source : sources.stream().sorted(Comparator.comparing(Source::path)).toList()) {
            String domain = source.domain();
            if (domain != null) domains.computeIfAbsent(domain, unused -> new ArrayList<>()).add(source);
        }
        List<Source> project = List.copyOf(sources);
        List<Finding> findings = new ArrayList<>();
        for (List<Source> domain : domains.values()) {
            Reporter reporter = (source, token, code, message) -> {
                Finding finding = new Finding(source.path(), token, code, message);
                findings.add(finding);
                if (observer != null) {
                    // BLANK policies can be inherited from another domain in the deployment.
                    observer.accept(code.equals("SPP_REDUNDANT_PADDED_BLANK") ? project : domain, finding);
                }
            };
            LocalRedundancy.check(domain, reporter);
            ServiceRedundancy.check(domain, reporter);
            StructuralRedundancy.check(domain, reporter);
            DataAccessLint.check(domain, reporter);
            QueryLint.check(domain, reporter);
            ReviewLint.check(domain, reporter);
        }
        findings.sort(Comparator.comparing(Finding::code).thenComparing(Finding::path).thenComparingInt(f -> f.token().getTokenIndex()));
        return List.copyOf(findings);
    }
}
