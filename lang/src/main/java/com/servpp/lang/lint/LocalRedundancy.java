package com.servpp.lang.lint;

import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Conservative redundancy checks that can be decided from a same-domain parse forest. */
public final class LocalRedundancy {
    private LocalRedundancy() { }

    public static void check(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        Map<String, Map<String, String>> typeMaps = typeMaps(sources);
        checkCopiedTypes(sources, reporter);
        for (RedundancyLint.Source source : sources) {
            if (source.tree() instanceof SppParser.ProgramContext program) {
                checkSameNameTakes(source, program, typeMaps, reporter);
                walk(program, node -> {
                    if (node instanceof SppParser.QuantumBoundContext bound) checkBound(source, bound, reporter);
                    if (node instanceof SppParser.CalculationAssignmentContext assignment)
                        checkBlankAssignment(source, assignment, typeMaps, reporter);
                    if (node instanceof SppParser.CalculationLocalContext local)
                        checkBlankLocal(source, local, reporter);
                    if (node instanceof SppParser.LogicEqualsExprContext comparison)
                        checkBlankComparison(source, comparison, typeMaps, reporter);
                });
            }
        }
    }

    private static void checkCopiedTypes(List<RedundancyLint.Source> sources, RedundancyLint.Reporter reporter) {
        Map<Map<String, String>, SeenType> seen = new HashMap<>();
        List<DeclaredType> declarations = new ArrayList<>();
        for (RedundancyLint.Source source : sources) {
            if (!(source.tree() instanceof SppParser.ProgramContext program)) continue;
            walk(program, node -> {
                if (node instanceof SppParser.EntityContext entity && entity.Identifier() != null)
                    declarations.add(new DeclaredType(source, entity.Identifier().getSymbol(), fields(entity.entityBody())));
                else if (node instanceof SppParser.RoleContext role && role.Identifier() != null)
                    declarations.add(new DeclaredType(source, role.Identifier().getSymbol(), fields(role.roleBody())));
            });
        }
        for (DeclaredType declaration : declarations) {
            if (declaration.fields.isEmpty()) continue;
            SeenType previous = seen.putIfAbsent(declaration.fields, new SeenType(declaration.source, declaration.name));
            if (previous != null && !previous.name.equals(declaration.name.getText())) {
                reporter.warn(declaration.source, declaration.name, "SPP_REDUNDANT_COPY_TYPE",
                        "Fields duplicate " + previous.name.getText() + "; reuse that declaration instead of copying its fields.");
            }
        }
    }

    private static Map<String, String> fields(ParserRuleContext body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (body == null) return result;
        walk(body, node -> {
            if (!(node instanceof SppParser.FieldDeclarationContext field)) return;
            String name;
            String type;
            if (field.variableDeclaratorId() != null && field.variableDeclaratorId().Identifier() != null) {
                name = field.variableDeclaratorId().Identifier().getText();
                type = field.type() == null ? (field.entitytype() == null ? "" : field.entitytype().getText()) : field.type().getText();
                type += field.variableDeclaratorId().getText().substring(name.length());
            } else if (field.entitytype() != null && field.entitytype().Identifier() != null) {
                name = field.entitytype().Identifier().getText();
                type = field.entitytype().getText();
            } else if (field.IntegerLiteral() != null && field.Identifier() != null) {
                name = field.Identifier().getText();
                type = "String(" + field.IntegerLiteral().getText() + ")";
            } else return;
            result.put(name, type);
        });
        return result;
    }

    static Map<String, Map<String, String>> typeMaps(List<RedundancyLint.Source> sources) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (RedundancyLint.Source source : sources) {
            if (!(source.tree() instanceof SppParser.ProgramContext program)) continue;
            walk(program, node -> {
                if (node instanceof SppParser.EntityContext entity && entity.Identifier() != null)
                    result.put(entity.Identifier().getText(), fields(entity.entityBody()));
                else if (node instanceof SppParser.RoleContext role && role.Identifier() != null)
                    result.put(role.Identifier().getText(), fields(role.roleBody()));
            });
        }
        return result;
    }

    private static void checkSameNameTakes(RedundancyLint.Source source, SppParser.ProgramContext program,
                                            Map<String, Map<String, String>> types,
                                            RedundancyLint.Reporter reporter) {
        walk(program, node -> {
            if (!(node instanceof SppParser.ActasContext take) || take.mapBody() == null) return;
            if (take.Identifier().size() != 2 || take.mapBody().mapDeclaration().stream()
                    .anyMatch(declaration -> declaration.mapfield() == null
                            || declaration.takeall() != null || declaration.mapsame() != null)) return;
            List<SppParser.MapfieldContext> mappings = take.mapBody().mapDeclaration().stream()
                    .map(SppParser.MapDeclarationContext::mapfield).filter(java.util.Objects::nonNull).toList();
            String sourceName = take.Identifier(0).getText();
            String targetName = take.Identifier(1).getText();
            Map<String, String> sourceFields = types.get(sourceName);
            Map<String, String> targetFields = types.get(targetName);
            if (mappings.isEmpty() || sourceFields == null || targetFields == null
                    || mappings.stream().anyMatch(mapping -> mapping.Identifier().size() < 2
                    || !mapping.Identifier(0).getText().equals(mapping.Identifier(1).getText()))) return;
            Set<String> sameFields = new HashSet<>();
            for (Map.Entry<String, String> field : sourceFields.entrySet())
                if (field.getValue().equals(targetFields.get(field.getKey()))) sameFields.add(field.getKey());
            Set<String> listed = new HashSet<>();
            mappings.forEach(mapping -> listed.add(mapping.Identifier(0).getText()));
            if (!listed.equals(sameFields)) return;
            reporter.warn(source, take.Identifier(take.Identifier().size() - 1).getSymbol(), "SPP_REDUNDANT_TAKE_MAP",
                    "Every mapped field has the same name; use mapsame in the take block.");
        });
    }

    private static void checkBound(RedundancyLint.Source source, SppParser.QuantumBoundContext bound,
                                   RedundancyLint.Reporter reporter) {
        if (bound.expression().size() != 2) return;
        if (isNull(bound.expression(0)) && isNull(bound.expression(1)))
            reporter.warn(source, bound.getStart(), "SPP_REDUNDANT_VACUOUS_BOUNDS",
                    "Both limits are null; remove this bounds entry because it imposes no constraint.");
    }

    private static boolean isNull(ParseTree tree) {
        if (tree instanceof SppParser.ParenthesesExprContext parentheses) return isNull(parentheses.expression());
        return tree.getText().equals("null");
    }

    private static void checkBlankAssignment(RedundancyLint.Source source, SppParser.CalculationAssignmentContext assignment,
                                             Map<String, Map<String, String>> types,
                                             RedundancyLint.Reporter reporter) {
        if (assignment.expression() == null || assignment.getChildCount() < 3
                || !assignment.getChild(1).getText().equals("=")) return;
        TerminalNode blank = directWhitespaceString(assignment.expression());
        String target = assignment.qualifiedName() == null ? "" : assignment.qualifiedName().getText();
        if (blank != null && isStringReference(assignment, target, types)) reporter.warn(source, blank.getSymbol(), "SPP_REDUNDANT_PADDED_BLANK",
                "Use BLANK instead of an empty or space-padded string in this assignment.");
    }

    private static void checkBlankLocal(RedundancyLint.Source source, SppParser.CalculationLocalContext local,
                                        RedundancyLint.Reporter reporter) {
        if (local.primitiveType() == null || !local.primitiveType().getText().equals("String")
                || local.getText().contains("[") || local.expression().isEmpty()) return;
        for (SppParser.ExpressionContext expression : local.expression()) {
            TerminalNode blank = directWhitespaceString(expression);
            if (blank != null) reporter.warn(source, blank.getSymbol(), "SPP_REDUNDANT_PADDED_BLANK",
                    "Use BLANK instead of an empty or space-padded string in this String initializer.");
        }
    }

    private static void checkBlankComparison(RedundancyLint.Source source, ParserRuleContext comparison,
                                             Map<String, Map<String, String>> types,
                                             RedundancyLint.Reporter reporter) {
        List<SppParser.ExpressionContext> expressions = directExpressions(comparison);
        if (expressions.size() != 2) return;
        TerminalNode left = directWhitespaceString(expressions.get(0));
        TerminalNode right = directWhitespaceString(expressions.get(1));
        if (left != null && isStringOperand(comparison, expressions.get(1), types))
            reporter.warn(source, left.getSymbol(), "SPP_REDUNDANT_PADDED_BLANK",
                    "Use BLANK instead of an empty or space-padded string in this comparison.");
        else if (right != null && isStringOperand(comparison, expressions.get(0), types))
            reporter.warn(source, right.getSymbol(), "SPP_REDUNDANT_PADDED_BLANK",
                    "Use BLANK instead of an empty or space-padded string in this comparison.");
    }

    private static boolean isStringOperand(ParserRuleContext comparison, SppParser.ExpressionContext expression,
                                           Map<String, Map<String, String>> types) {
        String reference = directReference(expression);
        return reference != null && isStringReference(comparison, reference, types);
    }

    private static boolean isStringReference(ParserRuleContext context, String reference,
                                             Map<String, Map<String, String>> types) {
        SppParser.ServiceDefinitionContext definition = null;
        SppParser.AtomicserviceContext atomicService = null;
        for (ParserRuleContext ancestor = context; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof SppParser.AtomicserviceContext atomic) {
                atomicService = atomic;
                definition = atomic.serviceDefinition();
                break;
            }
            if (ancestor instanceof SppParser.ScenarioContext scenario) {
                definition = scenario.serviceDefinition();
                break;
            }
        }
        if (definition == null) return false;
        Map<String, String> participants = new HashMap<>();
        Set<String> stringVariables = new HashSet<>();
        Set<String> nonStringVariables = new HashSet<>();
        var declarations = definition.parameterDeclarations();
        if (declarations != null) {
            for (SppParser.ParameterDeclarationContext parameter : declarations.parameterDeclaration()) {
                if (parameter.roletype() != null) {
                    String type = parameter.roletype().Identifier() == null ? "" : parameter.roletype().Identifier().getText();
                    boolean array = parameter.roletype().getText().contains("[");
                    if (type.isEmpty() || array) continue;
                    String name = parameter.Identifier() == null ? type : parameter.Identifier().getText();
                    participants.put(name, type);
                } else if (parameter.type() != null && parameter.type().primitiveType() != null
                        && parameter.type().primitiveType().getText().equals("String")
                        && !parameter.type().getText().contains("[")) {
                    if (parameter.Identifier() != null) stringVariables.add(parameter.Identifier().getText());
                } else if (parameter.Identifier() != null) {
                    nonStringVariables.add(parameter.Identifier().getText());
                }
            }
        }
        if (atomicService != null) walk(atomicService, node -> {
            if (node instanceof SppParser.CalculationLocalContext local && local.primitiveType() != null) {
                boolean string = local.primitiveType().getText().equals("String") && !local.getText().contains("[");
                local.Identifier().forEach(identifier -> {
                    if (string) stringVariables.add(identifier.getText());
                    else nonStringVariables.add(identifier.getText());
                });
            }
        });
        if (!reference.contains(".")) {
            if (stringVariables.contains(reference)) return true;
            if (nonStringVariables.contains(reference)) return false;
            Set<String> possibleFieldTypes = new HashSet<>();
            int matchingParticipants = 0;
            for (String type : participants.values()) {
                String fieldType = types.getOrDefault(type, Map.of()).get(reference);
                if (fieldType != null) {
                    matchingParticipants++;
                    if (isStringType(fieldType)) possibleFieldTypes.add("String");
                    else possibleFieldTypes.add(fieldType);
                }
            }
            return matchingParticipants == 1 && possibleFieldTypes.equals(Set.of("String"));
        }
        String[] path = reference.split("\\.");
        if (path.length < 2 || !participants.containsKey(path[0])) return false;
        String currentType = participants.get(path[0]);
        for (int index = 1; index < path.length; index++) {
            String fieldType = types.getOrDefault(currentType, Map.of()).get(path[index]);
            if (fieldType == null) return false;
            if (index == path.length - 1) return isStringType(fieldType);
            if (fieldType.contains("[")) return false;
            currentType = fieldType.replaceFirst("^quantum\\s+", "");
        }
        return false;
    }

    private static String directReference(SppParser.ExpressionContext expression) {
        if (expression instanceof SppParser.ParenthesesExprContext parentheses)
            return directReference(parentheses.expression());
        if (expression instanceof SppParser.PrimExprContext primary && primary.primaryExpr() != null
                && primary.primaryExpr().primaryExprQualified() != null)
            return primary.primaryExpr().primaryExprQualified().qualifiedName().getText();
        return null;
    }

    private static boolean isStringType(String type) {
        return !type.contains("[") && (type.equals("String") || type.startsWith("String("));
    }

    private static TerminalNode directWhitespaceString(ParseTree tree) {
        TerminalNode literal = directStringLiteral(tree);
        if (literal == null) return null;
        String text = literal.getSymbol().getText();
        String contents = text.substring(1, text.length() - 1);
        return contents.chars().allMatch(ch -> ch == ' ') ? literal : null;
    }

    private static TerminalNode directStringLiteral(ParseTree tree) {
        if (tree instanceof SppParser.ParenthesesExprContext parentheses)
            return directStringLiteral(parentheses.expression());
        if (tree instanceof SppParser.PrimExprContext primary && primary.primaryExpr() != null
                && primary.primaryExpr().primaryExprLiteral() != null) {
            var literal = primary.primaryExpr().primaryExprLiteral().literal();
            return literal == null ? null : literal.StringLiteral();
        }
        return null;
    }

    private static List<SppParser.ExpressionContext> directExpressions(ParserRuleContext context) {
        List<SppParser.ExpressionContext> result = new ArrayList<>();
        for (int i = 0; i < context.getChildCount(); i++)
            if (context.getChild(i) instanceof SppParser.ExpressionContext expression) result.add(expression);
        return result;
    }

    private static void walk(ParseTree tree, java.util.function.Consumer<ParserRuleContext> consumer) {
        if (tree instanceof ParserRuleContext context) consumer.accept(context);
        for (int i = 0; i < tree.getChildCount(); i++) walk(tree.getChild(i), consumer);
    }

    private record DeclaredType(RedundancyLint.Source source, Token name, Map<String, String> fields) { }
    private record SeenType(RedundancyLint.Source source, Token name) { }
}
