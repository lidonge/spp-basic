package com.servpp.sdk.editor;

import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.antlr.SppParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

public final class ProjectAnalysis {
    private final Map<String, DocumentAnalysis> documents = new LinkedHashMap<>();
    private final List<SourceSymbol> allSymbols;
    private final List<SourceSymbol> effectiveSymbols;
    private final String physicalProfile;
    private final String environment;

    public ProjectAnalysis(Collection<SourceDocument> sources) {
        this(sources, "", System.getProperty("spp.environment", "develop"));
    }

    public ProjectAnalysis(Collection<SourceDocument> sources, String physicalProfile, String environment) {
        this.physicalProfile = physicalProfile;
        this.environment = environment;
        for (SourceDocument source : sources) documents.put(source.uri(), new DocumentAnalysis(source));
        List<SourceSymbol> raw = documents.values().stream().flatMap(document -> document.symbols().stream()).toList();
        List<SourceSymbol> normalized = new ArrayList<>();
        for (SourceSymbol symbol : raw) {
            SourceSymbol normalizedSymbol = symbol;
            if (symbol.owner() != null && symbol.owner().type().equals("mapping")) {
                var owners = raw.stream().filter(candidate -> candidate.owner() == null && isType(candidate)
                        && candidate.domain().equals(symbol.domain()) && candidate.name().equals(symbol.owner().name())).toList();
                if (owners.size() == 1) {
                    String name = symbol.name();
                    if (symbol.implicit() && owners.get(0).type().equals("Role"))
                        name = com.servpp.lang.general.NameUtil.firstToLowerCase(name, true);
                    normalizedSymbol = new SourceSymbol(symbol.document(), name, symbol.type(), symbol.kind(), owners.get(0),
                            symbol.nameToken(), symbol.context(), symbol.visibility(), symbol.implicit(), symbol.type() + " " + name);
                }
            }
            normalized.add(normalizedSymbol);
        }
        allSymbols = List.copyOf(normalized);
        effectiveSymbols = allSymbols.stream().filter(this::active).toList();
    }

    public List<SourceSymbol> symbols() {
        return effectiveSymbols;
    }

    public SourceDocument document(String uri) { return documents.containsKey(uri) ? documents.get(uri).document() : null; }
    public List<DocumentAnalysis.Mapping> documentMappings(String uri) {
        return documents.containsKey(uri) ? documents.get(uri).mappings() : List.of();
    }

    public boolean active(SourceSymbol symbol) {
        String filename = java.nio.file.Path.of(java.net.URI.create(symbol.document().uri())).getFileName().toString();
        return (!filename.endsWith(".phy") || com.servpp.lang.general.PhysicalProfiles.matches(filename, physicalProfile))
                && (symbol.environment().isEmpty() || symbol.environment().equals(environment));
    }

    public List<SourceSymbol> documentSymbols(String uri) {
        DocumentAnalysis document = documents.get(uri);
        return document == null ? List.of() : allSymbols.stream().filter(symbol -> symbol.document().uri().equals(uri)).toList();
    }

    public List<SourceSymbol> resolve(String uri, Position position) {
        DocumentAnalysis analysis = documents.get(uri);
        if (analysis == null) return List.of();
        Token token = analysis.document().identifierAt(position);
        if (token == null) return List.of();
        for (SourceSymbol symbol : documentSymbols(uri))
            if (!symbol.implicit() && symbol.nameToken().getTokenIndex() == token.getTokenIndex()) return List.of(symbol);
        ParseTree leaf = analysis.nodeFor(token);
        if (leaf == null) return List.of();
        String domain = analysis.document().domain();
        for (ParseTree ancestor = leaf.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof SppParser.QualifiedNameContext || ancestor instanceof AppParser.QualifiedNameContext)
                return resolvePath(analysis, (ParserRuleContext) ancestor, token);
            if (ancestor instanceof SppParser.EntitytypeContext || ancestor instanceof SppParser.RoletypeContext
                    || ancestor instanceof SppParser.ParticipantConstructionContext)
                return topLevel(domain, token.getText(), ProjectAnalysis::isType);
            if (ancestor instanceof SppParser.MapfieldContext field)
                return resolveMapping(analysis, field, token);
            if (ancestor instanceof SppParser.AtomiccallContext call && call.Identifier() != null
                    && call.Identifier().getSymbol().getTokenIndex() == token.getTokenIndex())
                return serviceTargets(domain, token.getText());
            if (ancestor instanceof AppParser.ScopeItemIdentifierContext
                    || ancestor instanceof AppParser.InterceptionHookContext
                    || ancestor instanceof AppParser.InterceptionTargetsContext)
                return serviceTargets(domain, token.getText());
            if (ancestor instanceof AppParser.LocalServiceBindingContext binding && binding.Identifier() != null
                    && binding.Identifier().getSymbol().getTokenIndex() == token.getTokenIndex())
                return serviceTargets(domain, token.getText());
            if (ancestor instanceof AppParser.ServiceContext implementation
                    && implementation.Identifier().getSymbol().getTokenIndex() != token.getTokenIndex()) break;
            if (ancestor instanceof AppParser.TablesContext tables
                    && tables.Identifier().getSymbol().getTokenIndex() == token.getTokenIndex())
                return topLevel(domain, token.getText(), ProjectAnalysis::isType);
            if (ancestor instanceof AppParser.IdentifyItemContext)
                return topLevel(domain, token.getText(), ProjectAnalysis::isType);
            if (ancestor instanceof AppParser.ForeignKeyReferencesContext)
                return topLevel(domain, token.getText(), symbol -> symbol.type().equals("table"));
            if (ancestor instanceof AppParser.ExtendsHeadersContext)
                return topLevel(domain, token.getText(), symbol -> symbol.type().equals("header"));
            if (ancestor instanceof SppParser.ActasContext)
                return topLevel(domain, token.getText(), ProjectAnalysis::isType);
        }
        return List.of();
    }

    public String implicitDescription(String uri, Position position) {
        DocumentAnalysis analysis = documents.get(uri);
        if (analysis == null) return "";
        Token token = analysis.document().identifierAt(position);
        if (token == null) return "";
        ParseTree leaf = analysis.nodeFor(token);
        for (ParseTree ancestor = leaf; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof SppParser.RoletypeContext && java.util.Set.of("ResultInfo", "FailureInfo").contains(token.getText()))
                return "Implicit S++ system participant " + token.getText() + ". No explicit source definition exists.";
            if (isServiceReference(ancestor, token)) {
                String domain = analysis.document().domain();
                if (!services(domain, token.getText()).isEmpty()) return "";
                List<SourceSymbol> participants = implicitParticipants(domain, token.getText());
                if (participants.size() != 1) return "";
                SourceSymbol participant = participants.get(0);
                String method = com.servpp.lang.general.NameUtil.firstToLowerCase(token.getText(), true);
                String operation = method.substring(0, method.length() - participant.name().length());
                String effect = switch (operation) {
                    case "find" -> "Load facts into the existing participant by its storage identity; set identified.";
                    case "insert" -> "Insert the participant's own stored facts.";
                    case "update" -> "Update the participant's own stored facts.";
                    default -> "Delete the participant's own stored record.";
                };
                return "S++ built-in (implicit) service: " + token.getText() + "(" + participant.name() + ")"
                        + "\ndomain " + domain + "\n" + effect
                        + "\nSingle participants and collections use the same service name."
                        + "\nThe compiler validates participant arguments and APP/PHY storage binding."
                        + "\nGo to Definition/Declaration opens the participant type that supplies this service."
                        + "\n" + participant.document().uri() + ":" + (participant.selectionRange().getStart().getLine() + 1);
            }
        }
        return "";
    }

    private static boolean isServiceReference(ParseTree node, Token token) {
        return node instanceof SppParser.AtomiccallContext call && call.Identifier() != null
                && call.Identifier().getSymbol().getTokenIndex() == token.getTokenIndex()
                || node instanceof AppParser.ScopeItemIdentifierContext
                || node instanceof AppParser.InterceptionHookContext
                || node instanceof AppParser.InterceptionTargetsContext
                || node instanceof AppParser.LocalServiceBindingContext binding && binding.Identifier() != null
                && binding.Identifier().getSymbol().getTokenIndex() == token.getTokenIndex();
    }

    private List<SourceSymbol> serviceTargets(String domain, String name) {
        List<SourceSymbol> explicit = services(domain, name);
        return explicit.isEmpty() ? implicitParticipants(domain, name) : explicit;
    }

    private List<SourceSymbol> implicitParticipants(String domain, String name) {
        String method = com.servpp.lang.general.NameUtil.firstToLowerCase(name, true);
        for (String operation : List.of("find", "insert", "update", "delete")) {
            if (!method.startsWith(operation)) continue;
            String participant = method.substring(operation.length());
            if (java.util.Set.of("ResultInfo", "FailureInfo").contains(participant)) return List.of();
            List<SourceSymbol> matches = topLevel(domain, participant,
                    symbol -> symbol.kind() == SymbolKind.Class || symbol.kind() == SymbolKind.Interface);
            // An ambiguous participant cannot supply a unique implicit service.
            return matches.size() == 1 ? matches : List.of();
        }
        return List.of();
    }

    private List<SourceSymbol> resolvePath(DocumentAnalysis analysis, ParserRuleContext path, Token token) {
        ParseTree parent = path.getParent();
        if (parent instanceof AppParser.LocalServiceBindingContext || parent instanceof AppParser.ExampleRestartContext
                || parent instanceof AppParser.NumericTypeNameContext || parent instanceof AppParser.PhyNumericBindingContext)
            return List.of();
        if (parent instanceof AppParser.PhyNumericRuleContext)
            for (int child = 1; child < parent.getChildCount(); child++)
                if (parent.getChild(child) == path && parent.getChild(child - 1).getText().equals("using")) return List.of();
        List<Token> parts = new ArrayList<>();
        identifiers(path, parts);
        int selected = -1;
        for (int part = 0; part < parts.size(); part++)
            if (parts.get(part).getTokenIndex() == token.getTokenIndex()) selected = part;
        if (selected < 0 || parts.isEmpty()) return List.of();
        String domain = analysis.document().domain();
        String first = segmentName(analysis.document(), parts.get(0));
        if (path.getParent() instanceof SppParser.EntityContext entity && entity.qualifiedName() == path) {
            if (parts.size() != 2) return List.of();
            return selected == 0 ? topLevel(first, first, symbol -> symbol.kind() == SymbolKind.Namespace)
                    : topLevel(first, parts.get(1).getText(), ProjectAnalysis::isType);
        }
        List<SourceSymbol> current = locals(analysis, first, token.getTokenIndex());
        if (current.isEmpty()) current = topLevel(domain, first,
                symbol -> isType(symbol) || symbol.kind() == SymbolKind.Constant);
        for (int part = 1; part <= selected; part++) {
            List<SourceSymbol> next = new ArrayList<>();
            String member = segmentName(analysis.document(), parts.get(part));
            for (SourceSymbol receiver : current) {
                List<SourceSymbol> owners = isType(receiver) ? List.of(receiver)
                        : topLevel(receiver.domain(), DocumentAnalysis.baseType(receiver.type()), ProjectAnalysis::isType);
                for (SourceSymbol owner : owners) {
                    List<SourceSymbol> direct = members(owner, member);
                    next.addAll(direct.isEmpty() ? inheritedMembers(owner, member, new java.util.HashSet<>()) : direct);
                }
            }
            current = next.stream().distinct().toList();
        }
        return current;
    }

    private List<SourceSymbol> locals(DocumentAnalysis analysis, String name, int tokenIndex) {
        List<SourceSymbol> found = analysis.symbols().stream().filter(symbol -> symbol.kind() == SymbolKind.Variable
                && symbol.name().equals(name) && symbol.visibleAt(tokenIndex)
                && (symbol.implicit() || symbol.nameToken().getTokenIndex() <= tokenIndex)).sorted(
                Comparator.comparingInt((SourceSymbol symbol) -> symbol.visibility().getStart().getTokenIndex()).reversed())
                .toList();
        if (!found.isEmpty()) {
            ParserRuleContext scope = found.get(0).visibility();
            return found.stream().filter(symbol -> symbol.visibility() == scope).toList();
        }
        SourceSymbol implementation = analysis.symbols().stream().filter(symbol -> symbol.type().equals("implementation")
                && tokenIndex >= symbol.context().getStart().getTokenIndex()
                && tokenIndex <= symbol.context().getStop().getTokenIndex()).findFirst().orElse(null);
        String serviceName = implementation == null ? null : implementation.name();
        if (serviceName == null) {
            ParseTree node = analysis.nodeFor(analysis.document().tokens().get(tokenIndex));
            for (ParseTree ancestor = node; ancestor != null; ancestor = ancestor.getParent())
                if (ancestor instanceof AppParser.ScopeItemContext scope && scope.scopeItemIdentifier() != null) {
                    serviceName = scope.scopeItemIdentifier().getText();
                    break;
                }
                else if (ancestor instanceof AppParser.LocalServiceBindingContext binding && binding.Identifier() != null) {
                    serviceName = binding.Identifier().getText();
                    break;
                }
        }
        if (serviceName == null) return List.of();
        List<SourceSymbol> services = services(analysis.document().domain(), serviceName);
        return symbols().stream().filter(symbol -> symbol.kind() == SymbolKind.Variable && symbol.name().equals(name)
                && services.contains(symbol.owner()) && symbol.context() instanceof SppParser.ParameterDeclarationContext).toList();
    }

    private List<SourceSymbol> topLevel(String domain, String name, Predicate<SourceSymbol> predicate) {
        return symbols().stream().filter(symbol -> symbol.owner() == null && domain.equals(symbol.domain())
                && name.equals(symbol.name()) && predicate.test(symbol)).toList();
    }

    private List<SourceSymbol> services(String domain, String name) {
        String canonical = com.servpp.lang.general.NameUtil.firstToLowerCase(name, false);
        return symbols().stream().filter(symbol -> isService(symbol) && symbol.owner() == null && domain.equals(symbol.domain())
                && canonical.equals(com.servpp.lang.general.NameUtil.firstToLowerCase(symbol.name(), false))).toList();
    }

    private List<SourceSymbol> resolveMapping(DocumentAnalysis document, SppParser.MapfieldContext field, Token token) {
        var mapping = document.mappings().stream().filter(candidate -> isAncestor(candidate.context(), field)).findFirst().orElse(null);
        if (mapping == null || field.Identifier().size() < 2) return List.of();
        String target = field.Identifier(0).getText();
        String source = field.Identifier(1).getText();
        if (field.Identifier(0).getSymbol().getTokenIndex() == token.getTokenIndex()) {
            List<SourceSymbol> owners = topLevel(document.document().domain(), mapping.role(), ProjectAnalysis::isType);
            return symbols().stream().filter(candidate -> owners.contains(candidate.owner()) && candidate.name().equals(target)).toList();
        }
        List<SourceSymbol> result = new ArrayList<>();
        for (String performer : mapping.performers()) {
            List<SourceSymbol> types = topLevel(document.document().domain(), performer, ProjectAnalysis::isType);
            if (performer.equals(source)) result.addAll(types);
            else for (SourceSymbol type : types) result.addAll(members(type, source));
        }
        return result.stream().distinct().toList();
    }

    private List<SourceSymbol> members(SourceSymbol owner, String name) {
        return symbols().stream().filter(candidate -> owner.equals(candidate.owner()) && (candidate.name().equals(name)
                || candidate.implicit() && com.servpp.lang.general.NameUtil.participantName(DocumentAnalysis.baseType(candidate.type()),
                (candidate.type().length() - candidate.type().replace("[]", "").length()) / 2).equals(name))).toList();
    }

    private List<SourceSymbol> inheritedMembers(SourceSymbol owner, String name, java.util.Set<String> visited) {
        if (!visited.add(owner.domain() + "." + owner.name())) return List.of();
        List<SourceSymbol> result = new ArrayList<>();
        for (DocumentAnalysis document : documents.values()) {
            if (!document.document().domain().equals(owner.domain())) continue;
            for (var mapping : document.mappings()) {
                if (!mapping.takeAll() || !mapping.role().equals(owner.name())) continue;
                boolean explicitlyMapped = mapping.context().mapBody().mapDeclaration().stream()
                        .filter(declaration -> declaration.mapfield() != null)
                        .anyMatch(declaration -> declaration.mapfield().Identifier().size() >= 2
                                && declaration.mapfield().Identifier(1).getText().equals(name));
                if (explicitlyMapped) continue;
                for (String performer : mapping.performers())
                    for (SourceSymbol type : topLevel(owner.domain(), performer, ProjectAnalysis::isType)) {
                        List<SourceSymbol> found = members(type, name);
                        result.addAll(found.isEmpty() ? inheritedMembers(type, name, visited) : found);
                    }
            }
        }
        return result.stream().distinct().toList();
    }

    private static boolean isAncestor(ParseTree ancestor, ParseTree node) {
        for (ParseTree parent = node.getParent(); parent != null; parent = parent.getParent()) if (parent == ancestor) return true;
        return false;
    }

    private static String segmentName(SourceDocument document, Token identifier) {
        StringBuilder name = new StringBuilder(identifier.getText());
        List<Token> tokens = document.tokens();
        boolean bracket = false;
        for (int tokenIndex = identifier.getTokenIndex() + 1; tokenIndex < tokens.size(); tokenIndex++) {
            Token token = tokens.get(tokenIndex);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) continue;
            if (!bracket && token.getText().equals("[")) bracket = true;
            else if (bracket && token.getText().equals("]")) { name.append("Array"); bracket = false; }
            else break;
        }
        return name.toString();
    }

    private static boolean isType(SourceSymbol symbol) {
        return symbol.kind() == SymbolKind.Class || symbol.kind() == SymbolKind.Interface
                || symbol.kind() == SymbolKind.Enum || symbol.type().equals("header");
    }

    private static boolean isService(SourceSymbol symbol) { return symbol.kind() == SymbolKind.Function; }

    private static void identifiers(ParseTree tree, List<Token> tokens) {
        if (tree instanceof TerminalNode terminal) {
            Token token = terminal.getSymbol();
            if (token.getText().matches("[\\p{L}_$][\\p{L}\\p{N}_$]*")) tokens.add(token);
            return;
        }
        for (int child = 0; child < tree.getChildCount(); child++) identifiers(tree.getChild(child), tokens);
    }
}
