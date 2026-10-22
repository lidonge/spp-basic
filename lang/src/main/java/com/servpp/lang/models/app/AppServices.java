package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class AppServices extends Annotation {
    List<AppService> appServiceList=new ArrayList<>();
    List<String> duplicateServiceNames = new ArrayList<>();

    public List<AppService> getAppServiceList() {
        return appServiceList;
    }

    public List<String> getDuplicateServiceNames() {
        return duplicateServiceNames;
    }

    public String addService(AppService appService){
        String error = appService.addToList(appServiceList,appService);
        if (error != null) {
            duplicateServiceNames.add(appService.getName());
        }
        return error;
    }
}
