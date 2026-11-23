package com.servpp.service.runtime;

/** An immutable S++ enum value whose wire code is used in messages and JDBC columns. */
public interface WireMappedEnum {
    String getValue();
}
