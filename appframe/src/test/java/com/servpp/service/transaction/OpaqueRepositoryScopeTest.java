package com.servpp.service.transaction;
import com.servpp.service.Service;
import com.servpp.service.repository.*;
import com.servpp.service.scenario.*;
import com.servpp.service.jdbc.JdbcTransactionScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class OpaqueRepositoryScopeTest {
    @Test public void serialWritesRollbackTogetherWhenBootstrapUsesAnotherResource()throws Exception {verify(true);}
    @Test public void successfulSerialWritesUseOneConnectionAndCommitTogether()throws Exception {verify(false);}
    @Test public void appBoundWritesRollbackTogetherThroughTheirProviderRepository()throws Exception {verify(true,true);}
    @Test public void appBoundWritesCommitOnTheProviderConnection()throws Exception {verify(false,true);}
    private void verify(boolean failure)throws Exception {verify(failure,false);}
    private void verify(boolean failure,boolean delegated)throws Exception {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:opaque_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("create table facts(id int primary key)");}
        var repository=new JdbcObjectRepository(new JdbcObjectSnapshotStore(source,value->"{}"),new com.fasterxml.jackson.databind.ObjectMapper());
        var manager=DefaultTransactionManager.getInstance();var previous=manager.getQuantumAccessAdapter();
        manager.setQuantumAccessAdapter(new QuantumAccessAdapter(){public void executeInAccessScope(Runnable work){work.run();}public void commit(TransactionContext context,QuantumEntanglementBoundary boundary){}});
        var connections=new ArrayList<java.sql.Connection>();
        class Writer implements Service,TransactionalService,RepositoryWriteRuleProvider {
            public String getName(){return "provider.write";}public Object execute(Object... args){return null;}public String getTransactionResourceId(){return "ordinary";}public TransactionMode getTransactionMode(){return TransactionMode.LOCAL;}
            public List<RepositoryWriteRule> getRepositoryWriteRules(){return List.of();}public void configureRepositoryWrites(java.util.function.Supplier<ObjectRepository> supplier){}
            public void executeInRepositoryScope(Runnable work){repository.executeInTransaction(work);}
        }
        var writer=new Writer();
        class Delegate implements Service, com.servpp.service.runtime.LocalServiceDelegateProvider {
            public String getName(){return "consumer.write";}
            public Object execute(Object... arguments){return null;}
            public String getLocalDelegateServiceName(){return writer.getName();}
        }
        var container=com.servpp.service.server.DefaultServiceContainer.getInstance();
        if(delegated)container.registerService(writer.getName(),writer);
        Service scopeService=delegated?new Delegate():writer;
        IExecutor opaque=new IExecutor(){
            public Service getService(){return null;}public List<Service> getRepositoryScopeServices(){return List.of(scopeService);}public TransactionMode getTransactionModeHint(){return TransactionMode.LOCAL;}
            public void execute(){for(int id=1;id<=2;id++){final int value=id;SourceTransactionUpdates.execute(new IExecutor(){public Service getService(){return writer;}public void execute(){
                var c=JdbcTransactionScope.currentConnection(source).orElseThrow();connections.add(c);
                try(var s=c.prepareStatement("insert into facts values(?)")){s.setInt(1,value);s.executeUpdate();}catch(java.sql.SQLException e){throw new IllegalStateException(e);}
                if(value==2 && failure)throw new IllegalStateException("SYNTHETIC_SECOND_WRITE_FAILURE");
            }});}}
        };
        ITransactionGroup boundary=new ITransactionGroup(){public TransactionMode getDeclaredTransactionMode(){return TransactionMode.LOCAL;}public boolean parallelOrdinaryUpdates(){return true;}public boolean usesSourceTransactionPlan(){return true;}public IExecutor[] getServices(){return new IExecutor[]{opaque};}};
        try {
            if(failure)assertEquals("SYNTHETIC_SECOND_WRITE_FAILURE",assertThrows(IllegalStateException.class,boundary::execute).getMessage());else boundary.execute();
            assertEquals(2,connections.size());assertSame(connections.get(0),connections.get(1));
            try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("select count(*) from facts")){rows.next();assertEquals(failure?0:2,rows.getInt(1));}
            assertTrue(JdbcTransactionScope.currentConnections().isEmpty());
        }finally{if(delegated)container.clear();manager.setQuantumAccessAdapter(previous);DefaultTransactionManager.clearLastContext();try(var c=source.getConnection();var s=c.createStatement()){s.execute("shutdown");}}
    }
}
