package com.servpp.lang;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Compile-local resource resolution; the default preserves source-relative PHY resources. */
public final class CompilationResources {
    @FunctionalInterface public interface Resolver {Path resolve(File source,String location)throws IOException;}
    private static final ThreadLocal<Resolver> CURRENT=new ThreadLocal<>();
    private CompilationResources(){}
    public static Path resolve(File source,String location)throws IOException {var resolver=CURRENT.get();return resolver==null?source.toPath().toAbsolutePath().getParent().resolve(location).normalize():resolver.resolve(source,location);}
    public static Scope withResolver(Resolver resolver){var previous=CURRENT.get();CURRENT.set(java.util.Objects.requireNonNull(resolver));return new Scope(previous);}
    public static final class Scope implements AutoCloseable {private final Resolver previous;private Scope(Resolver previous){this.previous=previous;}public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
}
