import com.servpp.service.runtime.BusinessChecks;
import com.servpp.service.runtime.GeneratedExampleRunner;

/** Acceptance adapter only: business preparation and checks remain in SPP. */
public final class VerifyTutorial {
    public static void main(String[] args) throws Exception {
        GeneratedExampleRunner.main(new String[]{"preview"});
        var result = BusinessChecks.scenarioResult();
        if (!result.passed()) {
            throw new AssertionError("Business check rejected preview: " + result.code());
        }
        System.out.println("BUSINESS PASS preview");
    }
}
