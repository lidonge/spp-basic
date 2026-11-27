package com.servpp.lang.lint;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser.*;
import com.servpp.lang.general.NameUtil;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

import static com.servpp.lang.lint.RedundancyLint.*;

/** Atomic, source-snapshot-relative plans. A diagnostic can have no mechanical rewrite. */
final class MechanicalEdits {
    private MechanicalEdits() { }

    static List<Edit> forFinding(List<Source> domain, Finding f) {
        if (!List.of("SPP_REDUNDANT_FIELD_COPY", "SPP_REDUNDANT_PRESENCE_CHECK", "SPP_REDUNDANT_IMPLICIT_CRUD",
                "SPP_REDUNDANT_VACUOUS_BOUNDS", "SPP_REDUNDANT_TAKE_MAP").contains(f.code())) return List.of();
        var source = domain.stream().filter(s -> s.path().equals(f.path())).findFirst().orElse(null);
        if (source == null || !(source.tree() instanceof ProgramContext program)) return List.of();
        var anchor = tokenNode(program, f.token());
        if (anchor == null) return List.of();
        var edits = new ArrayList<Edit>();
        switch (f.code()) {
            case "SPP_REDUNDANT_VACUOUS_BOUNDS" -> {
                var bound = ancestor(anchor, QuantumBoundContext.class);
                if (bound == null || bound.getStart() != f.token() || !vacuous(bound)) return List.of();
                var bounds = (QuantumBoundsContext) bound.getParent();
                var target = bounds.quantumBound().stream().allMatch(MechanicalEdits::vacuous) ? bounds : bound;
                edits.add(edit(source.path(), target, ""));
            }
            case "SPP_REDUNDANT_TAKE_MAP" -> {
                var take = ancestor(anchor, ActasContext.class);
                if (take == null || take.Identifier(take.Identifier().size() - 1).getSymbol() != f.token()
                        || !LocalRedundancy.sameNameTake(take, LocalRedundancy.typeMaps(domain))
                        || take.mapBody().mapDeclaration().stream().anyMatch(d -> d.mapfield().primitiveType() == null)
                        || inheritsOtherFields(domain, take)) return List.of();
                edits.add(edit(source.path(), take.mapBody(), "{ mapsame; }"));
            }
            case "SPP_REDUNDANT_PRESENCE_CHECK" -> {
                var plan = StructuralRedundancy.presence(domain, f.token());
                if (plan == null || globallyIntercepted(domain)) return List.of();
                // A duplicate-guard recommendation can depend on another simultaneous repair.
                if (plan.destinationBody() == null && plan.replacement() == null) return List.of();
                if (!removeDeclaration(domain, source, plan.guard(), edits)) return List.of();
                var call = plan.call();
                String path = pathOf(domain, call);
                if (plan.destinationBody() != null) {
                    edits.add(edit(path, call, ""));
                    var brace = plan.destinationBody().getStart();
                    int offset = RepairDiagnostic.utf16Offset(brace) + 1;
                    String next = brace.getInputStream().getText(Interval.of(brace.getStopIndex() + 1, brace.getStopIndex() + 1));
                    String separator = next.isBlank() ? "" : " ";
                    edits.add(new Edit(plan.destinationPath(), offset, offset, " " + plan.requirement() + separator));
                } else {
                    // Leave the original branch, breakTransaction and semicolon at the call site.
                    Token last = call.parameters() == null ? call.Identifier().getSymbol()
                            : call.getChild(3) instanceof TerminalNode close
                                    ? close.getSymbol() : null;
                    if (last == null) return List.of();
                    edits.add(edit(path, call.getStart(), last, plan.replacement()));
                }
            }
            case "SPP_REDUNDANT_IMPLICIT_CRUD" -> {
                var atomic = ancestor(anchor, AtomicserviceContext.class);
                if (atomic == null || atomic.serviceDefinition().Identifier().getSymbol() != f.token()
                        || !ServiceRedundancy.isImplicitCrud(domain, atomic)
                        || boundByPolicy(domain, atomic) || !preservesCrudRequest(domain, atomic)) return List.of();
                edits.add(edit(source.path(), atomic, ""));
            }
            case "SPP_REDUNDANT_FIELD_COPY" -> {
                var atomic = ancestor(anchor, AtomicserviceContext.class);
                if (atomic == null || atomic.serviceDefinition().Identifier().getSymbol() != f.token()) return List.of();
                var projection = ServiceRedundancy.projection(domain, atomic);
                if (projection == null || globallyIntercepted(domain) || boundByPolicy(domain, atomic)) return List.of();
                // A take alone never executes the copy. Only replace an unreferenced, isolated projection
                // or move the entire copy to a keyed read that writes the very same fields to its target.
                String name = atomic.serviceDefinition().Identifier().getText();
                var calls = new ArrayList<AtomiccallContext>();
                for (var file : domain) if (file.tree() instanceof ProgramContext spp)
                    collectCalls(spp, name, calls);
                if (calls.isEmpty()) {
                    if (!isolated(domain, atomic, projection)) return List.of();
                    edits.add(edit(source.path(), atomic, projection.existing() ? "" : projection.take()));
                } else {
                    if (calls.size() != 1 || !projection.existing()) return List.of();
                    var read = ServiceRedundancy.copyRead(domain, atomic, calls.get(0), projection);
                    if (read == null || boundByPolicy(domain, read.statement().Identifier().getText())
                            || !removeDeclaration(domain, source, atomic, edits)) return List.of();
                    String path = pathOf(domain, calls.get(0));
                    edits.add(edit(path, calls.get(0), ""));
                    int offset = RepairDiagnostic.utf16Offset(read.statement().getStop());
                    edits.add(new Edit(path, offset, offset, " by " + read.provider()));
                }
            }
            default -> { return List.of(); }
        }
        // Comments in any replaced slice belong to the author. Withhold the entire group.
        return edits.contains(null) ? List.of() : List.copyOf(edits);
    }

    // Implicit services (and their generated Java request types) materialize on demand.
    // Keep a declaration unless its callers recreate the same scalar request parameter.
    private static boolean preservesCrudRequest(List<Source> domain, AtomicserviceContext atomic) {
        var definition = atomic.serviceDefinition();
        var formal = definition.parameterDeclarations().parameterDeclaration(0);
        String type = formal.roletype().Identifier().getText();
        if (formal.Identifier() != null || !formal.roletype().getText().equals(type)) return false;
        var calls = new ArrayList<AtomiccallContext>();
        for (var source : domain) if (source.tree() instanceof ProgramContext program)
            collectCalls(program, definition.Identifier().getText(), calls);
        if (calls.isEmpty()) return false;
        for (var call : calls) {
            var scene = ancestor(call, ScenarioContext.class);
            if (scene == null || scene.serviceDefinition().parameterDeclarations() == null) return false;
            var args = call.parameters();
            if (args != null && args.parameter().size() != 1) return false;
            String name = args == null ? null : args.parameter(0).getText();
            long matches = scene.serviceDefinition().parameterDeclarations().parameterDeclaration().stream()
                    .filter(p -> p.roletype() != null && p.roletype().getText().equals(type))
                    .filter(p -> name == null || name.equals(com.servpp.lang.ParticipantNames.bindingName(p)))
                    .count();
            if (matches != 1) return false;
        }
        return true;
    }

    private static boolean inheritsOtherFields(List<Source> domain, ActasContext take) {
        for (var source : domain) if (source.tree() instanceof ProgramContext spp) {
            for (var unit : spp.compilationUnits().compilationUnit()) {
                var other = unit.actas();
                if (other == null || other == take) continue;
                String role = other.Identifier(other.Identifier().size() - 1).getText();
                if (take.Identifier().stream().anyMatch(id -> id.getText().equals(role))) return true;
            }
        }
        return false;
    }

    private static boolean isolated(List<Source> domain, AtomicserviceContext atomic, ServiceRedundancy.CopyProjection copy) {
        for (var source : domain) {
            if (source.tree() instanceof ProgramContext spp) {
                for (var unit : spp.compilationUnits().compilationUnit()) {
                    if (unit.atomicservice() == atomic) continue;
                    if (unit.role() != null && (unit.role().Identifier().getText().equals(copy.role())
                            || unit.role().Identifier().getText().equals(copy.performer()))) continue;
                    if (unit.entity() != null && unit.entity().Identifier().getText().equals(copy.performer())) continue;
                    if (mentions(unit, copy.performer()) || mentions(unit, copy.role())
                            || mentions(unit, atomic.serviceDefinition().Identifier().getText())) return false;
                }
            } else if (mentions(source.tree(), copy.performer()) || mentions(source.tree(), copy.role())
                    || mentions(source.tree(), atomic.serviceDefinition().Identifier().getText())) return false;
        }
        return true;
    }

    private static boolean vacuous(QuantumBoundContext bound) {
        return bound.expression().size() == 2 && bound.expression().stream().allMatch(LocalRedundancy::isNull);
    }

    private static boolean globallyIntercepted(List<Source> domain) {
        return domain.stream().anyMatch(source -> source.tree() instanceof AppParser.ProgramContext app
                && app.ruleBlock().stream().anyMatch(block -> block.annotatableRule() != null
                && block.annotatableRule().serviceInterception() != null));
    }

    private static boolean boundByPolicy(List<Source> domain, AtomicserviceContext atomic) {
        return boundByPolicy(domain, atomic.serviceDefinition().Identifier().getText());
    }

    private static boolean boundByPolicy(List<Source> domain, String name) {
        for (var source : domain) if (source.tree() instanceof AppParser.ProgramContext app) {
            for (var block : app.ruleBlock())
                if (mentionsOutsideLocal(block, name)) return true;
        }
        return false;
    }

    private static boolean mentionsOutsideLocal(ParseTree node, String name) {
        if (node instanceof AppParser.ScopeItemContext item && StructuralRedundancy.plainLocal(item)) return false;
        if (node instanceof TerminalNode token) return sameName(token.getText(), name);
        for (int i = 0; i < node.getChildCount(); i++) if (mentionsOutsideLocal(node.getChild(i), name)) return true;
        return false;
    }

    private static boolean removeDeclaration(List<Source> domain, Source source, AtomicserviceContext atomic, List<Edit> edits) {
        if (boundByPolicy(domain, atomic)) return false;
        String name = atomic.serviceDefinition().Identifier().getText();
        edits.add(edit(source.path(), atomic, ""));
        for (var file : domain) if (file.tree() instanceof AppParser.ProgramContext app) {
            for (var block : app.ruleBlock()) {
                var rules = block.annotatableRule();
                if (rules == null || rules.scope() == null) continue;
                for (var local : rules.scope().scopeDefine().localBody()) for (var item : local.scopeItem())
                    if (StructuralRedundancy.plainLocal(item) && sameName(item.scopeItemIdentifier().getText(), name))
                        edits.add(edit(file.path(), item, ""));
            }
        }
        return true;
    }

    private static boolean mentions(ParseTree node, String name) {
        if (node instanceof TerminalNode token) return sameName(token.getText(), name);
        for (int i = 0; i < node.getChildCount(); i++) if (mentions(node.getChild(i), name)) return true;
        return false;
    }

    private static boolean sameName(String left, String right) {
        return !left.isEmpty() && NameUtil.firstToLowerCase(left, true).equals(NameUtil.firstToLowerCase(right, true));
    }

    private static void collectCalls(ParseTree tree, String name, List<AtomiccallContext> calls) {
        if (tree instanceof AtomiccallContext call && call.Identifier() != null && sameName(call.Identifier().getText(), name))
            calls.add(call);
        for (int i = 0; i < tree.getChildCount(); i++) collectCalls(tree.getChild(i), name, calls);
    }

    private static String pathOf(List<Source> domain, ParserRuleContext context) {
        while (context.getParent() != null) context = context.getParent();
        for (var source : domain) if (source.tree() == context) return source.path();
        throw new IllegalArgumentException("Call is outside the analyzed domain");
    }

    private static TerminalNode tokenNode(ParserRuleContext tree, Token token) {
        for (int i = 0; i < tree.getChildCount(); i++) {
            var child = tree.getChild(i);
            if (child instanceof TerminalNode t && t.getSymbol() == token) return t;
            if (child instanceof ParserRuleContext rule && rule.getStart().getTokenIndex() <= token.getTokenIndex()
                    && rule.getStop() != null && token.getTokenIndex() <= rule.getStop().getTokenIndex()) {
                var found = tokenNode(rule, token);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T ancestor(ParseTree node, Class<T> type) {
        for (; node != null; node = node.getParent()) if (type.isInstance(node)) return type.cast(node);
        return null;
    }

    private static Edit edit(String path, ParserRuleContext node, String replacement) {
        var edit = edit(path, node.getStart(), node.getStop(), replacement);
        if (edit == null || !replacement.isEmpty()) return edit;
        var input = node.getStart().getInputStream();
        String text = input.getText(Interval.of(0, input.size() - 1));
        int start = edit.start(), end = edit.end();
        while (start > 0 && horizontalSpace(text.charAt(start - 1))) start--;
        if (start > 0 && text.charAt(start - 1) != '\n') return edit;
        while (end < text.length() && horizontalSpace(text.charAt(end))) end++;
        if (end < text.length() && text.charAt(end) == '\r') end++;
        if (end < text.length() && text.charAt(end) != '\n') return edit;
        return new Edit(path, start, Math.min(text.length(), end + 1), replacement);
    }

    private static boolean horizontalSpace(char ch) {
        return ch == ' ' || ch == '\t';
    }

    private static Edit edit(String path, Token first, Token last, String replacement) {
        var input = first.getInputStream();
        String slice = input.getText(Interval.of(first.getStartIndex(), last.getStopIndex()));
        if (slice.contains("//") || slice.contains("/*")) return null;
        int start = RepairDiagnostic.utf16Offset(first);
        return new Edit(path, start, start + slice.length(), replacement);
    }
}
