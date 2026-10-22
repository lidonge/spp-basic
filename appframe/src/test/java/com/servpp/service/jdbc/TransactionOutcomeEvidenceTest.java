package com.servpp.service.jdbc;
import com.servpp.service.transaction.TransactionOutcomes;
import com.servpp.service.scenario.ScenarioFailures;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.Assert.*;
import static com.servpp.service.transaction.TransactionOutcomes.Outcome.*;

public class TransactionOutcomeEvidenceTest {
    private static final class Fixture {
        final JdbcDataSource actual=new JdbcDataSource();
        boolean commitBefore,commitAfter,rollbackFail,closeFail,resetFail;
        int commits,rollbacks;
        final DataSource source;
        Fixture() throws Exception {
            actual.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
            try(var c=actual.getConnection();var s=c.createStatement()){s.execute("CREATE TABLE sample_value(id INT PRIMARY KEY)");}
            source=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)-> {
                Object result=invoke(actual,m,a);
                if(result instanceof Connection c)return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)-> {
                    if(cm.getName().equals("commit")){commits++;if(commitBefore)throw new SQLException("COMMIT_ACK_LOST");Object v=invoke(c,cm,ca);if(commitAfter)throw new SQLException("COMMIT_ACK_LOST");return v;}
                    if(cm.getName().equals("rollback")){rollbacks++;if(rollbackFail)throw new SQLException("ROLLBACK_FAILED");}
                    if(cm.getName().equals("setAutoCommit")&&Boolean.TRUE.equals(ca[0])&&resetFail)throw new IllegalStateException("RESET_FAILED");
                    Object v=invoke(c,cm,ca);if(cm.getName().equals("close")&&closeFail)throw new SQLException("CLOSE_FAILED");return v;
                });return result;
            });
        }
        private static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
        int rows()throws Exception{try(var c=actual.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sample_value")){r.next();return r.getInt(1);}}
    }
    private static void insert(Connection c)throws SQLException{try(var s=c.createStatement()){s.executeUpdate("INSERT INTO sample_value VALUES(1)");}}
    @Test public void bodyFailureConfirmsRollbackAndPreservesOriginal()throws Exception{
        var f=new Fixture();var original=new IllegalStateException("BODY_FAILED");
        var e=assertThrows(IllegalStateException.class,()->JdbcTransactionScope.execute(f.source,c->{insert(c);throw original;}));
        assertSame(original,e);assertEquals(ROLLED_BACK,TransactionOutcomes.outcome(e));assertEquals(0,f.rows());assertEquals(0,f.commits);assertEquals(1,f.rollbacks);assertEquals(0,e.getSuppressed().length);assertTrue(JdbcTransactionScope.currentConnection(f.source).isEmpty());
    }
    @Test public void failedCommitBeforeEffectIsConservativelyUnknown()throws Exception{
        var f=new Fixture();f.commitBefore=true;var e=assertThrows(SQLException.class,()->JdbcTransactionScope.execute(f.source,c->{insert(c);return null;}));
        assertEquals(UNKNOWN,TransactionOutcomes.outcome(e));assertEquals(0,f.rows());
    }
    @Test public void failedAcknowledgementAfterEffectIsUnknownDespiteRollbackReturning()throws Exception{
        var f=new Fixture();f.commitAfter=true;var e=assertThrows(SQLException.class,()->JdbcTransactionScope.execute(f.source,c->{insert(c);return null;}));
        assertEquals(UNKNOWN,TransactionOutcomes.outcome(e));assertEquals(1,f.rows());assertEquals(1,f.rollbacks);
    }
    @Test public void rollbackFailureCannotReplaceOriginalOrClaimRollback()throws Exception{
        var f=new Fixture();f.rollbackFail=true;var original=new IllegalStateException("BODY_FAILED");var e=assertThrows(IllegalStateException.class,()->JdbcTransactionScope.execute(f.source,c->{insert(c);throw original;}));
        assertSame(original,e);assertEquals(UNKNOWN,TransactionOutcomes.outcome(e));assertEquals(1,e.getSuppressed().length);assertEquals("ROLLBACK_FAILED",e.getSuppressed()[0].getMessage());assertTrue(JdbcTransactionScope.currentConnection(f.source).isEmpty());
    }
    @Test public void postCommitCleanupFailureKeepsCommittedEvidenceThroughWrapping()throws Exception{
        var f=new Fixture();f.closeFail=true;var e=assertThrows(SQLException.class,()->JdbcTransactionScope.execute(f.source,c->{insert(c);return null;}));
        assertEquals(COMMITTED,TransactionOutcomes.outcome(e));assertEquals(1,f.rows());var facts=ScenarioFailures.describe(new IllegalStateException("WRAPPED",e),new Facts());assertEquals("WRAPPED",facts.code);assertEquals("COMMITTED",facts.transactionOutcome);
    }
    @Test public void runtimeCleanupFailureIsSuppressedAfterKnownRollback()throws Exception{
        var f=new Fixture();f.resetFail=true;var original=new IllegalStateException("BODY_FAILED");var e=assertThrows(IllegalStateException.class,()->JdbcTransactionScope.execute(f.source,c->{insert(c);throw original;}));
        assertSame(original,e);assertEquals(ROLLED_BACK,TransactionOutcomes.outcome(e));assertEquals(0,f.rows());assertEquals("RESET_FAILED",e.getSuppressed()[0].getMessage());assertTrue(JdbcTransactionScope.currentConnection(f.source).isEmpty());
    }
    @Test public void mixedIndependentOutcomesCannotClaimGlobalRollback()throws Exception{
        var a=new Fixture();var b=new Fixture();var original=new IllegalStateException("BODY_FAILED");
        var e=assertThrows(IllegalStateException.class,()->TransactionOutcomes.observe(()->{try{JdbcTransactionScope.execute(a.source,c->{insert(c);JdbcTransactionScope.execute(b.source,d->{insert(d);return null;});throw original;});}catch(SQLException sql){throw new IllegalStateException(sql);}}));
        assertSame(original,e);assertEquals(UNKNOWN,TransactionOutcomes.outcome(e));assertEquals(0,a.rows());assertEquals(1,b.rows());
    }
    @Test public void joinedScopeNeverCommitsAndBoundaryPreservesKnownRollback()throws Exception{
        var f=new Fixture();var original=new IllegalStateException("BODY_FAILED");var e=assertThrows(IllegalStateException.class,()->TransactionOutcomes.observe(()->{try{JdbcTransactionScope.execute(f.source,c->JdbcTransactionScope.execute(f.source,d->{assertSame(c,d);insert(d);throw original;}));}catch(SQLException sql){throw new IllegalStateException(sql);}}));
        assertSame(original,e);assertEquals(ROLLED_BACK,TransactionOutcomes.outcome(e));assertEquals(0,f.rows());assertEquals(0,f.commits);
    }
    @Test public void identityEvidenceAndAbsenceAreKeptDistinct(){
        var a=new EqualFailure();var b=new EqualFailure();TransactionOutcomes.record(a,ROLLED_BACK);TransactionOutcomes.record(b,COMMITTED);
        assertEquals(ROLLED_BACK,TransactionOutcomes.outcome(a));assertEquals(COMMITTED,TransactionOutcomes.outcome(b));assertEquals(UNREPORTED,TransactionOutcomes.outcome(new IllegalStateException("BUSINESS_REJECTION")));
    }
    @Test public void earlierInnerCommitDoesNotProveLaterUnmeasuredOperationCommitted() throws Exception {
        var f = new Fixture();
        var original = new IllegalStateException("LATER_UNMEASURED_FAILURE");
        var error = assertThrows(IllegalStateException.class, () -> TransactionOutcomes.observe(() -> {
            try { JdbcTransactionScope.execute(f.source, c -> { insert(c); return null; }); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
            throw original;
        }));
        assertSame(original, error);
        assertEquals(UNKNOWN, TransactionOutcomes.outcome(error));
        assertEquals(1, f.rows());
        var unrelated = new IllegalStateException("UNRELATED_FAILURE");
        assertSame(unrelated, assertThrows(IllegalStateException.class,
                () -> TransactionOutcomes.observe(() -> { throw unrelated; })));
        assertEquals(UNREPORTED, TransactionOutcomes.outcome(unrelated));
    }
    @Test public void evidenceFollowsCyclicCauseAndSuppressedGraphsWithoutChangingThem() {
        var a = new IllegalStateException("FIRST");
        var b = new IllegalStateException("SECOND");
        a.initCause(b); b.initCause(a);
        TransactionOutcomes.record(a, ROLLED_BACK);
        TransactionOutcomes.record(b, COMMITTED);
        assertEquals(UNKNOWN, TransactionOutcomes.outcome(a));
        assertEquals(UNKNOWN, TransactionOutcomes.outcome(b));
        assertEquals(0, a.getSuppressed().length);
        assertSame(b, a.getCause());
    }
    private static final class EqualFailure extends RuntimeException{public boolean equals(Object o){return o instanceof EqualFailure;}public int hashCode(){return 0;}}
    public static final class Facts{String code,transactionOutcome;public void setCode(String v){code=v;}public void setTransactionOutcome(String v){transactionOutcome=v;}}
}
