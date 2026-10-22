package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import java.util.*;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhysicalDataSourceSharingTest {
    @Test public void explicitlySharedJdbcBackendUsesOneEnlistedConnection() throws Exception {
        JdbcDataSource bootstrap=new JdbcDataSource();bootstrap.setURL("jdbc:h2:mem:bootstrap_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var runtime=LocalGeneratedServiceRuntime.jsonSnapshots(bootstrap,new ObjectMapper(),null);
        Map<String,String> config=Map.of("driverClassName","org.h2.Driver","datasourceUrl","jdbc:h2:mem:physical_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","username","sa","password","");
        var backend=new PhysicalRuntimeCatalog.PhysicalBackend("shared","localJdbc",List.of(),"",Map.of());
        var method=LocalGeneratedServiceRuntime.class.getDeclaredMethod("physicalDataSource",String.class,String.class,Map.class,Optional.class);
        method.setAccessible(true);
        DataSource repository=(DataSource)method.invoke(runtime,"repository","records",config,Optional.of(backend));
        DataSource journal=(DataSource)method.invoke(runtime,"quantumStore","journal",config,Optional.of(backend));
        assertSame(repository,journal);
        JdbcTransactionScope.execute(repository, connection -> {
            assertSame(connection,JdbcTransactionScope.currentConnection(journal).orElseThrow()); return null;
        });
        var other=new PhysicalRuntimeCatalog.PhysicalBackend("other","localJdbc",List.of(),"",Map.of());
        // Logical backend names do not split one physical resource.
        assertSame(repository,method.invoke(runtime,"repository","records",config,Optional.of(other)));
        Map<String,String> different=new HashMap<>(config);different.put("password","different");
        assertNotSame(repository,method.invoke(runtime,"repository","records",different,Optional.of(backend)));
        assertNotSame(method.invoke(runtime,"repository","unbound",config,Optional.empty()),
                method.invoke(runtime,"quantumStore","unbound",config,Optional.empty()));
    }
}
