package com.servpp.sdk.compiler;

import com.servpp.generator.GeneratorUtil;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StatementRoleBindingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void expandedStatementBindsRoleToEntityCrud() throws Exception {
        var root = temporary.getRoot().toPath();
        var statements = Files.createDirectories(root.resolve("src/main/spp/statements"));
        var fragment = Files.writeString(statements.resolve("loadStock.spp"), "findStock(actual);\n");
        var input = Files.writeString(root.resolve("catalog.spp"), """
                domain catalog;
                Entity Stock { String sku; String description; }
                Role Selection short selected;
                take Stock as Selection;
                readonly Scenario readStock(Selection) {
                    include loadStock(actual=selected);
                }
                """);
        var expanded = new StatementTemplates(List.of(input, fragment)).expand(input, Files.readString(input));
        var source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("catalog.spp"), expanded.text());
        Files.writeString(source.resolve("catalog.app"), """
                domain catalog;
                tables entities { table STOCK(Stock) { primarykey(Stock.sku); } }
                scope { local {} }
                phy { repository records { adapter jdbcObjectRepository; config default = true; } }
                """);
        var java = root.resolve("java");
        GeneratorUtil.execute(source.toFile(), root.resolve("yaml").toFile(), java.toFile(), "generated");
        var generated = Files.readString(java.resolve("generated/catalog/service/SimpleReadStock.java"));
        assertTrue(generated, generated.contains("withRoleArguments(new Object[]{selected}"));
        assertTrue(generated, generated.contains("Stock.class, generated.catalog.model.Selection.class"));
    }
}
