package com.servpp.service.runtime;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PureDomainDeploymentTest {
    private Properties contract() {
        var p=new Properties();p.setProperty("deployment.environment","develop");p.setProperty("deployment.transactionMode","LOCAL");return p;
    }
    private void validate(Properties pure) {
        var application=new Properties();application.setProperty("application.0.environment","develop");application.setProperty("application.0.transactionMode","LOCAL");
        var ledger=contract();ledger.setProperty("backends","records");ledger.setProperty("backend.records.adapter","localJdbc");ledger.setProperty("backend.records.config.datasourceUrl","jdbc:h2:mem:pure-domain");
        DeploymentContracts.validate(application,"application.0.",Map.of("ledger",ledger,"rules",pure));
    }
    @Test public void generatedPureDomainCanShareALocalApplicationWithoutItsOwnBackend() {
        var pure=contract();pure.setProperty("domain.rules.requiresLocalStorage","false");validate(pure);
    }
    @Test public void absentOrRequiredStorageEvidenceStillRequiresTheBackend() {
        for(String value:new String[]{"","true","invalid"}) {
            var pure=contract();if(!value.isEmpty())pure.setProperty("domain.rules.requiresLocalStorage",value);
            try{validate(pure);fail("Unproven storage-free domain must be rejected");}
            catch(IllegalStateException expected){assertEquals("LOCAL_PHYSICAL_BACKEND_REQUIRED: rules",expected.getMessage());}
        }
    }
}
