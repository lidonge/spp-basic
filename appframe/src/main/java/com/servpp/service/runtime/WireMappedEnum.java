package com.servpp.service.runtime;

/** An immutable S++ enum value whose wire code is used in messages and JDBC columns. */
public interface WireMappedEnum {
    String getValue();

    static Object wireValue(Object value) {
        return value instanceof WireMappedEnum member ? member.getValue() : value;
    }
}
