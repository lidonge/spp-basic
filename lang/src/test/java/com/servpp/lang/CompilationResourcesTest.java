package com.servpp.lang;
import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;
public class CompilationResourcesTest {
 @Test public void resourceScopesRestorePreviousResolverAndDefaultPaths()throws Exception {var file=Path.of("target/project/domain/model.phy").toAbsolutePath().toFile();Path fallback=file.toPath().getParent().resolve("../assets").normalize();assertEquals(fallback,CompilationResources.resolve(file,"../assets"));try(var outer=CompilationResources.withResolver((source,location)->Path.of("outer"))){assertEquals(Path.of("outer"),CompilationResources.resolve(file,"../assets"));try(var inner=CompilationResources.withResolver((source,location)->Path.of("inner"))){assertEquals(Path.of("inner"),CompilationResources.resolve(file,"../assets"));}assertEquals(Path.of("outer"),CompilationResources.resolve(file,"../assets"));}assertEquals(fallback,CompilationResources.resolve(file,"../assets"));}
}
