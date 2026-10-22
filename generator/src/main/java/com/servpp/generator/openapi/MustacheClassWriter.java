package com.servpp.generator.openapi;

import com.servpp.mustache.MustacheCompiler;
import com.servpp.mustache.handler.MustacheListenerImpl;
import com.servpp.mustache.handler.MustacheWriter;
import com.servpp.mustache.model.BaseSection;
import com.servpp.mustache.model.Template;
import com.servpp.lang.general.IConstance;
import com.servpp.lang.models.*;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lidong@date 2023-11-15@version 1.0
 */
public class MustacheClassWriter extends ClassWriterConfig {
    private final boolean deploymentService;

    public MustacheClassWriter(File domainPath, SppClass sppClass, String basePackage, String domainName) {
        super(domainPath, sppClass, basePackage, domainName);
        this.deploymentService = true;
    }

    public MustacheClassWriter(File domainPath, SppClass sppClass, String basePackage, String domainName,
                               boolean deploymentService) {
        super(domainPath, sppClass, basePackage, domainName);
        this.deploymentService = deploymentService;
    }
    public static void generateFile(URL mustacheUrl,Object obj, File genRoot, String name ) throws URISyntaxException, IOException {
        generateFile(mustacheUrl,obj,genRoot,name,null);
    }
    public static void generateFile(URL mustacheUrl,Object obj, File genRoot, String name , String subPath) throws URISyntaxException, IOException {
        String string = renderTemplate(mustacheUrl, obj, subPath);
        string = CodeFormator.formatCode(string);
        File file = new File(genRoot, name);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory " + parent);
        }
        try (PrintWriter out = new PrintWriter(new FileOutputStream(file))) {
            out.println(string);
        }
    }

    public void generate() throws IOException, URISyntaxException {
        SppClass sppclass = (SppClass) sppCompilationUnit;
        IConstance.CompilationUnitType type = sppclass.getType();
        if (sppclass instanceof SppService service && service.isLocalService()) {
            try {
                if (type == IConstance.CompilationUnitType.scenario) {
                    String body = service.getScenarioBodyJava();
                    var statements = service.getServiceBody().getSppServiceCallList();
                    if (statements != null && !statements.isEmpty() && body.isBlank()) {
                        throw new IllegalStateException("Nonempty scenario rendered an empty body");
                    }
                } else if (service.hasBusinessImplementation()) {
                    for (var statement : service.getPhysicalJavaStatements()) statement.getJavaStatement();
                }
            } catch (RuntimeException failure) {
                throw new IOException("Cannot generate business body for " + service.getFuncName(), failure);
            }
        }
        if (type != null) {
            if (type == IConstance.CompilationUnitType.rolemapper) {
                takeAllMapFields();

                genClassFile("model", "/rolemapper.mustache", sppclass, sppclass.getName());
            } else if (type == IConstance.CompilationUnitType.atomicservice) {
                SppService sppService = (SppService) sppclass;
                if (deploymentService) {
                    if(sppService.isLocalService()){
                        genClassFile("service", "/service.mustache", sppclass, "Simple" + sppclass.getName());
                    }else {
                        genClassFile("service", "/proxyservice.mustache", sppclass, "Simple" + sppclass.getName());
                    }
                }
                genClassFile("handler", "/atomicservice.mustache", sppclass, sppclass.getName());
            } else if (type == IConstance.CompilationUnitType.scenario) {
                SppService sppService = (SppService) sppclass;
                genClassFile("handler", "/scenario.mustache", sppclass, sppclass.getName());
                if (deploymentService) {
                    if (sppService.isLocalService()) {
                        genClassFile("service", "/scenarioservice.mustache", sppclass, "Simple" + sppclass.getName());
                    } else {
                        genClassFile("service", "/remotescenarioservice.mustache", sppclass, "Simple" + sppclass.getName());
                    }
                }
            }
        }
    }

    private void takeAllMapFields() {
        SppRoleMapper sppRoleMapper = (SppRoleMapper) sppCompilationUnit;
        if (sppRoleMapper.isTakeAll() || sppRoleMapper.isMapSame()) {
            Map<String, SppField> nameToEntitys = new HashMap<>();
            for (SppField var : sppRoleMapper.getSppFieldList()) {
                SppRoleField roleField = (SppRoleField) var;
                nameToEntitys.put(roleField.getEntityName(), var);
            }
            for(SppClass sppClass:sppRoleMapper.getEntities()) {
                for (SppField var : sppClass.getSppFieldList()) {
                    SppField entVar = nameToEntitys.get(var.getName());
                    SppField roleField = sppRoleMapper.getRole().getField(var.getName());
                    if (entVar == null && roleField != null
                            && (!sppRoleMapper.getRole().isStorageOwnershipResolved()
                                || roleField.isBorrowedStorageFact())) {
                        SppRoleField sppRoleField = new SppRoleField(var.getType(), var.getName());
                        sppRoleField.setArrayDimension(var.getArrayDimension());
                        sppRoleField.setEntityName(sppClass.getName());
                        sppRoleMapper.addLocalVar(sppRoleField);
                        nameToEntitys.put(var.getName(), sppRoleField);
                    }
                }
            }
        }
    }

    private void genClassFile(String additionalPackage, String mustacheFile, SppClass cls, String fileName) throws IOException, URISyntaxException {
        cls.setAdditionalPackage(additionalPackage);
        URL mustacheUrl = MustacheClassWriter.class.getResource(mustacheFile);
        File clsPath = new File(getDomainPath(), additionalPackage);
        if (!clsPath.exists() && !clsPath.mkdirs()) {
            throw new IOException("Cannot create output directory " + clsPath);
        }
        File objPath = new File(clsPath, fileName + IConstance.SUFFIX);

        try (PrintWriter out = new PrintWriter(new FileOutputStream(objPath))) {
            String text = renderTemplate(mustacheUrl, cls, null);
//            out = new PrintWriter(System.out);
            out.println(CodeFormator.formatCode(text));
        }
    }

    static String renderTemplate(URL mustacheUrl, Object root, String subPath) throws IOException, URISyntaxException {
        MustacheWriter writer = new MustacheWriter(mustacheUrl.toURI(), root)
                .setNullVariableAsEmpty(true);
        writer.setPartialFileHandler(partialName -> {
            try {
                return compileTemplate(resolvePartialUrl(partialName, subPath));
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
        try {
            writer.write(compileTemplate(mustacheUrl), BaseSection.SectionType.Normal);
            return writer.getOutText().toString();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to render mustache template " + mustacheUrl
                    + " with root " + describeRoot(root), e);
        }
    }

    private static String describeRoot(Object root) {
        if (root instanceof SppCompilationUnit) {
            SppCompilationUnit unit = (SppCompilationUnit) root;
            return root.getClass().getName() + "[name=" + unit.getName() + ", type=" + unit.getType() + "]";
        }
        return root == null ? "null" : root.getClass().getName();
    }

    private static Template compileTemplate(URL mustacheUrl) throws IOException, URISyntaxException {
        MustacheCompiler mustacheCompiler = new MustacheCompiler(mustacheUrl);
        mustacheCompiler.compileAntlr4(null);
        MustacheListenerImpl listener = new MustacheListenerImpl(mustacheUrl.toURI());
        mustacheCompiler.workListener(listener);
        return listener.getTemplate();
    }

    private static URL resolvePartialUrl(String partialName, String subPath) throws IOException {
        String path = subPath == null ? "" : subPath;
        path += File.separator + partialName + ".mustache";
        URL url = MustacheClassWriter.class.getResource(path);
        if (url == null) {
            throw new FileNotFoundException(path);
        }
        return url;
    }

}
