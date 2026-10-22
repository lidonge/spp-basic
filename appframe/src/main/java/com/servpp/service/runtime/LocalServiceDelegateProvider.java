package com.servpp.service.runtime;

/** The provider behind a generated APP service binding; used to enlist local repositories. */
public interface LocalServiceDelegateProvider {
    String getLocalDelegateServiceName();
}
