package com.servpp.service.scenario;

import com.servpp.service.Service;

/**
 * @author lidong@date 2023-11-20@version 1.0
 */
public interface IExecuteGroup extends IExecutor {
    @Override
    default Service getService() {
        return null;
    }
    IExecutor[] getServices();
}
