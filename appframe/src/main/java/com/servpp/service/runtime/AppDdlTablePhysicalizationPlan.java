package com.servpp.service.runtime;

import java.util.List;

public record AppDdlTablePhysicalizationPlan(String tableName,
                                             String category,
                                             String businessType,
                                             boolean classified,
                                             int primaryKeyCount,
                                             int indexPathCount,
                                             int namedIndexCount,
                                             int columnCount,
                                             int platformPrimaryKeyCount,
                                             int objectBackedPrimaryKeyCount,
                                             int platformIndexPathCount,
                                             int objectBackedIndexPathCount,
                                             int platformColumnCount,
                                             boolean platformPrimaryKeyAvailable,
                                             boolean platformIndexAvailable,
                                             boolean platformColumnAvailable,
                                             boolean objectBackedPrimaryKeyClear,
                                             boolean objectBackedIndexClear,
                                             boolean physicalized,
                                             List<String> repositoryNames,
                                             List<String> adapterNames,
                                             List<String> adapterRoles,
                                             List<String> storageTableNames,
                                             List<String> transactionHistoryTableNames,
                                             List<String> transactionArchiveTableNames,
                                             List<String> datasourceUrls,
                                             List<String> driverClassNames,
                                             List<String> missingReasons,
                                             List<String> actions) {
    public AppDdlTablePhysicalizationPlan {
        repositoryNames = repositoryNames == null ? List.of() : List.copyOf(repositoryNames);
        adapterNames = adapterNames == null ? List.of() : List.copyOf(adapterNames);
        adapterRoles = adapterRoles == null ? List.of() : List.copyOf(adapterRoles);
        storageTableNames = storageTableNames == null ? List.of() : List.copyOf(storageTableNames);
        transactionHistoryTableNames = transactionHistoryTableNames == null
                ? List.of()
                : List.copyOf(transactionHistoryTableNames);
        transactionArchiveTableNames = transactionArchiveTableNames == null
                ? List.of()
                : List.copyOf(transactionArchiveTableNames);
        datasourceUrls = datasourceUrls == null ? List.of() : List.copyOf(datasourceUrls);
        driverClassNames = driverClassNames == null ? List.of() : List.copyOf(driverClassNames);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
