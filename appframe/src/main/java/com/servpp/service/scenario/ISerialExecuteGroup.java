package com.servpp.service.scenario;

/**
 * @author lidong@date 2023-11-20@version 1.0
 */
public interface ISerialExecuteGroup extends IExecuteGroup{
    @Override
    default void execute() {
        for(IExecutor executor:getServices()) {
            executor.execute();
        }
    }
}
