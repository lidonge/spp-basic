package com.servpp.sdk.editor;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser;
import com.servpp.lang.general.NameUtil;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.List;

public final class DocumentAnalysis {
    public record Mapping(SppParser.ActasContext context, String role, List<String> performers, boolean takeAll) { }
    private final SourceDocument document;
    private final List<SourceSymbol> symbols = new ArrayList<>();
    private final List<Mapping> mappings = new ArrayList<>();

    public DocumentAnalysis(SourceDocument document) {
        this.document = document;
        collect(document.tree(), null, 0);
    }

    public SourceDocument document() { return document; }
    public List<SourceSymbol> symbols() { return List.copyOf(symbols); }
    public List<Mapping> mappings() { return List.copyOf(mappings); }

    public ParseTree nodeFor(Token token) { return nodeFor(document.tree(), token.getTokenIndex()); }

    private ParseTree nodeFor(ParseTree tree, int tokenIndex) {
        if (tree instanceof TerminalNode terminal)
            return terminal.getSymbol().getTokenIndex() == tokenIndex ? terminal : null;
        if (tree instanceof ParserRuleContext context && context.getStart() != null && context.getStop() != null
                && (tokenIndex < context.getStart().getTokenIndex() || tokenIndex > context.getStop().getTokenIndex()))
            return null;
        for (int child = 0; child < tree.getChildCount(); child++) {
            ParseTree found = nodeFor(tree.getChild(child), tokenIndex);
            if (found != null) return found;
        }
        return null;
    }

    private SourceSymbol add(TerminalNode name, String type, SymbolKind kind, SourceSymbol owner,
                             ParserRuleContext context, ParserRuleContext visibility, boolean implicit, String detail) {
        if (name == null || name.getSymbol().getStartIndex() < 0 || name.getSymbol().getTokenIndex() < 0) return null;
        SourceSymbol symbol = new SourceSymbol(document, name.getText(), type, kind, owner, name.getSymbol(),
                context, visibility, implicit, detail);
        symbols.add(symbol);
        return symbol;
    }

    private void collect(ParseTree tree, SourceSymbol owner, int depth) {
        if (!(tree instanceof ParserRuleContext context)) return;
        if (depth > 256) throw new IllegalArgumentException("S++ editor syntax nesting exceeds 256 contexts.");
        SourceSymbol childOwner = owner;
        if (context instanceof SppParser.DomainnameContext domain) {
            add(domain.Identifier(), "domain", SymbolKind.Namespace, null, context, null, false, "domain " + domain.getText());
        } else if (context instanceof AppParser.DomainNameContext domain) {
            add(domain.Identifier(), "domain", SymbolKind.Namespace, null, context, null, false, "domain " + domain.getText());
        } else if (context instanceof SppParser.EntityContext entity && entity.Identifier() != null) {
            childOwner = add(entity.Identifier(), entity.getChild(0).getText(), SymbolKind.Class,
                    null, context, null, false, entity.getChild(0).getText() + " " + entity.Identifier().getText());
        } else if (context instanceof SppParser.RoleContext role && role.Identifier() != null) {
            childOwner = add(role.Identifier(), role.getChild(0).getText(), SymbolKind.Interface,
                    null, context, null, false, role.getChild(0).getText() + " " + role.Identifier().getText());
        } else if (context instanceof SppParser.EnumerationContext enumeration && enumeration.Identifier() != null) {
            childOwner = add(enumeration.Identifier(), "Enum", SymbolKind.Enum, null, context, null, false,
                    "Enum " + enumeration.Identifier().getText());
        } else if (context instanceof SppParser.AtomicserviceContext atomic && atomic.serviceDefinition() != null && atomic.servicetype() != null) {
            childOwner = service(atomic.serviceDefinition(), context);
        } else if (context instanceof SppParser.ScenarioContext scenario && scenario.serviceDefinition() != null) {
            childOwner = service(scenario.serviceDefinition(), context);
        } else if (context instanceof SppParser.ActasContext mapping && mapping.Identifier().size() >= 2) {
            TerminalNode target = mapping.Identifier(mapping.Identifier().size() - 1);
            boolean takeAll = mapping.mapBody() != null && mapping.mapBody().mapDeclaration().stream()
                    .anyMatch(declaration -> declaration.takeall() != null);
            mappings.add(new Mapping(mapping, target.getText(), mapping.Identifier().subList(0, mapping.Identifier().size() - 1)
                    .stream().map(TerminalNode::getText).toList(), takeAll));
            childOwner = new SourceSymbol(document, target.getText(), "mapping", SymbolKind.Interface, null,
                    target.getSymbol(), context, null, true, "take as " + target.getText());
        } else if (context instanceof SppParser.EnumBodyIdentifierContext member) {
            add(member.Identifier(), owner == null ? "" : owner.name(), SymbolKind.EnumMember, owner,
                    context, null, false, member.getText());
        } else if (context instanceof SppParser.ConstantDeclarationContext constant && constant.primitiveType() != null) {
            add(constant.Identifier(), constant.primitiveType().getText(), SymbolKind.Constant, null,
                    context, null, false, constant.getText());
        } else if (context instanceof SppParser.FieldDeclarationContext field && owner != null
                && (field.type() != null || field.entitytype() != null)) {
            String type = field.type() != null ? typeText(field.type()) : field.entitytype().getText();
                if (field.variableDeclaratorId() != null && field.variableDeclaratorId().Identifier() != null)
                    type += field.variableDeclaratorId().getText().substring(field.variableDeclaratorId().Identifier().getText().length());
            TerminalNode identifier = field.variableDeclaratorId() != null ? field.variableDeclaratorId().Identifier()
                    : field.entitytype() == null ? null : field.entitytype().Identifier();
            if (identifier != null) {
                boolean implicit = field.variableDeclaratorId() == null;
                String name = implicit ? inferredName(type) : identifier.getText();
                if (implicit && owner.type().equals("Role")) name = NameUtil.firstToLowerCase(name, true);
                SourceSymbol original = add(identifier, type, SymbolKind.Field, owner, context, null, implicit, type + " " + name);
                if (original != null && implicit) replaceName(original, name);
            }
        } else if (context instanceof SppParser.ParameterDeclarationContext parameter && owner != null
                && (parameter.type() != null || parameter.roletype() != null)) {
            String type = parameter.type() != null ? typeText(parameter.type()) : parameter.roletype().getText();
            boolean implicit = parameter.Identifier() == null;
            TerminalNode identifier = implicit ? (parameter.roletype() == null ? null : parameter.roletype().Identifier()) : parameter.Identifier();
            SourceSymbol symbol = add(identifier, type, SymbolKind.Variable, owner, context, owner.context(),
                    implicit, type + " " + (implicit ? inferredName(type) : identifier.getText()));
            if (symbol != null && implicit) replaceName(symbol, inferredName(type));
        } else if (context instanceof SppParser.CalculationLocalContext local && owner != null
                && (local.primitiveType() != null || !local.Identifier().isEmpty())) {
            String type = local.primitiveType() == null ? local.Identifier(0).getText() : local.primitiveType().getText();
            if (local.getChildCount() > 1 && local.getChild(1).getText().equals("[")) type += "[]";
            int firstName = local.primitiveType() == null ? 1 : 0;
            for (int nameIndex = firstName; nameIndex < local.Identifier().size(); nameIndex++)
                add(local.Identifier(nameIndex), type, SymbolKind.Variable, owner, context, localScope(context), false,
                        type + " " + local.Identifier(nameIndex).getText());
        } else if (context instanceof AppParser.HeaderContext header && header.Identifier() != null) {
            childOwner = add(header.Identifier(), "header", SymbolKind.Struct, null, context, null, false,
                    "header " + header.Identifier().getText());
        } else if (context instanceof AppParser.HeaderFieldDefineContext field && owner != null
                && field.headerFieldDefineIdentifier() != null && field.headerFieldDefinePrimitiveType() != null) {
            add(field.headerFieldDefineIdentifier().Identifier(), field.headerFieldDefinePrimitiveType().getText(),
                    SymbolKind.Field, owner, context, null, false, field.getText());
        } else if (context instanceof AppParser.TableDefineContext table && table.Identifier() != null) {
            childOwner = add(table.Identifier(), "table", SymbolKind.Struct, null, context, null, false,
                    "table " + table.Identifier().getText());
        } else if (context instanceof AppParser.ColumnDefineContext column && owner != null
                && column.columnName() != null && column.columnType() != null) {
            add(column.columnName().Identifier(), column.columnType().getText(), SymbolKind.Field,
                    owner, context, null, false, column.getText());
        } else if (context instanceof AppParser.MapContext map && map.Identifier() != null) {
            childOwner = add(map.Identifier(), "map", SymbolKind.Object, null, context, null, false,
                    "map " + map.Identifier().getText());
        } else if (context instanceof AppParser.ServiceContext implementation && implementation.Identifier() != null) {
            childOwner = add(implementation.Identifier(), "implementation", SymbolKind.Method, null, context, null, false,
                    "services " + implementation.Identifier().getText());
        }
        for (int child = 0; child < context.getChildCount(); child++) collect(context.getChild(child), childOwner, depth + 1);
    }

    private SourceSymbol service(SppParser.ServiceDefinitionContext definition, ParserRuleContext context) {
        Token stop = definition.getStop() == null ? definition.getStart() : definition.getStop();
        int start = document.utf16Offset(context.getStart().getStartIndex());
        int end = Math.max(start, document.utf16Offset(stop.getStopIndex() + 1));
        String signature = document.text().substring(start, end).strip().replaceAll("\\s+", " ");
        return add(definition.Identifier(), "service", SymbolKind.Function, null, context, null, false,
                signature);
    }

    private void replaceName(SourceSymbol symbol, String name) {
        symbols.set(symbols.indexOf(symbol), new SourceSymbol(document, name, symbol.type(), symbol.kind(), symbol.owner(),
                symbol.nameToken(), symbol.context(), symbol.visibility(), symbol.implicit(), symbol.detail()));
    }

    public static String baseType(String specification) {
        return specification.replaceFirst("^quantum\\s+", "").replaceAll("\\([^)]*\\)", "").replace("[]", "");
    }

    private static String typeText(SppParser.TypeContext type) {
        return type.quantumNumber() == null ? type.getText() : "quantum " + type.getText().substring("quantum".length());
    }

    private static String inferredName(String type) {
        int dimensions = (type.length() - type.replace("[]", "").length()) / 2;
        return NameUtil.participantName(baseType(type), dimensions);
    }

    private static ParserRuleContext localScope(ParserRuleContext context) {
        for (ParserRuleContext ancestor = context.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof SppParser.CalculationBodyContext) return ancestor;
            if (ancestor instanceof SppParser.CalculationStatementContext statement
                    && statement.getStart().getText().equals("for")) return ancestor;
        }
        return context;
    }
}
