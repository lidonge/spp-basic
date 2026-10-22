package com.servpp.lang.general.app;

import com.servpp.lang.models.SppProject;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.antlr.AppParser;

import java.io.File;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class AppGeneralHandler extends RuleBlockHandler {
    private SppProject sppProject;

    public AppGeneralHandler(File antlrFile, SppProject sppProject) {
        super(antlrFile);
        this.sppProject = sppProject;
    }

    @Override
    public void enterRuleBlock(AppParser.RuleBlockContext ctx) {
        if (!isPhysicalFile()) {
            return;
        }
        if (ctx.annotatableRule() == null || ctx.annotatableRule().phy() == null) {
            logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(),
                    "PHY file " + getAntlrFile().getName() + " can only declare phy blocks.");
        }
    }

    @Override
    public void enterDomainName(AppParser.DomainNameContext ctx) {
        String domainName = ctx.getText();
        if (isPhysicalFile()) {
            SppDomain domain = sppProject.getDomain(domainName);
            if (domain == null) {
                domain = sppProject.getSppDomainMap().values().stream()
                        .findFirst()
                        .orElseGet(() -> sppProject.addDomain(domainName));
                setSppDomain(domain);
                logSppError(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(),
                        "PHY file " + getAntlrFile().getName()
                                + " references undefined domain " + domainName + ".");
                return;
            }
            setSppDomain(domain);
            return;
        }
        setSppDomain(sppProject.addDomain(domainName));
    }

    private boolean isPhysicalFile() {
        return getAntlrFile() != null && getAntlrFile().getName().endsWith(".phy");
    }
}
