package com.servpp.service.runtime;

import com.servpp.service.Service;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** PERF-REFLECT-1: preserve reflection resolution while reusing structural data. */
public class GeneratedServiceSignaturesTest {
    public static class Parent implements Service {
        String name="read";
        public void read(Long value) { }
        public Object execute(Object... roles) { return null; }
        public String getName() { return name; }
    }
    public static class Child extends Parent {
        public void read(String value) { }
        public void second(Integer value) { }
    }
    public static class Inherited extends Parent { }
    public static class Overloaded extends Parent {
        public void ambiguous(String value) { }
        public void ambiguous(Integer value) { }
    }
    interface Generic<T> { T convert(T value); }
    public static class Bridge extends Parent implements Generic<String> {
        public String convert(String value) { return value; }
    }
    @Test public void declaredMethodsWinAndCurrentNameIsNotCached() {
        var child=new Child();
        assertEquals(List.of(String.class.getName()),GeneratedServiceSignatures.parameters(child));
        child.name="second";
        assertEquals(List.of(Integer.class.getName()),GeneratedServiceSignatures.parameters(child));
        child.name="missing";assertTrue(GeneratedServiceSignatures.parameters(child).isEmpty());
        child.name=null;assertTrue(GeneratedServiceSignatures.parameters(child).isEmpty());
        assertEquals(List.of(Long.class.getName()),GeneratedServiceSignatures.parameters(new Inherited()));
    }
    @Test public void ambiguousOverloadsStayUnresolvedAndSyntheticBridgesDoNotAddCandidates() {
        var overloaded=new Overloaded();overloaded.name="ambiguous";
        assertTrue(GeneratedServiceSignatures.parameters(overloaded).isEmpty());
        var bridge=new Bridge();bridge.name="convert";
        assertEquals(List.of(String.class.getName()),GeneratedServiceSignatures.parameters(bridge));
    }
}
