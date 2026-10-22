package com.servpp.lang.general.app;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.ISppErrorLogger;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.app.IQualifiedNameUtil;
import com.servpp.lang.models.app.RuleBlock;
import com.servpp.lang.models.app.SppFieldReference;
import com.servpp.lang.antlr.AppListener;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.List;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public interface IApplicationHandler extends AppListener, IConstance, ISppErrorLogger, IQualifiedNameUtil {

    void push(Object obj);
    Object pop();

    Object peek();
    RuleBlock getCurrentRuleBlock();

    SppDomain getSppDomian();

    default <T> T getLastElement(List<T> elements){
        return elements.get(elements.size()-1);
    }
    default <T> T removeLastElement(List<T> elements){
        return elements.remove(elements.size()-1);
    }
    default SppFieldReference getQualifiedField(ParserRuleContext ctx, SppClass mapEntity, String qualifiedName) {
        try {
            return getQualifiedField(getSppDomian(),mapEntity,qualifiedName);
        } catch (SemanticException e) {
            logSppError(ctx,e.getMessage());
        }
        return null;
    }
}
