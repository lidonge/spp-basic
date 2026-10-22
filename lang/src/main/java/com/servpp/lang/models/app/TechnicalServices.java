package com.servpp.lang.models.app;

import com.servpp.lang.models.*;
import java.util.*;
import java.util.stream.Collectors;

/** APP matching and PHY implementations, kept out of the business service graph. */
public final class TechnicalServices {
    public record Implementation(String className, Map<String, String> configuration) { }
    public record Hook(List<String> targets, String phase, List<String> services) { }
    public record Binding(String phase, String name, Implementation implementation) { }
    private final Set<String> names = new LinkedHashSet<>();
    private final Map<String, Implementation> implementations = new LinkedHashMap<>();
    private final List<Hook> hooks = new ArrayList<>();
    public Set<String> names() { return names; }
    public Map<String, Implementation> implementations() { return implementations; }
    public List<Hook> hooks() { return hooks; }

    public List<String> validate(SppDomain domain) {
        var errors = new ArrayList<String>();
        for (String name : names) {
            if (!implementations.containsKey(name)) errors.add("Technical service " + name + " requires a PHY implementation.");
        }
        for (var item : implementations.entrySet()) {
            if (!names.contains(item.getKey())) errors.add("Undeclared technical service " + item.getKey());
            if (!item.getValue().className().matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))
                errors.add("Invalid technical implementation class " + item.getValue().className());
        }
        var services = domain.getMapsOfClass().values().stream().filter(unit -> unit instanceof SppService)
                .map(unit -> (SppService)unit).toList();
        for (var hook : hooks) {
            if (!Set.of("before", "after", "failure").contains(hook.phase())) errors.add("Unknown interception phase " + hook.phase());
            for (String name : hook.services()) if (!names.contains(name)) errors.add("Undeclared technical service " + name);
            for (String target : hook.targets()) {
                if (target.equals("services") || target.equals("scenarios")) {
                    if (hook.targets().size() != 1) errors.add("Global interception target must appear alone.");
                } else if (services.stream().noneMatch(service -> service.getFuncName().equals(target)))
                    errors.add("Unknown interception service " + target);
            }
        }
        for (var service : services) {
            var bindings = new ArrayList<Binding>();
            var seen = new HashSet<String>();
            for (var hook : hooks) if (matches(hook, service)) for (String name : hook.services()) {
                if (!seen.add(hook.phase() + ":" + name)) errors.add("Duplicate interception " + service.getFuncName() + " " + hook.phase() + " " + name);
                var implementation = implementations.get(name);
                if (implementation != null) bindings.add(new Binding(hook.phase(), name, implementation));
            }
            service.setTechnicalBindings(List.copyOf(bindings));
        }
        return errors;
    }
    private boolean matches(Hook hook, SppService service) {
        return hook.targets().contains("services") || hook.targets().contains(service.getFuncName())
                || hook.targets().contains("scenarios") && service instanceof SppScenario;
    }
    public static String quote(String value) {
        var out = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '\\' -> out.append("\\\\"); case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> out.append(ch);
            }
        }
        return out.append('"').toString();
    }
    public static String javaBinding(Binding binding) {
        String config = binding.implementation().configuration().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "java.util.Map.entry(" + quote(entry.getKey()) + ", " + quote(entry.getValue()) + ")")
                .collect(Collectors.joining(", "));
        return "new com.servpp.service.technical.ServiceInterceptors.Binding("
                + "com.servpp.service.technical.TechnicalInvocation.Phase." + binding.phase().toUpperCase(Locale.ROOT)
                + ", " + quote(binding.name()) + ", " + quote(binding.implementation().className())
                + ", java.util.Map.ofEntries(" + config + "))";
    }
}
