package com.servpp.service.runtime;

import java.lang.reflect.Method;

public interface MethodAwareRemoteServiceAdapter extends RemoteServiceAdapter {
    Object execute(String serviceName, Method method, Object... arguments);
}
