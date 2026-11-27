package com.servpp.generator.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.runtime.GeneratedApplicationDeployment;
import com.servpp.service.runtime.LocalGeneratedServiceRuntime;
import com.servpp.service.transaction.DeclaredTransactionContext;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import com.servpp.service.transaction.TransactionMode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Basic package D, gap D3: a role books the quantum position of its player at run time. */
public class RoleQuantumRuntimeTest {
    private static final String JDBC = "jdbc:h2:mem:role_quantum_runtime;DB_CLOSE_DELAY=-1";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    /** Wallet is the player; every update takes the player type for one endpoint and a role for the other. */
    private void run(String roles, String app) throws Exception { run(roles, app, "Holder", "holder"); }

    private void run(String roles, String app, String first, String firstName) throws Exception {
        var root = temporary.newFolder().toPath();
        var source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("d.spp"), """
                domain d;
                Entity Wallet { String account; quantum decimal funds; }
                Contract Cover short cover { decimal amount; }
                """ + roles + """
                quantum update receiveCover(Wallet, Pool, Cover) { pool.funds -> wallet.funds : cover.amount; }
                quantum update deliverCover(Wallet, Pool, Cover) { wallet.funds -> pool.funds : cover.amount; }
                Scenario serial receive(%1$s, Pool, Cover) { transaction { receiveCover(%2$s, pool, cover); } }
                Scenario serial deliver(%1$s, Pool, Cover) { transaction { deliverCover(%2$s, pool, cover); } }
                """.formatted(first, firstName) + (first.equals("Holder") ? """
                quantum update moveRoles(Holder, Pool, Cover) { holder.funds -> pool.funds : cover.amount; }
                Scenario serial move(Holder, Pool, Cover) { transaction { moveRoles(holder, pool, cover); } }
                """ : ""));
        Files.writeString(source.resolve("d.app"), """
                domain d;
                application dApp { deploy d; }
                transactions { default at resource records; }
                """ + app + """
                tables entities { table WALLETS(Wallet) { primarykey(Wallet.account); } }
                quantumStore values { attribute Wallet.funds; }
                scope { local {
                    quantum resource(movement, Wallet.funds) receiveCover;
                    quantum resource(movement, Wallet.funds) deliverCover;
                    """ + (first.equals("Holder") ? "quantum resource(movement, Wallet.funds) moveRoles;" : "") + """
                } }
                """);
        Files.writeString(source.resolve("d.phy"), """
                domain d;
                phy {
                    repository records {
                        adapter jdbcObjectRepository;
                        config default = true;
                        config appDdlTables = true;
                        config persistenceMode = LOCAL; config persistenceResource = records;
                        config datasourceUrl = "%1$s";
                        config driverClassName = "org.h2.Driver";
                    }
                    backend database {
                        adapter localJdbc;
                        quantum movement;
                        repository records;
                        quantumStore values;
                        config datasourceUrl = "%1$s";
                        config driverClassName = "org.h2.Driver";
                    }
                    quantumStore values {
                        adapter jdbcQuantum;
                        config datasourceUrl = "%1$s";
                        config driverClassName = "org.h2.Driver";
                        config journalTable = "d_journal";
                        config valueTable = "d_value";
                        config journalStore = "com.servpp.service.transaction.JdbcQuantumJournalStore";
                    }
                }
                """.formatted(JDBC));
        GeneratedDatabaseFixture.run(root, source, "d", Fixture.class, firstName);
    }

    @Test public void playerTypedParticipantsStillBookTheirOwnPositions() throws Exception {
        run("Role Pool short pool;\ntake Wallet as Pool;\n", "", "Wallet", "wallet");
    }

    @Test public void takenRolesBookThePositionsOfTheirPlayer() throws Exception {
        run("""
                Role Holder short holder;
                Role Pool short pool;
                take Wallet as Holder;
                take Wallet as Pool;
                """, "");
    }

    @Test public void sameDomainPlayedRolesBookThePositionsOfTheirPlayer() throws Exception {
        run("""
                Role Holder short holder;
                Role Pool short pool;
                """, """
                play Holder by d.Wallet { account = account; funds = funds; }
                play Pool by d.Wallet { account = account; funds = funds; }
                """);
    }

    @Test public void aTakeRoleOverAPlayedRoleBooksThePositionOfTheUnderlyingPlayer() throws Exception {
        run("""
                Role Holder short holder;
                Role Pool short pool;
                Role Origin;
                take Origin as Holder;
                """, """
                play Origin by d.Wallet { account = account; funds = funds; }
                play Pool by d.Wallet { account = account; funds = funds; }
                """);
    }

    public static class Fixture {
        public static void main(String[] args) throws Exception {
            var dataSource = new DriverManagerJdbcDataSource("org.h2.Driver", JDBC, null, null);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var runtime = LocalGeneratedServiceRuntime.jsonSnapshots(dataSource, mapper, ignored -> null);
            GeneratedApplicationDeployment.install(runtime, "dApp");
            var repository = runtime.getRepository();
            var wallet = Class.forName("generated.d.model.Wallet");
            DeclaredTransactionContext.execute(TransactionMode.LOCAL, () -> repository.executeInTransaction(() -> {
                try {
                    for (String account : new String[]{"H", "P"}) {
                        var row = wallet.getDeclaredConstructor().newInstance();
                        wallet.getMethod("setAccount", String.class).invoke(row, account);
                        repository.insert("Wallet", account, row);
                    }
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }));
            var store = runtime.getQuantumJournalStore();
            store.seedCurrentValue("$state", "Wallet.funds", "H", BigDecimal.TEN);
            store.seedCurrentValue("$state", "Wallet.funds", "P", BigDecimal.valueOf(100));
            var observer = new JdbcQuantumJournalStore(dataSource, "d_journal", "d_value");
            execute(runtime, mapper, args[0], "Receive");
            assertEquals(0, BigDecimal.valueOf(14).compareTo(observer.findCurrentValue("Wallet.funds", "H").orElseThrow()));
            assertEquals(0, BigDecimal.valueOf(96).compareTo(observer.findCurrentValue("Wallet.funds", "P").orElseThrow()));
            execute(runtime, mapper, args[0], "Deliver");
            assertEquals(0, BigDecimal.TEN.compareTo(observer.findCurrentValue("Wallet.funds", "H").orElseThrow()));
            assertEquals(0, BigDecimal.valueOf(100).compareTo(observer.findCurrentValue("Wallet.funds", "P").orElseThrow()));
            assertEquals(4, observer.findAll().size());
            if (args[0].equals("holder")) {
                execute(runtime, mapper, args[0], "Move");
                assertEquals(0, BigDecimal.valueOf(6).compareTo(observer.findCurrentValue("Wallet.funds", "H").orElseThrow()));
                assertEquals(0, BigDecimal.valueOf(104).compareTo(observer.findCurrentValue("Wallet.funds", "P").orElseThrow()));
            }
        }

        private static void execute(LocalGeneratedServiceRuntime runtime, ObjectMapper mapper, String first, String name) throws Exception {
            var request = mapper.convertValue(Map.of(first, Map.of("account", "H"), "pool", Map.of("account", "P"),
                    "cover", Map.of("amount", 4)), Class.forName("generated.d.model." + name + "Request"));
            runtime.executeGeneratedService("d." + Character.toLowerCase(name.charAt(0)) + name.substring(1), request);
        }
    }
}
