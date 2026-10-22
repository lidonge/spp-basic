package com.servpp.lang.general;

import com.servpp.lang.models.SppProject;
import com.servpp.lang.antlr.SppParser;

/**
 * @author lidong@date 2023-10-31@version 1.0
 */
public class SppGeneralHandler extends CompilationUnitHandler {
    private SppProject sppProject;

    public SppGeneralHandler(
//            File genRoot,String basePackage,
            SppProject sppProject) {
        this.sppProject = sppProject;
    }


    @Override
    public void enterDomainname(SppParser.DomainnameContext ctx) {
        String domainName = ctx.getText();

        setSppDomain(sppProject.addDomain(domainName));
        push(domainName);
    }


}
