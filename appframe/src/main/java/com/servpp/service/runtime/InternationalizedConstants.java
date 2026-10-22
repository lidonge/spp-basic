package com.servpp.service.runtime;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Locale is application execution context; business sources use immutable constant names. */
public final class InternationalizedConstants {
    private record Bundle(String defaultLocale, Set<String> locales, Map<String,String> values) { }
    private static final Map<String,Bundle> BUNDLES = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> LOCALE = new ThreadLocal<>();
    private InternationalizedConstants() { }
    public static void configure(String domain, Properties properties) {
        String defaultLocale = properties.getProperty("defaultLocale", "zh-CN");
        Set<String> locales = Set.copyOf(java.util.Arrays.asList(properties.getProperty("supportedLocales", defaultLocale).split(",")));
        var values = new java.util.LinkedHashMap<String,String>();
        for (String name : properties.stringPropertyNames()) if (name.startsWith("locale.")) values.put(name.substring(7), properties.getProperty(name));
        BUNDLES.put(domain, new Bundle(defaultLocale, locales, Map.copyOf(values)));
    }
    public static String stringValue(String qualifiedName, String fallback) {
        int separator = qualifiedName.indexOf('.');
        Bundle bundle = BUNDLES.get(qualifiedName.substring(0, separator));
        if (bundle == null) return fallback;
        String locale = LOCALE.get();
        if (locale == null || !bundle.locales().contains(locale)) locale = bundle.defaultLocale();
        String name = qualifiedName.substring(separator + 1);
        return bundle.values().getOrDefault(locale + "." + name,
                bundle.values().getOrDefault(bundle.defaultLocale() + "." + name, fallback));
    }
    public static <T> T withLocale(String locale, Supplier<T> action) {
        String previous = LOCALE.get();
        if (locale == null || locale.isBlank()) LOCALE.remove(); else LOCALE.set(locale.replace('_','-'));
        try { return action.get(); }
        finally { if (previous == null) LOCALE.remove(); else LOCALE.set(previous); }
    }
}
