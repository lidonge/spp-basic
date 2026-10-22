package com.servpp.generator.openapi;

import com.servpp.lang.*;
import com.servpp.lang.models.*;
import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantConstructionGenerationTest {
    @Test public void constructorsAndCalendarFunctionsExecuteWithBigIntegerPhysicalFields() throws Exception {
        Path source=Files.createTempFile("spp-new-calendar", ".spp");
        Files.writeString(source,"""
                domain rules;
                Role DateValue { int encoded; int year; int month; int day; int epoch; boolean valid; }
                Role Holder { DateValue value; DateValue[] dates; }
                atomic calculate construct(Holder h) {
                    h.value = new DateValue();
                    h.dates = new DateValue[] { new DateValue(), null };
                    DateValue[] copiedDates = copy(h.dates);
                    h.dates = copiedDates;
                    DateValue local = new DateValue();
                    local.encoded = 20240229;
                    DateValue[] dates = new DateValue[] { local };
                    for (DateValue date : dates) { h.value.encoded = date.encoded; }

                    h.value.valid = isGregorianDate(local.encoded);
                    h.value.year = dateYear(local.encoded);
                    h.value.month = dateMonth(local.encoded);
                    h.value.day = dateDay(local.encoded);
                    h.value.epoch = dateEpochDay(local.encoded);
                }
                """);
        SppProject project=new SppProject();new SppCompiler(source.toFile(),project).compile();
        var phy=Files.createTempFile("spp-new-calendar", ".phy");Files.writeString(phy,"domain rules; phy { numeric int as BigInteger; }");
        new AppCompiler(phy.toFile(),project).compile();SppDomain domain=project.getSppDomainMap().get("rules");
        assertTrue(domain.validateAppDeclarations().toString(),domain.validateAppDeclarations().isEmpty());
        String body=((SppService)domain.getSppClass("Construct")).getPhysicalJavaStatements().stream()
                .map(statement->statement.getJavaStatement()).collect(java.util.stream.Collectors.joining("\n"));
        Path root=Files.createTempDirectory("spp-new-calendar-java");
        Files.writeString(root.resolve("Fixture.java"),"""
                import java.math.BigInteger;
                public class Fixture {
                    public static class DateValue {
                        public BigInteger encoded,year,month,day,epoch; public Boolean valid;
                        public BigInteger getEncoded(){return encoded;} public void setEncoded(BigInteger x){encoded=x;}
                        public void setYear(BigInteger x){year=x;} public void setMonth(BigInteger x){month=x;}
                        public void setDay(BigInteger x){day=x;} public void setEpoch(BigInteger x){epoch=x;}
                        public void setValid(Boolean x){valid=x;}
                    }
                    public static class Holder { public java.util.List<DateValue> dates; public java.util.List<DateValue> getDates(){return dates;} public void setDates(java.util.List<DateValue> x){dates=x;} public DateValue value; public DateValue getValue(){return value;} public void setValue(DateValue x){value=x;} }
                    public static String run(){ Holder h=new Holder(); %s
                        if(h.dates.size()!=2 || h.dates.get(0)==null || h.dates.get(1)!=null) throw new AssertionError("invalid collection");
                        return h.value.year+"/"+h.value.month+"/"+h.value.day+":"+h.value.valid;
                    }
                }
                """.formatted(body));
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-classpath",System.getProperty("java.class.path"),"-d",root.toString(),root.resolve("Fixture.java").toString()));
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{root.toUri().toURL()},getClass().getClassLoader())) {
            assertEquals("2024/2/29:true",loader.loadClass("Fixture").getMethod("run").invoke(null));
        }
    }
}
