package com.servpp.service.runtime;

import java.util.List;

public record CalculationAssignment(String targetPath,
                                    List<String> sourcePaths,
                                    String expression) {
}
