package com.servpp.lang.general;

public final class PhysicalProfiles {
    private PhysicalProfiles() { }

    public static boolean matches(String filename, String profile) {
        return profile == null || profile.isBlank() || matchesExplicit(filename, profile) || !isQualified(filename);
    }

    public static boolean matchesExplicit(String filename, String profile) {
        String selected = profile.trim();
        return filename.equals(selected + ".phy") || filename.endsWith("." + selected + ".phy");
    }

    public static boolean isQualified(String filename) {
        return filename.endsWith(".phy") && filename.substring(0, filename.length() - 4).contains(".");
    }
}
