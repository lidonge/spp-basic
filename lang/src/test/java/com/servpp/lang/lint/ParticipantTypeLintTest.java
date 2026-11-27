package com.servpp.lang.lint;

import com.servpp.lang.antlr.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ParticipantTypeLintTest {
    private static List<RedundancyLint.Finding> run(String content) {
        var parser = new SppParser(new CommonTokenStream(new SppLexer(CharStreams.fromString("domain ledger;\n" + content))));
        var tree = parser.program();
        assertEquals(0, parser.getNumberOfSyntaxErrors());
        var findings = new ArrayList<RedundancyLint.Finding>();
        ParticipantTypeLint.check(List.of(new RedundancyLint.Source("a.spp", tree)), (source, token, code, message) ->
                findings.add(new RedundancyLint.Finding(source.path(), token, code, message)));
        return findings;
    }

    private static List<String> names(List<RedundancyLint.Finding> findings) {
        return findings.stream().map(finding -> finding.message().split(" declares")[0]).toList();
    }

    @Test public void twoParticipantsOfOneTypeAreAnErrorInScenariosAndAtomicServices() {
        var findings = run("""
                Entity Operator { String number; }
                Scenario release(Operator registrar, Operator releaser) { }
                atomic check checkBoth(Operator a, Operator b) { }
                atomic query findBoth(Operator[] first, Operator[] second);
                """);
        assertEquals(List.of("Scenario release", "Atomic service checkBoth", "Atomic service findBoth"), names(findings));
        assertTrue(findings.stream().allMatch(finding -> finding.code().equals("SPP_DUPLICATE_PARTICIPANT_TYPE")));
        assertTrue(RedundancyLint.ERROR_CODES.contains("SPP_DUPLICATE_PARTICIPANT_TYPE"));
        assertTrue(findings.get(0).message().contains("registrar, releaser"));
        assertTrue(findings.get(0).message().contains("declare a Role per participant"));
    }

    @Test public void selectorWithResultListValueTypesEnumsAndDistinctRolesAreAccepted() {
        var findings = run("""
                Entity Operator { String number; }
                Role Registrar { String number; }
                Role Releaser { String number; }
                Enum Mode { A, B }
                Enum Failure { X:"E1" }
                atomic query listOperators(key Operator selector, Operator[] rows);
                atomic check checkValues(String first, String second, int a, int b, BigDecimal(9,2) c, BigDecimal(9,2) d) { }
                atomic check checkEnums(Mode a, Mode b, Failure c, Failure d) { }
                Scenario release(Registrar registrar, Releaser releaser) { }
                Scenario distinctShapes(Operator[] one, Operator[][] two, Operator single) { }
                """);
        assertEquals(List.of(), names(findings));
    }
}
