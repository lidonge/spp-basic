package com.servpp.generator;

import com.servpp.lang.models.SppProject;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;

/**
 * @author lidong@date 2023-11-13@version 1.0
 */
@Mojo(name = "sppcompile", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SppPlugin extends AbstractMojo {
    @Parameter(defaultValue = "src/main/spp")
    private File sppSourceDirectory;

    @Parameter(defaultValue = "target/spp/src/main/resources")
    private File yamlOutputDirectory;
    @Parameter(defaultValue = "target/spp/src/main/java")
    private File javaOutputDirectory;

    @Parameter(defaultValue = "com.servpp.openapi")
    private String basePackage;

    @Parameter(property = "sppPhysicalProfile")
    private String physicalProfile;

    static interface IAntlrCompiler{
        ParseTreeListener parse(File sppFile, SppProject sppProject) throws IOException;
    }
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        GeneratorUtil.execute(sppSourceDirectory, yamlOutputDirectory, javaOutputDirectory,
                basePackage, physicalProfile);
    }
}
