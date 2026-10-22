package com.servpp.lang;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppService;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScenarioCompensationSemanticsTest {
    private SppDomain compile(String recovery, String bindings) throws Exception {
        var root = Files.createTempDirectory("spp-scenario-compensation");
        var spp = root.resolve("sample.spp"); var app = root.resolve("sample.app");
        Files.writeString(spp, """
            domain sample;
            Contract Work { String code; int value; }
            Contract Other { String code; }
            atomic update store(Work work);
            atomic update restore(Work work);
            Scenario serial commitWork(Work work) { transaction { store(work); } }
            """ + recovery + """
            atomic update node(Work work);
            Scenario serial workflow(Work work) { transaction { node(work); } }
            """);
        Files.writeString(app, """
            domain sample;
            header trace { unique String globalSerialNumber; }
            header response { notnull String status(Default = "OK"); }
            scope { local (in trace, out response) {
                quantum resource(records) store; quantum resource(records) restore;
                commitWork;
                at resource(nodes) snapshot(argument(0, Work.code)) compensate(recover) node;
                saga workflow;
            """ + bindings + "} }");
        var project = new SppProject(); new SppCompiler(spp.toFile(), project).compile(); new AppCompiler(app.toFile(), project).compile();
        return project.getDomain("sample");
    }
    @Test public void recoveryScenarioOwnsItsDeclaredSourceTransaction() throws Exception {
        var domain = compile("Scenario serial recover(Work work) { transaction { restore(work); } }", "recover;");
        var errors = domain.validateAppDeclarations();
        assertTrue(errors.toString(), errors.isEmpty());
        var node = (SppService) domain.getSppClass("Node");
        assertTrue(node.getCompensationServiceNamesMethod().contains("recover"));
        assertEquals(List.of("node"), ((SppService) domain.getSppClass("Workflow")).getSagaTransactionCallServiceNames());
    }
    @Test public void recoveryCannotDelegateToAnotherScenario() throws Exception {
        var domain = compile("Scenario serial recoverInner(Work work) { transaction { restore(work); } } Scenario serial recover(Work work) { recoverInner(work); }", "recover; recoverInner;");
        assertFalse(domain.validateAppDeclarations().isEmpty());
    }
    @Test public void recoveryWithoutSourceBoundaryAndMismatchedParametersAreRejected() throws Exception {
        var unbounded = compile("Scenario serial recover(Work work) { restore(work); }", "recover;");
        assertTrue(unbounded.validateAppDeclarations().stream().anyMatch(message -> message.contains("must declare a local transaction boundary in SPP")));
        var mismatched = compile("Scenario serial recover(Work work, Other other) { transaction { restore(work); } }", "recover;");
        assertTrue(mismatched.validateAppDeclarations().stream().anyMatch(message -> message.contains("must accept the same parameter list")));
    }
    @Test public void selfDuplicateAndReachableNestedCompensationAreRejected() throws Exception {
        String recovery = "Scenario serial recover(Work work) { transaction { restore(work); } }";
        var self = compile(recovery, "recover;");
        self.validateAppDeclarations();
        ((SppService)self.getSppClass("Node")).getScopeItem().addCompensationServiceName("node");
        assertTrue(self.validateCompensationDeclarations().stream().anyMatch(message -> message.contains("cannot compensate itself")));
        var duplicate = compile(recovery, "recover;"); duplicate.validateAppDeclarations();
        ((SppService)duplicate.getSppClass("Node")).getScopeItem().addCompensationServiceName("recover");
        assertTrue(duplicate.validateCompensationDeclarations().stream().anyMatch(message -> message.contains("duplicate compensation service")));
        var nested = compile(recovery, "recover;"); nested.validateAppDeclarations();
        ((SppService)nested.getSppClass("Restore")).getScopeItem().addCompensationServiceName("store");
        assertTrue(nested.validateCompensationDeclarations().stream().anyMatch(message -> message.contains("cannot declare nested compensation services")));
    }
}
