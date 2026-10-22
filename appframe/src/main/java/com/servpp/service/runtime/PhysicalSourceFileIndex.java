package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record PhysicalSourceFileIndex(List<String> all,
                                      List<String> shared,
                                      List<String> profile,
                                      Map<String, List<String>> byRole,
                                      List<String> sourceSelectionContracts,
                                      Map<String, List<String>> bySourceSelectionContract) {
}
