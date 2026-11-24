import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.*;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import com.servpp.xbod.array.generated.physicalarray.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArrayReadTest {
    @Test public void samePhysicalRowVisibleToBothQueries() throws Exception {
        String url="jdbc:h2:mem:physical_array;DB_CLOSE_DELAY=-1";
        System.setProperty("spp.xbod.cash-voucher-lane-a-url",url);
        var runtime=LocalGeneratedServiceRuntime.jsonSnapshots(new DriverManagerJdbcDataSource("org.h2.Driver",url,null,null),new ObjectMapper(),ignored->null);
        GeneratedApplicationDeployment.install(runtime,"arrayExample");
        runtime.getPhysicalRuntime().setQuantumJournalStore(new JdbcQuantumJournalStore((javax.sql.DataSource)runtime.getRepository().transactionResourceIdentity()));
        try(var conn=java.sql.DriverManager.getConnection(url);var stmt=conn.createStatement()){
            stmt.executeUpdate("INSERT INTO PHYSICAL_ENTRIES(itemCode,sector) VALUES('1','A')");
        }
        var request=new CompareReadsRequest();
        var selection=new Entry();selection.setSector("A");request.setSelection(selection);
        request.setExternalRows(new java.util.ArrayList<>());
        request.setTransactionalRows(new java.util.ArrayList<>());
        request.setBefore(new Result());request.setInside(new Result());
        runtime.executeGeneratedService("physicalarray.compareReads",request);
        assertEquals(Integer.valueOf(1),request.getBefore().getObserved());
        assertEquals(Integer.valueOf(1),request.getInside().getObserved());
    }
}
