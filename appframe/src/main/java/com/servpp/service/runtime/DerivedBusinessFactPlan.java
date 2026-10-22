package com.servpp.service.runtime;

import java.util.List;

import com.servpp.service.transaction.TransactionMode;

public record DerivedBusinessFactPlan(String serviceName,
                                      String targetPath,
                                      String targetOwnerType,
                                      String targetOwnerCategory,
                                      List<String> sourcePaths,
                                      List<String> sourceOwnerTypes,
                                      List<String> businessFactKinds,
                                      String expression,
                                      TransactionMode transactionMode,
                                      String transactionResourceId,
                                      String readiness,
                                      String businessFactContract,
                                      List<String> readyReasons,
                                      List<String> missingReasons,
                                      List<String> actions) {
}
