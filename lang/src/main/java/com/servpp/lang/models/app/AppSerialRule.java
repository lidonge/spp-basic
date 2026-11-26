package com.servpp.lang.models.app;

import java.util.ArrayList;
import java.util.List;

/** APP serial rule: what the serial is. The engine that produces raw numbers is a technical service. */
public final class AppSerialRule implements INamedObject {
    private final String name;
    private final String engine;
    private final List<String> scope = new ArrayList<>();
    private boolean scopeDeclared, rangeDeclared, formatDeclared;
    private long min = 1, max = Long.MAX_VALUE;
    private boolean wrap, gapless;
    private String format;

    public AppSerialRule(String name, String engine) { this.name = name; this.engine = engine; }
    @Override public String getName() { return name; }
    public String getEngine() { return engine; }
    public List<String> getScope() { return scope; }
    public boolean isScopeDeclared() { return scopeDeclared; }
    public void markScopeDeclared() { scopeDeclared = true; }
    public boolean isRangeDeclared() { return rangeDeclared; }
    public long getMin() { return min; }
    public long getMax() { return max; }
    public boolean isWrap() { return wrap; }
    public void setRange(long min, long max, boolean wrap) { this.min = min; this.max = max; this.wrap = wrap; rangeDeclared = true; }
    public boolean isGapless() { return gapless; }
    public void setGapless(boolean gapless) { this.gapless = gapless; }
    public String getFormat() { return format; }
    public boolean isFormatDeclared() { return formatDeclared; }
    public void setFormat(String format) { this.format = format; formatDeclared = true; }
}
