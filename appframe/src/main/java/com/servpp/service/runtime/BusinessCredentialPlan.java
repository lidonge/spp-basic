package com.servpp.service.runtime;

public record BusinessCredentialPlan(String tableName,
                                     String businessType,
                                     String category,
                                     String columnName,
                                     String path,
                                     boolean primaryKey,
                                     boolean platformColumn) {
}
