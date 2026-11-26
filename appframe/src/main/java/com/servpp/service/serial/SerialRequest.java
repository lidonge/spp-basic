package com.servpp.service.serial;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * One allocation request.
 *
 * @param rule         the APP serial rule name
 * @param counter      the counter key: the rule plus the escaped scope values; safe as a store key
 * @param scope        the scope field values, in declaration order
 * @param businessDate the scope value of date type, when the scope has one (a daily reset group)
 * @param min          the rule's range start
 * @param max          the rule's range end
 * @param gapless      whether the rule demands allocation inside the business transaction
 * @param connection   the business transaction's connection, when one is bound
 */
public record SerialRequest(String rule, String counter, Map<String, Object> scope, Optional<LocalDate> businessDate,
                            long min, long max, boolean gapless, Optional<Connection> connection) { }
