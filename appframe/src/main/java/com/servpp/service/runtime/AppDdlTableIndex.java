package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record AppDdlTableIndex(Map<String, AppDdlCatalog.Table> byName,
                               Map<String, AppDdlCatalog.Table> byBusinessType,
                               Map<String, List<AppDdlCatalog.Table>> byCategory) {
}
