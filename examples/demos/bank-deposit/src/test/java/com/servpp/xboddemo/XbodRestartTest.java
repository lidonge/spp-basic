package com.servpp.xboddemo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class XbodRestartTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void independentJvmsPreserveDepositReversalAndLedger() throws Exception { verifyRestart("setup"); }
    @Test public void abruptExitAfterConfirmedCommitPreservesDepositForReversal() throws Exception { verifyRestart("setupCrash"); }
    private void verifyRestart(String setupStage) throws Exception {
        Path root = temporary.newFolder("restart").toPath();
        String owner = "jdbc:h2:file:"+root.resolve("records")+";DB_CLOSE_DELAY=-1;WRITE_DELAY=0";
        String ledger = "jdbc:h2:file:"+root.resolve("ledger")+";DB_CLOSE_DELAY=-1;WRITE_DELAY=0";
        for (String stage : new String[]{setupStage,"reverse","verify"}) {
            Path log = root.resolve(stage+".log");
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Dspp.environment="+System.getProperty("spp.environment","develop"),
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    XbodRestartProbe.class.getName(),owner,ledger,root.resolve("state.json").toString(),stage)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue("Probe timed out: "+stage,process.waitFor(45,TimeUnit.SECONDS));
                assertEquals(Files.readString(log),stage.equals("setupCrash") ? 73 : 0,process.exitValue());
            } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); } }
        }
    }
}
