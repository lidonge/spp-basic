package com.servpp.service;

/**
 * @author lidong@date 2023-11-16@version 1.0
 */
public interface Service {
    Object execute(Object... role);
    String getName();
}
