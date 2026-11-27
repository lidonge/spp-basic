package com.servpp.generator.openapi;

import com.servpp.generator.GeneratorUtil;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SameDomainRoleGenerationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void twoRolesOverOneEntityOfTheSameDomainGenerateProviderQueries() throws Exception {
        var source = temporary.newFolder("sources").toPath();
        Files.writeString(source.resolve("ops.spp"), """
                domain ops;
                Entity Operator { String operatorNumber; String operatorName; }
                Role Registrar short registrar;
                Role Releaser short releaser;
                Scenario serial readOperators(Registrar, Releaser) {
                    findRegistrar by registrar.operatorNumber = "R1";
                    findReleaser by releaser.operatorNumber = "R2";
                }
                """);
        Files.writeString(source.resolve("ops.app"), """
                domain ops;
                play Registrar by ops.Operator { operatorNumber = operatorNumber; }
                play Releaser by ops.Operator { operatorNumber = operatorNumber; }
                tables entities { table OPERATORS(Operator) { primarykey(Operator.operatorNumber); } }
                scope { local {} }
                """);
        Files.writeString(source.resolve("ops.phy"), """
                domain ops;
                phy {
                    repository data {
                        adapter jdbcObjectRepository;
                        config default = true;
                        config appDdlTables = true;
                        config datasourceUrl = "jdbc:h2:mem:same_domain_roles";
                        config driverClassName = "org.h2.Driver";
                    }
                    backend database {
                        adapter localJdbc;
                        config datasourceUrl = "jdbc:h2:mem:same_domain_roles";
                        config driverClassName = "org.h2.Driver";
                    }
                }
                """);
        var output = temporary.newFolder("output").toPath();
        var javaOutput = output.resolve("java");
        GeneratorUtil.execute(source.toFile(), output.resolve("resources").toFile(), javaOutput.toFile(), "example");
        String query = Files.readString(javaOutput.resolve("example/ops/service/SimpleFindRegistrar.java"));
        assertTrue(query, query.contains("ParticipantRoleQueries.Play(\"ops.findOperator\", example.ops.model.Operator.class"));
        assertTrue(query, query.contains("Map.entry(\"operatorNumber\", \"operatorNumber\")"));
        assertTrue(query, query.contains("Map.entry(\"operatorName\", \"operatorName\")"));
    }
}
