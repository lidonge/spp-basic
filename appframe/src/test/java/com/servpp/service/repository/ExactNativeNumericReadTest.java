package com.servpp.service.repository;

import com.servpp.service.runtime.AppDdlCatalog;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class ExactNativeNumericReadTest {
    public static class Evidence {
        public String id; public BigDecimal decimalValue; public BigInteger integerValue;
    }
    @Test public void declaredNativeColumnsReadTextNumbersExactlyAndRejectFractionalIntegers() throws Exception {
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:numeric_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var columns = List.of(new AppDdlCatalog.Column("id", "VARCHAR", 40, 0, false, true),
                new AppDdlCatalog.Column("decimal_value", "VARCHAR", 4096, 0, false, true),
                new AppDdlCatalog.Column("integer_value", "VARCHAR", 4096, 0, false, true));
        var table = new AppDdlCatalog.Table("evidence", "roles", "Evidence", List.of("id"), List.of(), List.of(),
                List.of(), Map.of(), columns, List.of("id"), List.of(), List.of(), List.of(), List.of());
        var reader = new JdbcAppDdlObjectTableReader(source, new AppDdlCatalog(List.of(table)));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table evidence(id varchar(40) primary key, decimal_value varchar(4096), integer_value varchar(4096))");
            statement.execute("insert into evidence values ('one', '100.0000000000000000000000001', '999999999999999999999999999999999999999999')");
        }
        Evidence result = reader.find("Evidence", "one", Evidence.class).orElseThrow();
        assertEquals(new BigDecimal("100.0000000000000000000000001"), result.decimalValue);
        assertEquals(new BigInteger("999999999999999999999999999999999999999999"), result.integerValue);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("update evidence set integer_value = '1.5'");
        }
        assertThrows(ArithmeticException.class, () -> reader.find("Evidence", "one", Evidence.class));
    }
}
