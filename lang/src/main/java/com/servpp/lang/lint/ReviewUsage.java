package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser.*;
import com.servpp.lang.general.NameUtil;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Direct references in the selected domain. Scenarios can be invoked by external clients. */
final class ReviewUsage {
    private enum Kind {
        SERVICE, ROLE, ENTITY, ENUM, CONSTANT
    }

    private record Declaration(RedundancyLint.Source source, Kind kind, TerminalNode identifier) { }

    private final List<Declaration> declarations = new ArrayList<>();
    private final Set<String> types = new HashSet<>();
    private final Set<String> constants = new HashSet<>();
    private final Set<String> services = new HashSet<>();
    private final Set<String> deploymentNames = new HashSet<>();
    private final Set<String> deploymentServiceNames = new HashSet<>();

    static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        var usage = new ReviewUsage();
        for (var source : sources) {
            if (source.tree() instanceof ProgramContext spp) {
                for (var unit : spp.compilationUnits().compilationUnit()) usage.collect(source, unit);
            } else if (source.tree() instanceof AppParser.ProgramContext app) {
                // APP and PHY bind names in scope, web, examples, tables, play, and provider-specific rules.
                // An identifier in any of those positions is enough to withhold an unused warning.
                ReviewLint.walk(app, node -> {
                    for (var token : node.getTokens(AppParser.Identifier)) {
                        usage.deploymentNames.add(token.getText());
                        usage.deploymentServiceNames.add(method(token.getText()));
                    }
                });
            }
        }
        for (var declaration : usage.declarations) {
            String name = declaration.identifier.getText();
            boolean referenced = switch (declaration.kind) {
                case SERVICE -> usage.services.contains(method(name)) || usage.deploymentServiceNames.contains(method(name));
                case CONSTANT -> usage.constants.contains(name) || usage.deploymentNames.contains(name);
                default -> usage.types.contains(name) || usage.deploymentNames.contains(name);
            };
            if (!referenced)
                reporter.warn(declaration.source, declaration.identifier.getSymbol(), "SPP_UNUSED_" + declaration.kind,
                        "No reference to '" + name + "' was found elsewhere in the selected domain. Check external callers and mappings before removing this declaration.");
        }
    }

    private void collect(RedundancyLint.Source source, CompilationUnitContext unit) {
        TerminalNode owner = null;
        if (unit.atomicservice() != null) {
            owner = unit.atomicservice().serviceDefinition().Identifier();
            declarations.add(new Declaration(source, Kind.SERVICE, owner));
        } else if (unit.role() != null && unit.getStart().getText().equals("Role")) {
            owner = unit.role().Identifier();
            declarations.add(new Declaration(source, Kind.ROLE, owner));
        } else if (unit.entity() != null && unit.getStart().getText().equals("Entity")) {
            owner = unit.entity().Identifier();
            declarations.add(new Declaration(source, Kind.ENTITY, owner));
        } else if (unit.enumeration() != null) {
            owner = unit.enumeration().Identifier();
            declarations.add(new Declaration(source, Kind.ENUM, owner));
        } else if (unit.constantDeclaration() != null) {
            owner = unit.constantDeclaration().Identifier();
            declarations.add(new Declaration(source, Kind.CONSTANT, owner));
        }

        String selfType = (unit.entity() != null || unit.role() != null) && owner != null ? owner.getText() : "";
        String selfService = unit.atomicservice() != null ? method(owner.getText()) : "";
        ReviewLint.walk(unit, node -> reference(node, selfType, selfService));
    }

    private void reference(ParserRuleContext node, String selfType, String selfService) {
        if (node instanceof AtomiccallContext call && call.Identifier() != null) {
            String name = method(call.Identifier().getText());
            if (!name.equals(selfService)) services.add(name);
        } else if (node instanceof EntitytypeContext type) addType(type.Identifier(), selfType);
        else if (node instanceof RoletypeContext type) addType(type.Identifier(), selfType);
        else if (node instanceof ActasContext take) take.Identifier().forEach(id -> addType(id, selfType));
        else if (node instanceof ParticipantConstructionContext construct) addType(construct.Identifier(), selfType);
        else if (node instanceof CalculationLocalContext local && local.primitiveType() == null)
            addType(local.Identifier(0), selfType);
        else if (node instanceof CalculationFunctionContext function) addType(function.Identifier(), selfType);
        else if (node instanceof QualifiedNameContext path) {
            // Enum.Member and participant-qualified paths may originate in split SPP files.
            if (path.Identifier().size() > 1) addType(path.Identifier(0), selfType);
            if (path.getParent() instanceof EntityContext) addType(path.Identifier(path.Identifier().size() - 1), selfType);
            if (path.getParent() instanceof PrimaryExprQualifiedContext && path.Identifier().size() == 1)
                constants.add(path.getText());
        } else if (node instanceof ReadConstantContext value && value.Identifier() != null)
            constants.add(value.Identifier().getText());
    }

    private void addType(TerminalNode name, String selfType) {
        if (!name.getText().equals(selfType)) types.add(name.getText());
    }

    private static String method(String name) { return NameUtil.firstToLowerCase(name, true); }
}
