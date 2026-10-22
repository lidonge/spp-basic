package com.servpp.generator;

import com.servpp.generator.db.DDLGenerator;
import com.servpp.generator.db.DbColumn;
import com.servpp.generator.db.DbTable;
import com.servpp.generator.db.IMybatisClass;
import com.servpp.generator.db.MybatisAssociation;
import com.servpp.generator.db.MybatisClass;
import com.servpp.generator.db.MybatisField;
import com.servpp.generator.db.MybatisRef;
import com.servpp.generator.openapi.MustacheClassWriter;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDefaultService;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppService;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author lidong@date 2023-12-14@version 1.0
 */
public class MybatisGenerator {
    private SppDomain sppDomain;
    private DDLGenerator ddlGenerator;

    private Map<String, SppDefaultService> defaultServiceMap = new HashMap<>();

    public MybatisGenerator(SppDomain sppDomain, DDLGenerator ddlGenerator) {
        this.sppDomain = sppDomain;
        this.ddlGenerator = ddlGenerator;
    }

    private void generate() {

    }

    public void generateManager(URL inUrl, File genRoot, String basePackage) throws URISyntaxException, IOException {
        String domainPackage = basePackage +"." + sppDomain.getName();
        final String packName = domainPackage +".mapper";
        for(SppDefaultService sppDefaultService :sppDomain.getSppDefaultServices()) {
            SppClass sppClass = (SppClass) sppDefaultService.getRealm();
            defaultServiceMap.put(sppClass.getName(),sppDefaultService);
            String managerClassName = sppClass.getName()+"Mapper";
            String fileName = (packName+"."+managerClassName).replace('.',File.separatorChar)+".java";
            Object obj = new Object(){
                String packageName = packName;
                String modelPackage = domainPackage + ".model";
                SppClass cls = sppClass;
                String lowerClsName = NameUtil.firstToLowerCase(sppClass.getName(),true);
                List<SppService> methods = deduplicateServices(sppDefaultService.getServiceList());
            };
            MustacheClassWriter.generateFile(inUrl, obj, genRoot, fileName);
        }
    }

    private List<SppService> deduplicateServices(List<SppService> services) {
        Map<String, SppService> unique = new LinkedHashMap<>();
        for (SppService service : services) {
            unique.putIfAbsent(serviceSignature(service), service);
        }
        return new ArrayList<>(unique.values());
    }

    private String serviceSignature(SppService service) {
        String params = service.getServiceBody().getSppLocalVarList().stream()
                .map(var -> var.getName() + ":" + var.getType().getName())
                .collect(Collectors.joining(","));
        return service.getReturnType() + " " + service.getFuncName() + "(" + params + ")";
    }

    public void generateMapper( URL inUrl, File genRoot, String basePackage) throws URISyntaxException, IOException {
        String domainPackage = basePackage +"." + sppDomain.getName();
        final String packName = domainPackage +".mapper";
        genRoot = new File(genRoot,"resources");
        for(DbTable dbTable:ddlGenerator.getTableList().getArrayList()){
            SppClass sppClass = dbTable.getAppTable().getEntity();
            if(sppClass != null){
//                if(!sppClass.getName().equals("MyOrder"))
//                    continue;
//                SppDefaultService sppDefaultService = defaultServiceMap.get(sppClass.getName());
                List<DbColumn> quantumFields = null;
                for(DbColumn dbColumn : dbTable.getColumns().getArrayList()){
                    if(dbColumn.getField()!=null && dbColumn.getField().isQuantum()){
                        if(quantumFields == null)
                            quantumFields = new ArrayList<>();
                        quantumFields.add(dbColumn);
                    }
                }
                String managerClassName = sppClass.getName()+"Mapper";
                String fileName = (packName+"."+managerClassName).replace('.',File.separatorChar)+".xml";

                final List<DbColumn> q = quantumFields;
                Object obj = new MapperTemplateContext(packName, domainPackage + ".model",
                        NameUtil.firstToLowerCase(sppClass.getName(), true), dbTable, q,
                        dbTable.getMybatisClass());
                MustacheClassWriter.generateFile(inUrl, obj, genRoot, fileName,"/mustache/mybatis");
            }
        }
    }

    public static class MapperTemplateContext {
        public final String packageName;
        public final String modelPackage;
        public final String lowerClsName;
        public final DbTable table;
        public final List<DbColumn> quantum;
        public final MybatisClass mybatisClass;

        public MapperTemplateContext(String packageName, String modelPackage, String lowerClsName,
                                     DbTable table, List<DbColumn> quantum, MybatisClass mybatisClass) {
            this.packageName = packageName;
            this.modelPackage = modelPackage;
            this.lowerClsName = lowerClsName;
            this.table = table;
            this.quantum = quantum;
            this.mybatisClass = mybatisClass;
        }

        public String getResultMapFieldsXml() {
            StringBuilder xml = new StringBuilder();
            appendResultMapFields(xml, mybatisClass.getFields(), 4);
            return xml.toString();
        }

        public String getSearchConditionFieldsXml() {
            return dynamicSqlFields("and ");
        }

        public String getUpdateSetFieldsXml() {
            return dynamicSqlFields(",");
        }

        public String getInsertColumnFieldsXml() {
            StringBuilder xml = new StringBuilder();
            appendDynamicFields(xml, mybatisClass.getFields(), 12, FieldRenderMode.COLUMN, ",");
            return xml.toString();
        }

        public String getInsertValueFieldsXml() {
            StringBuilder xml = new StringBuilder();
            appendDynamicFields(xml, mybatisClass.getFields(), 12, FieldRenderMode.VALUE, ",");
            return xml.toString();
        }

        public boolean isHasQuantum() {
            return quantum != null && !quantum.isEmpty();
        }

        private String dynamicSqlFields(String suffix) {
            StringBuilder xml = new StringBuilder();
            appendDynamicFields(xml, mybatisClass.getFields(), 8, FieldRenderMode.COLUMN_EQ_VALUE, suffix);
            return xml.toString();
        }

        private void appendResultMapFields(StringBuilder xml, List<MybatisField> fields, int indent) {
            if (fields == null) {
                return;
            }
            for (MybatisField field : fields) {
                if (field.getColumn() != null) {
                    indent(xml, indent).append("<result property=\"").append(field.getProperty())
                            .append("\" column=\"").append(field.getColumn()).append("\"/>\n");
                } else if (field instanceof MybatisRef ref) {
                    indent(xml, indent).append("<association property=\"").append(field.getProperty())
                            .append("\" column=\"{ ").append(foreignKeyColumnMap(ref))
                            .append(" }\" javaType=\"").append(modelPackage).append(".")
                            .append(ref.getRefName()).append("\"\n");
                    indent(xml, indent + 4).append("select=\"").append(packageName).append(".")
                            .append(ref.getRefName()).append("Mapper.get").append(ref.getRefName())
                            .append("\">\n");
                    indent(xml, indent).append("</association>\n");
                } else if (field instanceof MybatisAssociation association) {
                    indent(xml, indent).append("<association property=\"").append(field.getProperty())
                            .append("\">\n");
                    appendResultMapFields(xml, association.getFields(), indent + 4);
                    indent(xml, indent).append("</association>\n");
                }
            }
        }

        private String foreignKeyColumnMap(MybatisRef ref) {
            return ref.getForeignKeys().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
        }

        private void appendDynamicFields(StringBuilder xml, List<MybatisField> fields, int indent,
                                         FieldRenderMode mode, String suffix) {
            if (fields == null) {
                return;
            }
            for (MybatisField field : fields) {
                if (field instanceof MybatisRef ref) {
                    for (Map.Entry entry : ref.getForeignKeys()) {
                        appendDynamicField(xml, indent, String.valueOf(entry.getKey()),
                                String.valueOf(entry.getValue()), mode, suffix);
                    }
                } else if (field.getColumn() != null) {
                    appendDynamicField(xml, indent, field.getQualifiedName(), field.getColumn(), mode, suffix);
                } else if (field instanceof IMybatisClass nested) {
                    appendDynamicFields(xml, nested.getFields(), indent, mode, suffix);
                }
            }
        }

        private void appendDynamicField(StringBuilder xml, int indent, String parameterName, String column,
                                        FieldRenderMode mode, String suffix) {
            indent(xml, indent).append("<if test=\"").append(parameterName).append(" != null\">");
            if (mode == FieldRenderMode.COLUMN) {
                xml.append(column);
            } else if (mode == FieldRenderMode.VALUE) {
                xml.append("#{ ").append(parameterName).append(" }");
            } else {
                xml.append(column).append("=#{ ").append(parameterName).append(" }");
            }
            xml.append(suffix).append("</if>\n");
        }

        private StringBuilder indent(StringBuilder xml, int spaces) {
            for (int i = 0; i < spaces; i++) {
                xml.append(' ');
            }
            return xml;
        }

        private enum FieldRenderMode {
            COLUMN,
            VALUE,
            COLUMN_EQ_VALUE
        }
    }

}
