package com.servpp.service.technical;

import com.servpp.service.Service;
import java.util.List;

/** Optional installed implementations; the basic runtime includes no system provider. */
public interface SystemTechnicalServiceProvider {
    List<ServiceInterceptors.Binding> bindings(Service service);
}
