package com.servpp.lang.models;

import com.servpp.lang.models.app.LocalServiceBinding;
import com.servpp.lang.general.IConstance;
import java.util.List;

/** Parameter compatibility is participant identity or an explicit take, never bean shape. */
public final class ParticipantRoleBindings {
    private ParticipantRoleBindings() { }
    /** Framework identity paths are generated from APP keys; they are not SPP properties. */
    public static boolean isStorageIdentityPath(SppDomain domain, SppClass type, String path) {
        String[] parts=path.split("\\.");
        if (!"@storageIdentity".equals(parts[parts.length-1])) return false;
        for(int i=0;i<parts.length-1;i++) {
            var field=type.getField(parts[i]);
            if(field==null || field.getArrayDimension()!=0 || !(field.getType() instanceof SppClass nested)) return false;
            type=nested;
        }
        if(type.getReferenceSource()!=null && domain.getProject()!=null) {
            String[] source=type.getReferenceSource().split("\\.");
            var owner=source.length==2?domain.getProject().getDomain(source[0]):null;
            var original=owner==null?null:owner.getSppClass(source[1]);
            if(!(original instanceof SppClass participant))return false;
            domain=owner;type=participant;
        }
        String name=type.getName();
        return domain.getRuleBlock().getAppTables().stream().flatMap(group->group.getAppTableList().stream())
                .anyMatch(table->table.getEntity()!=null && table.getEntity().getName().equals(name) && table.isIndependentIdentity()
                    && table.getAppColumns().stream().filter(column->column.isPrimaryKey() && !column.isObjectBacked()).count()==1);
    }
    public static String identity(SppDomain domain, SppClass type) {
        return type.getReferenceSource() == null ? domain.getName() + "." + type.getName() : type.getReferenceSource();
    }
    public static LocalServiceBinding.ParticipantRole resolve(SppDomain local, SppDomain owner, SppClass from, SppClass to) {
        String target = identity(owner, to);
        if (identity(local, from).equals(target)) return new LocalServiceBinding.ParticipantRole(from, null, null);
        var views = local.getMapsOfClass().values().stream().filter(unit -> unit instanceof SppClass view
                && target.equals(identity(local, view))).map(unit -> (SppClass)unit).toList();
        var candidates = local.getMapsOfClass().values().stream().filter(unit -> unit instanceof SppRoleMapper mapper
                && mapper.getEntities() != null && mapper.getEntities().size() == 1 && mapper.getEntities().get(0) == from
                && views.stream().anyMatch(view -> view == mapper.getRole())).map(unit -> (SppRoleMapper)unit).toList();
        if (candidates.size() == 1) {
            var mapper = candidates.get(0);
            return new LocalServiceBinding.ParticipantRole(mapper.getRole(), mapper, from.getName());
        }
        if (!candidates.isEmpty()) return null;
        SppDomain roleOwner = local;
        SppClass declaredRole = from;
        if (from.getReferenceSource() != null && local.getProject() != null) {
            String[] source = from.getReferenceSource().split("\\.");
            roleOwner = source.length == 2 ? local.getProject().getDomain(source[0]) : null;
            var original = roleOwner == null ? null : roleOwner.getSppClass(source[1]);
            if (!(original instanceof SppClass type)) return null;
            declaredRole = type;
        }
        if (roleOwner == null) return null;
        var roleType = declaredRole;
        var sourceDomain = roleOwner;
        var reverse = roleOwner.getMapsOfClass().values().stream()
                .filter(unit -> unit instanceof SppRoleMapper mapping && mapping.getRole() == roleType)
                .map(unit -> (SppRoleMapper)unit).toList();
        // A reverse call must identify one declared performer, never choose a take alternative.
        if (reverse.size() != 1 || reverse.get(0).getEntities() == null || reverse.get(0).getEntities().size() != 1) return null;
        var mapper = reverse.get(0);
        var performer = mapper.getEntities().get(0);
        if (!target.equals(identity(sourceDomain, performer))) return null;
        return new LocalServiceBinding.ParticipantRole(performer, mapper, performer.getName(), true, sourceDomain.getName());
    }
    public static List<String> primaryKeys(SppDomain domain, SppClass type) {
        if (type.getReferenceSource() != null && domain.getProject() != null) {
            String[] source=type.getReferenceSource().split("\\.");
            var owner=source.length==2?domain.getProject().getDomain(source[0]):null;
            var original=owner==null?null:owner.getSppClass(source[1]);
            if (owner!=null && original instanceof SppClass participant && participant.getReferenceSource()==null)
                return primaryKeys(owner,participant);
            return List.of();
        }

        var handler = new com.servpp.lang.general.app.RuleBlockHandler(new java.io.File(domain.getName() + ".app"));
        handler.setSppDomain(domain);
        var context = new org.antlr.v4.runtime.ParserRuleContext();
        context.start = new org.antlr.v4.runtime.CommonToken(0, "participant identity");
        boolean mapped = domain.getRuleBlock().getAppTables().stream().flatMap(group -> group.getAppTableList().stream())
                .anyMatch(table -> table.getEntity() == type);
        if (!mapped) return List.of();
        boolean independent = domain.getRuleBlock().getAppTables().stream().flatMap(group -> group.getAppTableList().stream())
                .anyMatch(table -> table.getEntity().getName().equals(type.getName()) && table.isIndependentIdentity());
        if (independent) return List.of("@storageIdentity");
        var inheritedIdentity=domain.inferSinglePerformerPlatformIdentity(type);
        if (!inheritedIdentity.isEmpty()) return inheritedIdentity.stream().map(key -> key.getObjectIdPath()).toList();
        return handler.repositoryTargetsFromAppDdl(context, type.getName()).stream().map(key -> key.getObjectIdPath()).toList();
    }
    private static boolean hasPath(SppDomain domain,SppClass type, String path) {
        if (isStorageIdentityPath(domain,type,path)) return true;
        String[] names = path.split("\\.");
        for (int index = 0; index < names.length; index++) {
            var field = type.getField(names[index]);
            if (field == null) return false;
            if (index < names.length - 1) {
                if (!(field.getType() instanceof SppClass nested) || field.getArrayDimension() != 0) return false;
                type = nested;
            }
        }
        return true;
    }
    public static void configure(SppProject project, LocalServiceBinding binding, SppDomain local, SppDomain owner,
                                 SppService caller, SppService provider, List<String> errors) {
        var inputs = caller.getServiceBody().getSppLocalVarList();
        var targets = provider.getServiceBody().getSppLocalVarList();
        String prefix = local.getName() + ": binding " + caller.getFuncName() + " -> " + binding.getTarget();
        if (inputs.size() != targets.size()) { errors.add(prefix + " requires the same participant count"); return; }
        for (int index = 0; index < inputs.size(); index++) {
            var from = inputs.get(index); var to = targets.get(index);
            if (!(from.getType() instanceof SppClass fromType) || !(to.getType() instanceof SppClass toType)
                    || from.getArrayDimension() != to.getArrayDimension()) {
                errors.add(prefix + " participant " + index + " requires matching collection dimensions"); continue;
            }
            var role = resolve(local, owner, fromType, toType);
            if (role == null) { errors.add(prefix + " participant " + fromType.getName() + " must explicitly take the provider role " + identity(owner, toType)); continue; }
            if (role.performerView() && from.getArrayDimension() != 0) {
                errors.add(prefix + " reverse performer calls currently require a scalar role; collection identity matching is not supported");
                continue;
            }
            binding.getParticipantRoles().put(index, role);
            // A provider Reference specifies its declared business view, not a bean-field intersection.
            var declaredFacts = toType.getReferenceSource() != null && role.mapper() == null
                    ? toType.getSppFieldList() : role.view().getSppFieldList();
            binding.getParticipantFacts().put(index, declaredFacts.stream().map(SppField::getName).toList());
            for (var fact : declaredFacts) {
                var expected = toType.getField(fact.getName());
                var available = role.view().getField(fact.getName());
                if (available == null) errors.add(prefix + " lacks declared participant fact " + fact.getName());
                if (expected == null || fact.getArrayDimension() != expected.getArrayDimension()
                        || !fact.getType().getName().equals(expected.getType().getName()))
                    errors.add(prefix + " has incompatible declared provider role fact " + fact.getName());
            }
            if (from.getArrayDimension() > 0) {
                SppDomain identityOwner = owner; SppClass identityType = toType;
                if (toType.getReferenceSource() != null) {
                    String[] source = toType.getReferenceSource().split("\\.");
                    identityOwner = project.getDomain(source[0]);
                    identityType = identityOwner == null ? null : (SppClass) identityOwner.getSppClass(source[1]);
                }
                var keys = identityOwner == null || identityType == null ? List.<String>of() : primaryKeys(identityOwner, identityType);
                if (keys.isEmpty()) errors.add(prefix + " collection requires provider APP primary keys");
                if (!keys.stream().allMatch(key -> hasPath(local,role.view(),key))) errors.add(prefix + " provider primary key is absent from the declared participant role");
                binding.getParticipantIdentities().put(index, keys);
                // A query's declared repository destination is its output collection.
                boolean destination = provider.getScopeItem() != null && provider.getScopeItem().getRepositoryHydrationRules().stream()
                        .anyMatch(rule -> rule.responseMethodName().equals(to.getName()));
                // Calculations may create collection members; preserve matched instances but admit new ones.
                if (provider.getServiceType() != IConstance.ServiceType.calculate
                        && (provider.getServiceType() != IConstance.ServiceType.query || !destination))
                    binding.getExistingCollections().add(index);
            }
        }
    }
}
