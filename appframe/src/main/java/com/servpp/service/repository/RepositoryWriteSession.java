package com.servpp.service.repository;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/** One generated update invocation; the enclosing declared boundary owns commit and rollback. */
public final class RepositoryWriteSession {
    private final ObjectRepository repository;
    private final List<RepositoryWriteRule> rules;
    private final Object[] arguments;
    private final List<Object> expectedVersions;
    private final List<String> expectedIds;
    private final List<java.util.Map<Object, ExpectedRow>> expectedRows = new ArrayList<>();
    private record ExpectedRow(Object version, String identity) { }

    private RepositoryWriteSession(ObjectRepository repository, List<RepositoryWriteRule> rules, Object[] arguments) {
        this.repository = repository;
        this.rules = List.copyOf(rules);
        this.arguments = arguments.clone();
        this.expectedVersions = new ArrayList<>();
        this.expectedIds = new ArrayList<>();
        for (RepositoryWriteRule rule : rules) {
            var version = rule.versionCheck();
            if (rule.operation() == RepositoryWriteRule.Operation.DELETE && version != null) {
                throw new IllegalStateException("PERSISTENCE_DELETE_VERSION_UNSUPPORTED");
            }
            java.util.Map<Object, ExpectedRow> rows = new java.util.IdentityHashMap<>();
            expectedRows.add(rows);
            Object root = argument(rule.argumentIndex());
            if (version != null && (root instanceof Collection<?> || root != null && root.getClass().isArray())) {
                if (version.expectedArgumentIndex() != rule.argumentIndex())
                    throw new IllegalStateException("PERSISTENCE_COLLECTION_VERSION_REQUIRES_INTERNAL_BASELINE");
                List<Object> members = new ArrayList<>();
                if (root instanceof Collection<?> collection) members.addAll(collection);
                else for (int i = 0; i < Array.getLength(root); i++) members.add(Array.get(root, i));
                java.util.Set<String> identities = new java.util.HashSet<>();
                for (Object member : members) {
                    Object baseline = RepositoryParticipantSnapshots.require(member);
                    String identity = expectedIdentity(rule, member, baseline);
                    if (!identities.add(identity)) throw new IllegalStateException("PERSISTENCE_COLLECTION_IDENTITY_DUPLICATED");
                    Object expected = version.expectedPath().startsWith("@platform:")
                            ? RepositoryParticipantSnapshots.platform(member, version.expectedPath().substring("@platform:".length()))
                            : ObjectPropertyAccess.read(baseline, version.expectedPath());
                    rows.put(member, new ExpectedRow(expected, identity));
                }
                expectedVersions.add(null); expectedIds.add(null); continue;
            }
            Object baseline = version == null ? null : argument(version.expectedArgumentIndex());
            if (version != null && version.expectedArgumentIndex() == rule.argumentIndex())
                baseline = RepositoryParticipantSnapshots.require(baseline);
            expectedVersions.add(version == null ? null : version.expectedPath().startsWith("@platform:")
                    ? RepositoryParticipantSnapshots.platform(argument(rule.argumentIndex()),version.expectedPath().substring("@platform:".length()))
                    : ObjectPropertyAccess.read(baseline, version.expectedPath()));
            expectedIds.add(version == null ? null : expectedIdentity(rule, argument(rule.argumentIndex()), baseline));
        }
    }

    public static RepositoryWriteSession prepare(Supplier<ObjectRepository> repositorySupplier,
                                                 List<RepositoryWriteRule> rules, Object[] arguments) {
        if (repositorySupplier == null) { throw new IllegalStateException("PERSISTENCE_REPOSITORY_NOT_BOUND"); }
        ObjectRepository repository = repositorySupplier.get();
        if (repository == null) { throw new IllegalStateException("PERSISTENCE_REPOSITORY_NOT_BOUND"); }
        repository.verifyWriteTransaction();
        if (rules == null || rules.isEmpty()) { throw new IllegalStateException("PERSISTENCE_EMPTY_BINDING"); }
        return new RepositoryWriteSession(repository, rules, arguments);
    }

    public void persist() {
        for (int i = 0; i < rules.size(); i++) {
            RepositoryWriteRule rule = rules.get(i);
            Object target = argument(rule.argumentIndex());
            if (target == null) { throw new IllegalStateException("PERSISTENCE_MISSING_TARGET"); }
            if (target instanceof Collection<?> collection) {
                for (Object item : collection) { writeMember(i, rule, item); }
            } else if (target.getClass().isArray()) {
                for (int j = 0; j < Array.getLength(target); j++) { writeMember(i, rule, Array.get(target, j)); }
            } else { write(rule, target, expectedVersions.get(i), expectedIds.get(i)); }
        }
    }

    private void writeMember(int index, RepositoryWriteRule rule, Object member) {
        if (rule.versionCheck() == null) { write(rule, member, null, null); return; }
        ExpectedRow baseline = expectedRows.get(index).get(member);
        if (baseline == null) throw new IllegalStateException("PERSISTENCE_COLLECTION_MEMBER_CHANGED");
        write(rule, member, baseline.version(), baseline.identity());
    }

    private Object argument(int index) {
        if (index < 0 || index >= arguments.length) { throw new IllegalStateException("PERSISTENCE_ARGUMENT_NOT_FOUND"); }
        return arguments[index];
    }

    private void write(RepositoryWriteRule rule, Object value, Object expectedVersion, String expectedId) {
        if (value == null) { throw new IllegalStateException("PERSISTENCE_MISSING_TARGET"); }
        String id = objectId(rule, value);
        if (expectedId != null && !expectedId.equals(id)) { throw new IllegalStateException("PERSISTENCE_IDENTITY_CHANGED"); }
        switch (rule.operation()) {
            case INSERT_IF_ABSENT -> repository.insertIfAbsent(rule.objectType(), id, value);
            case INSERT -> {
                repository.insert(rule.objectType(), id, value);
                RepositoryParticipantSnapshots.remember(value, repository, rule.objectType(), id);
            }
            case UPDATE -> {
                if (rule.versionCheck() == null) { repository.updateFromRead(rule.objectType(), id, value); }
                else {
                    if (rule.versionCheck().proposedPath().startsWith("@platform:")) {
                        if (!rule.versionCheck().advanceVersion()) throw new IllegalStateException("PLATFORM_VERSION_REQUIRES_ADVANCE");
                        repository.compareAndSetPlatform(rule.objectType(),id,value,rule.versionCheck().proposedPath().substring("@platform:".length()),expectedVersion);
                        RepositoryParticipantSnapshots.remember(value,repository,rule.objectType(),id);
                        break;
                    }
                    if (rule.versionCheck().advanceVersion()) {
                        if (!(expectedVersion instanceof Number)) throw new IllegalStateException("PERSISTENCE_VERSION_REQUIRED");
                        BigDecimal next = new BigDecimal(expectedVersion.toString()).add(BigDecimal.ONE);
                        setVersion(value, rule.versionCheck().proposedPath(), next);
                    }
                    repository.compareAndSet(rule.objectType(), id, value, rule.versionCheck().proposedPath(), expectedVersion);
                    if (rule.versionCheck().expectedArgumentIndex() == rule.argumentIndex())
                        RepositoryParticipantSnapshots.remember(value);
                }
            }
            case UPSERT -> repository.save(rule.objectType(), id, value);
            case DELETE -> repository.delete(rule.objectType(), id);
        }
    }

    private void setVersion(Object target, String path, BigDecimal value) {
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) target = ObjectPropertyAccess.read(target, parts[i]);
        if (target == null) throw new IllegalStateException("PERSISTENCE_VERSION_TARGET_REQUIRED");
        String property = parts[parts.length - 1];
        String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (var method : target.getClass().getMethods()) {
            if (!method.getName().equals(setter) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            Object converted;
            if (type == long.class || type == Long.class) converted = value.longValueExact();
            else if (type == int.class || type == Integer.class) converted = value.intValueExact();
            else if (type == BigDecimal.class) converted = value;
            else if (type == java.math.BigInteger.class) converted = value.toBigIntegerExact();
            else throw new IllegalStateException("PERSISTENCE_VERSION_TYPE_UNSUPPORTED");
            try { method.invoke(target, converted); return; }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("PERSISTENCE_VERSION_WRITE_FAILED", failure); }
        }
        throw new IllegalStateException("PERSISTENCE_VERSION_SETTER_REQUIRED");
    }

    private String objectId(RepositoryWriteRule rule, Object value) {
        if (rule.keyPaths().equals(List.of("@generatedIdentity")))
            return RepositoryParticipantSnapshots.identityOrCreate(value,rule.objectType());
        if (rule.keyPaths().isEmpty()) { throw new IllegalStateException("PERSISTENCE_MISSING_KEY"); }
        List<String> keys = rule.keyPaths().stream().map(path -> "@singleton".equals(path) ? "singleton" : key(identityProperty(rule, value, path))).toList();
        return keys.size() == 1 ? keys.get(0) : CompositeObjectId.encode(keys);
    }

    private String expectedIdentity(RepositoryWriteRule rule, Object participant, Object baseline) {
        if (rule.keyPaths().stream().anyMatch(path -> path.startsWith("@performer:") || "@storageIdentity".equals(path) || path.endsWith(".@storageIdentity")))
            return RepositoryParticipantSnapshots.objectIdentity(participant, rule.objectType())
                    .orElseThrow(() -> new IllegalStateException("PERSISTENCE_READ_IDENTITY_REQUIRED"));
        return objectId(rule, baseline);
    }

    private Object identityProperty(RepositoryWriteRule rule, Object value, String path) {
        if ("@storageIdentity".equals(path))
            return RepositoryParticipantSnapshots.objectIdentity(value, rule.objectType())
                    .orElseThrow(() -> new IllegalStateException("PERSISTENCE_READ_IDENTITY_REQUIRED"));
        if (!path.startsWith("@performer:")) return ObjectPropertyAccess.read(value, path);
        String source = path.substring("@performer:".length());
        int separator = source.indexOf('.');
        if (separator < 1) throw new IllegalStateException("PERSISTENCE_PERFORMER_KEY_INVALID");
        String type = source.substring(0, separator);
        var performers = com.servpp.service.runtime.RoleIdentityContext.performers(value, rule.objectType())
                .orElseThrow(() -> new IllegalStateException("PERSISTENCE_PERFORMER_IDENTITY_REQUIRED"));
        var matches = performers.stream().filter(actor -> actor.getClass().getSimpleName().equals(type)).toList();
        if (matches.size() != 1) throw new IllegalStateException("PERSISTENCE_PERFORMER_IDENTITY_AMBIGUOUS");
        return ObjectPropertyAccess.read(matches.get(0), source.substring(separator + 1));
    }

    private String key(Object value) {
        if (value == null) { throw new IllegalStateException("PERSISTENCE_MISSING_KEY"); }
        String key = value instanceof Number ? new BigDecimal(value.toString()).stripTrailingZeros().toPlainString() : value.toString();
        if (key.isBlank()) { throw new IllegalStateException("PERSISTENCE_MISSING_KEY"); }
        return key;
    }
}
