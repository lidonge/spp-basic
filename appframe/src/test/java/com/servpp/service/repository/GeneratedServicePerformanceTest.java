package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in scaling probe; business types outside the query remain synthetic. */
public class GeneratedServicePerformanceTest {
    public static class Fact { public String id; public String group; }
    @Test public void collectionQueryScaling() throws Exception {
        assumeTrue(Boolean.getBoolean("spp.perf"));
        var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:query_perf_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var mapper=new ObjectMapper();
        var store=new JdbcObjectSnapshotStore(source,value->{try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}});
        store.save("Fact","one",Map.of("id","one","group","wanted"));
        var jdbc=new JdbcObjectRepository(store,mapper);
        var json=new JsonObjectSnapshotRepository(store,mapper);
        measure("repository.jdbc.small",()->assertEquals(1,jdbc.findBy("Fact","group","wanted",Fact.class).size()));
        measure("repository.json.small",()->assertEquals(1,json.findAll("Fact",Fact.class).size()));
        int noise=Integer.getInteger("spp.perf.noise",2000);
        try(var c=source.getConnection();var statement=c.prepareStatement(
                "insert into spp_object_snapshot (object_type,object_id,payload_json,updated_at,revision_token) select 'PerfNoise', 'noise-' || X, ?, CURRENT_TIMESTAMP, 'noise' from system_range(1, ?)")) {
            statement.setString(1,"{\"padding\":\""+"x".repeat(4096)+"\"}"); statement.setInt(2,noise);
            if(noise>0) statement.executeUpdate();
        }
        measure("repository.jdbc.unrelated_rows",()->assertEquals(1,jdbc.findBy("Fact","group","wanted",Fact.class).size()));
        measure("repository.json.unrelated_rows",()->assertEquals(1,json.findAll("Fact",Fact.class).size()));
    }
    interface Action { void run() throws Exception; }
    static void measure(String label, Action action) throws Exception {
        int warmup = Integer.getInteger("spp.perf.warmup", 10);
        int count = Integer.getInteger("spp.perf.samples", 40);
        if (warmup < 0 || count < 1) throw new IllegalArgumentException("Invalid sample count");
        for (int i=0;i<warmup;i++) action.run();
        long[] times = new long[count];
        for (int i=0;i<count;i++) { long start=System.nanoTime(); action.run(); times[i]=System.nanoTime()-start; }
        java.util.Arrays.sort(times);
        System.out.printf(java.util.Locale.ROOT,"PERF %s n=%d p50_ms=%.3f p95_ms=%.3f max_ms=%.3f%n",
                label,count,times[(count-1)/2]/1e6,times[(int)Math.ceil(count*.95)-1]/1e6,times[count-1]/1e6);
    }
}
