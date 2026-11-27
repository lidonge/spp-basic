package com.servpp.lang.lint;

import com.servpp.lang.antlr.SppParser.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two participants of one type in one service signature imply the same participant, so a
 * service must name each participant by its own role. Value types and enums are exempt, and
 * a single object may accompany an array of its type (selector plus result list).
 */
final class ParticipantTypeLint {
    private ParticipantTypeLint() { }

    static final String CODE = "SPP_DUPLICATE_PARTICIPANT_TYPE";

    static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        Set<String> enums = new HashSet<>();
        for (var source : sources)
            if (source.tree() instanceof ProgramContext program && program.compilationUnits() != null)
                for (var unit : program.compilationUnits().compilationUnit())
                    if (unit.enumeration() != null) enums.add(unit.enumeration().Identifier().getText());
        for (var source : sources) {
            if (!(source.tree() instanceof ProgramContext program) || program.compilationUnits() == null) continue;
            for (var unit : program.compilationUnits().compilationUnit()) {
                if (unit.scenario() != null) check(source, "Scenario", unit.scenario().serviceDefinition(), enums, reporter);
                else if (unit.atomicservice() != null)
                    check(source, "Atomic service", unit.atomicservice().serviceDefinition(), enums, reporter);
            }
        }
    }

    private static void check(RedundancyLint.Source source, String kind, ServiceDefinitionContext definition,
                              Set<String> enums, RedundancyLint.Reporter reporter) {
        if (definition.parameterDeclarations() == null) return;
        Map<String, String> first = new HashMap<>();
        for (var parameter : definition.parameterDeclarations().parameterDeclaration()) {
            var type = parameter.roletype();
            if (type == null || enums.contains(type.Identifier().getText())) continue;
            String name = type.Identifier().getText();
            String shape = name + "[]".repeat((type.getChildCount() - 1) / 2);
            String label = parameter.Identifier() == null ? shape : parameter.Identifier().getText();
            String previous = first.putIfAbsent(shape, label);
            if (previous != null)
                reporter.warn(source, type.Identifier().getSymbol(), CODE, kind + " " + definition.Identifier().getText()
                        + " declares participant type " + shape + " twice (" + previous + ", " + label
                        + "). Two participants of one type imply the same participant; declare a Role per participant"
                        + " and take the entity into each (take " + name + " as <RoleName> { ... }).");
        }
    }
}
