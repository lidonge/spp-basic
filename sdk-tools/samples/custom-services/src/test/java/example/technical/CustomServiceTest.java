package example.technical;

import example.generated.ledger.model.Facts;
import example.generated.ledger.service.SimplePreview;
import example.generated.ledger.service.SimpleTwice;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.technical.ServiceInterceptors;
import com.servpp.service.technical.TechnicalInvocation.Phase;
import org.junit.Test;
import static org.junit.Assert.*;

public class CustomServiceTest {
    @Test public void generatedScenarioUsesOnlyExplicitCustomBindings()throws Exception {
        var container=DefaultServiceContainer.getInstance();container.clear();CallCounter.reset();
        try {
            try{Class.forName("com.servpp.service.runtime.OfficialPropertyChanges");fail("Commercial implementation installed");}
            catch(ClassNotFoundException expected){}
            container.registerService("ledger.twice",ServiceInterceptors.decorate(new SimpleTwice()));
            var facts=new Facts().amount(7);
            ((SimplePreview)ServiceInterceptors.decorate(new SimplePreview())).preview(facts);
            assertEquals(Integer.valueOf(14),facts.getAmount());
            assertEquals(1,CallCounter.count("ledger.preview",Phase.BEFORE));
            assertEquals(1,CallCounter.count("ledger.preview",Phase.AFTER));
            assertEquals(1,CallCounter.count("ledger.twice",Phase.BEFORE));
            assertEquals(1,CallCounter.count("ledger.twice",Phase.AFTER));
            assertEquals(0,CallCounter.count("ledger.preview",Phase.FAILURE));
        } finally {container.clear();CallCounter.reset();}
    }
}
