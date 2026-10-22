package com.servpp.service.repository;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.servpp.service.DelegatingService;
import com.servpp.service.Service;
import com.servpp.service.runtime.GeneratedRoleIdentityContextBinder;
import com.servpp.service.runtime.ScopeMetadataProvider;

public final class RepositoryHydratingServiceProxy {
    private RepositoryHydratingServiceProxy() {
    }

    public static Service decorate(Service delegate, ObjectRepository repository,
                                   List<HydrationRule> rules) {
        return decorate(delegate, () -> repository, rules);
    }

    public static Service decorate(Service delegate, Supplier<ObjectRepository> repositorySupplier,
                                   List<HydrationRule> rules) {
        return decorate(delegate, repositorySupplier, rules, RoleIdentityResolver.none());
    }

    public static Service decorate(Service delegate, Supplier<ObjectRepository> repositorySupplier,
                                   List<HydrationRule> rules, RoleIdentityResolver roleIdentityResolver) {
        Set<Class<?>> interfaces = collectInterfaces(delegate.getClass());
        interfaces.add(Service.class);
        interfaces.add(DelegatingService.class);
        List<String> serviceParameterNames = delegate instanceof ScopeMetadataProvider provider
                ? provider.getServiceParameterNames()
                : List.of();
        InvocationHandler handler = new RepositoryHydratingInvocationHandler(delegate, repositorySupplier, rules,
                roleIdentityResolver == null ? RoleIdentityResolver.none() : roleIdentityResolver,
                serviceParameterNames);
        return (Service) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                interfaces.toArray(Class[]::new), handler);
    }

    private static Set<Class<?>> collectInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = type;
        while (current != null) {
            collectInterfaces(current, interfaces);
            current = current.getSuperclass();
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> interfaces) {
        for (Class<?> item : type.getInterfaces()) {
            interfaces.add(item);
            collectInterfaces(item, interfaces);
        }
    }

    public record HydrationRule(String methodName, String objectType, List<KeyTarget> keyTargets,
                                String responseMethodName, String repositoryName, boolean appDdlInferred,
                                String sourceHint, boolean optional, boolean readLocked, Boolean ordinaryRead) {
        public HydrationRule(String methodName, String objectType, List<KeyTarget> keyTargets,
                             String responseMethodName, String repositoryName, boolean appDdlInferred,
                             String sourceHint, boolean optional, boolean readLocked) {
            this(methodName, objectType, keyTargets, responseMethodName, repositoryName, appDdlInferred, sourceHint, optional, readLocked, null);
        }
        public HydrationRule transactionRead(boolean ordinaryParticipant) {
            return new HydrationRule(methodName, objectType, keyTargets, responseMethodName, repositoryName, appDdlInferred, sourceHint, optional, readLocked, ordinaryParticipant);
        }
        public HydrationRule(String methodName, String objectType, List<KeyTarget> keyTargets,
                             String responseMethodName, String repositoryName, boolean appDdlInferred,
                             String sourceHint, boolean optional) {
            this(methodName,objectType,keyTargets,responseMethodName,repositoryName,appDdlInferred,sourceHint,optional,false);
        }
        public HydrationRule(String methodName, String objectType, List<KeyTarget> keyTargets,
                             String responseMethodName, String repositoryName, boolean appDdlInferred, String sourceHint) {
            this(methodName,objectType,keyTargets,responseMethodName,repositoryName,appDdlInferred,sourceHint,false);
        }
        public HydrationRule asOptional() {
            return new HydrationRule(methodName,objectType,keyTargets,responseMethodName,repositoryName,appDdlInferred,sourceHint,true,readLocked,ordinaryRead);
        }
        public HydrationRule forUpdate() {
            return new HydrationRule(methodName,objectType,keyTargets,responseMethodName,repositoryName,appDdlInferred,sourceHint,optional,true,ordinaryRead);
        }
        public HydrationRule {
            if (readLocked && (responseMethodName == null || responseMethodName.isBlank())) {
                throw new IllegalArgumentException("Locked repository query requires an explicit scalar response");
            }
            if ((objectType == null || objectType.isBlank()) && keyTargets != null && !keyTargets.isEmpty()) {
                objectType = keyTargets.get(0).objectType();
            }
            keyTargets = keyTargets == null ? List.of() : List.copyOf(keyTargets);
            sourceHint = sourceHint == null || sourceHint.isBlank() ? null : sourceHint;
        }

        public HydrationRule(String methodName, List<KeyTarget> keyTargets, String responseMethodName,
                             String repositoryName) {
            this(methodName, null, keyTargets, responseMethodName, repositoryName);
        }

        public HydrationRule(String methodName, String objectType, List<KeyTarget> keyTargets,
                             String responseMethodName, String repositoryName) {
            this(methodName, objectType, keyTargets, responseMethodName, repositoryName, false);
        }

        public HydrationRule(String methodName, String objectType, List<KeyTarget> keyTargets,
                             String responseMethodName, String repositoryName, boolean appDdlInferred) {
            this(methodName, objectType, keyTargets, responseMethodName, repositoryName, appDdlInferred, null);
        }

        public HydrationRule(String methodName, int argumentIndex, String objectType,
                             String objectIdPath, String responseMethodName, String repositoryName) {
            this(methodName, objectType, List.of(new KeyTarget(argumentIndex, objectType, objectIdPath)),
                    responseMethodName, repositoryName);
        }

        public HydrationRule(String methodName, int argumentIndex, String objectType,
                             String objectIdPath, String responseMethodName, String repositoryName,
                             boolean appDdlInferred) {
            this(methodName, objectType, List.of(new KeyTarget(argumentIndex, objectType, objectIdPath)),
                    responseMethodName, repositoryName, appDdlInferred);
        }

        public HydrationRule(String methodName, int argumentIndex, String objectType,
                             String objectIdPath, String responseMethodName, String repositoryName,
                             boolean appDdlInferred, String sourceHint) {
            this(methodName, objectType, List.of(new KeyTarget(argumentIndex, objectType, objectIdPath)),
                    responseMethodName, repositoryName, appDdlInferred, sourceHint);
        }

        public HydrationRule(String methodName, int argumentIndex, String objectType,
                             String objectIdPath, String responseMethodName) {
            this(methodName, argumentIndex, objectType, objectIdPath, responseMethodName, null);
        }

        public int argumentIndex() {
            return firstKey().argumentIndex();
        }

        public String objectIdPath() {
            return firstKey().objectIdPath();
        }

        public KeyTarget firstKey() {
            if (keyTargets.isEmpty()) {
                throw new IllegalStateException("Repository hydration rule " + methodName
                        + " must declare at least one key target.");
            }
            return keyTargets.get(0);
        }

        public record KeyTarget(int argumentIndex, String objectType, String objectIdPath,
                                String repositoryObjectIdPath) {
            public KeyTarget(int argumentIndex, String objectType, String objectIdPath) {
                this(argumentIndex, objectType, objectIdPath, objectIdPath);
            }
        }
    }

    public interface RoleIdentityResolver {
        Optional<RoleIdentity> resolve(String roleType, Object[] arguments);

        static RoleIdentityResolver none() {
            return (roleType, arguments) -> Optional.empty();
        }
    }

    public record RoleIdentity(String roleType, List<String> keyPaths, List<Object> keyValues,
                               List<String> platformColumns, String source, boolean complete) {
        public RoleIdentity {
            keyPaths = keyPaths == null ? List.of() : List.copyOf(keyPaths);
            keyValues = keyValues == null ? List.of() : List.copyOf(keyValues);
            platformColumns = platformColumns == null ? List.of() : List.copyOf(platformColumns);
        }
    }

    private record RepositoryHydratingInvocationHandler(Service delegate,
                                                        Supplier<ObjectRepository> repositorySupplier,
                                                        List<HydrationRule> rules,
                                                        RoleIdentityResolver roleIdentityResolver,
                                                        List<String> serviceParameterNames)
            implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObject(proxy, method, args);
            }
            if ("getDelegateService".equals(method.getName()) && method.getParameterCount() == 0) {
                return delegate;
            }
            if ("execute".equals(method.getName()) && method.getParameterCount() == 1) {
                return invokeExecuteEnvelope(method, args);
            }
            HydrationRule rule = effectiveReadRule(ruleFor(method), args);
            if (participantTarget(rule)) { hydrateParticipant(rule, args); return invokeDelegate(method, args); }
            Object hydratedArgument = null;
            Object queryKey = null;
            Method responseWriter = null;
            boolean collectionQuery = false;
            if (rule != null) {
                if (rule.responseMethodName() != null && method.getReturnType() != Void.TYPE) {
                    responseWriter = findResponseWriter(method.getReturnType(), rule.responseMethodName())
                            .orElse(null);
                    collectionQuery = responseWriter != null && isCollectionWriter(responseWriter);
                }
                if (collectionQuery) {
                    queryKey = keyValues(rule, args).get(0);
                    requireAllKeyValues(rule, args);
                } else {
                    hydratedArgument = hydrateArgument(rule, args);
                }
            }
            Object response;
            try {
                response = method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (rule != null && rule.responseMethodName() != null && response == null) {
                throw new IllegalStateException("Repository query " + rule.methodName()
                        + " returned null response for target " + rule.responseMethodName());
            }
            if (rule != null && rule.responseMethodName() != null) {
                if (collectionQuery) {
                    writeResponse(response, responseWriter, queryCollection(rule, queryKey, responseWriter, args));
                } else {
                    writeResponse(response, rule.responseMethodName(), hydratedArgument);
                }
            }
            return response;
        }

        private Object invokeExecuteEnvelope(Method method, Object[] args) throws Throwable {
            Object request = lastArgument(args);
            if (request == null || serviceParameterNames == null || serviceParameterNames.isEmpty()) {
                return invokeDelegate(method, args);
            }
            GeneratedRoleIdentityContextBinder.attach(request);
            Object[] businessArgs = businessArguments(request);
            List<ResponseWrite> responseWrites = new ArrayList<>();
            for (HydrationRule declaration : rules) {
                HydrationRule rule = effectiveReadRule(declaration, businessArgs);
                if (participantTarget(rule)) { hydrateParticipant(rule, businessArgs); continue; }
                Method responseWriter = null;
                boolean collectionQuery = false;
                if (rule.responseMethodName() != null) {
                    Optional<Method> writer = findResponseWriterByName(rule.responseMethodName());
                    if (writer.isPresent()) {
                        responseWriter = writer.orElseThrow();
                        collectionQuery = isCollectionWriter(responseWriter);
                    }
                }
                if (collectionQuery) {
                    List<Object> keyValues = keyValues(rule, businessArgs);
                    responseWrites.add(new ResponseWrite(rule, responseWriter, keyValues.get(0), null, true));
                } else {
                    Object hydratedArgument = hydrateArgument(rule, businessArgs);
                    responseWrites.add(new ResponseWrite(rule, responseWriter, null, hydratedArgument, false));
                }
            }
            Object response = invokeDelegate(method, args);
            for (ResponseWrite responseWrite : responseWrites) {
                if (responseWrite.rule().responseMethodName() == null) {
                    continue;
                }
                if (response == null) {
                    throw new IllegalStateException("Repository query " + responseWrite.rule().methodName()
                            + " returned null response for target " + responseWrite.rule().responseMethodName());
                }
                if (responseWrite.collectionQuery()) {
                    Method responseWriter = responseWrite.responseWriter();
                    if (responseWriter == null) {
                        responseWriter = findResponseWriter(response.getClass(),
                                responseWrite.rule().responseMethodName()).orElse(null);
                    }
                    if (responseWriter == null) {
                        throw new IllegalStateException("Cannot find response method "
                                + responseWrite.rule().responseMethodName()
                                + " on " + response.getClass().getName());
                    }
                    writeResponse(response, responseWriter,
                            queryCollection(responseWrite.rule(), responseWrite.queryKey(), responseWriter,
                                    businessArgs));
                } else {
                    writeResponse(response, responseWrite.rule().responseMethodName(),
                            responseWrite.hydratedArgument());
                }
            }
            return response;
        }

        private Object invokeDelegate(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Object[] businessArguments(Object request) {
            Object[] values = new Object[serviceParameterNames.size()];
            for (int i = 0; i < serviceParameterNames.size(); i++) {
                values[i] = readProperty(request, serviceParameterNames.get(i));
            }
            return values;
        }

        private Optional<Method> findResponseWriterByName(String methodName) {
            String responseTypeName = delegate.getClass().getPackageName().replace(".service", ".model")
                    + "." + capitalize(delegate.getName()) + "Response";
            try {
                return findResponseWriter(Class.forName(responseTypeName), methodName);
            } catch (ClassNotFoundException ignored) {
                return Optional.empty();
            }
        }

        private static Object lastArgument(Object[] args) {
            if (args == null || args.length == 0) {
                return null;
            }
            Object value = args[args.length - 1];
            if (value instanceof Object[] nestedArgs && nestedArgs.length > 0) {
                return nestedArgs[nestedArgs.length - 1];
            }
            return value;
        }

        private static String capitalize(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }

        private record ResponseWrite(HydrationRule rule, Method responseWriter, Object queryKey,
                                     Object hydratedArgument, boolean collectionQuery) {
        }

        private HydrationRule ruleFor(Method method) {
            for (HydrationRule rule : rules) {
                if (rule.methodName().equals(method.getName())) {
                    return rule;
                }
            }
            return null;
        }

        private HydrationRule effectiveReadRule(HydrationRule rule, Object[] args) {
            if (rule == null || rule.readLocked() || rule.ordinaryRead() == null) return rule;
            Object target = null;
            if (participantTarget(rule)) {
                String[] path = rule.responseMethodName().substring("@participant:".length()).split(":", 2);
                target = argument(args, Integer.parseInt(path[0]));
                if (path.length == 2 && !path[1].isEmpty()) target = ObjectPropertyAccess.read(target, path[1]);
            }
            return TransactionQueryReads.protects(target, rule.ordinaryRead()) ? rule.forUpdate() : rule;
        }

        private boolean participantTarget(HydrationRule rule) {
            return rule != null && rule.responseMethodName() != null && rule.responseMethodName().startsWith("@participant:");
        }
        private void hydrateParticipant(HydrationRule rule, Object[] args) {
            String[] path = rule.responseMethodName().substring("@participant:".length()).split(":",2);
            Object root = argument(args,Integer.parseInt(path[0]));
            if (root == null) throw new IllegalStateException("PARTICIPANT_INSTANCE_REQUIRED");
            String member = path.length==1 ? "" : path[1];
            Object owner = root, target = root;
            Method writer = null;
            if (!member.isEmpty()) {
                int separator=member.lastIndexOf('.');
                if (separator>=0) owner=ObjectPropertyAccess.read(root,member.substring(0,separator));
                if (owner==null) throw new IllegalStateException("QUERY_PARTICIPANT_OWNER_REQUIRED");
                String property=member.substring(separator+1);
                writer=findResponseWriter(owner.getClass(),property).orElseThrow(() -> new IllegalStateException("QUERY_PARTICIPANT_TARGET_UNWRITABLE"));
                target=ObjectPropertyAccess.read(owner,property);
            }
            if (member.isEmpty() && target instanceof List<?> collection && rule.objectIdPath() != null
                    && rule.keyTargets().stream().allMatch(key -> key.argumentIndex() == Integer.parseInt(path[0]))) {
                int index = Integer.parseInt(path[0]);
                List<Object> ordered = new java.util.ArrayList<>();
                for (Object item : collection) {
                    Object[] rowArgs = args.clone(); rowArgs[index] = item;
                    if (missingDefaultKey(rule,rowArgs)) com.servpp.service.runtime.ParticipantIdentity.confirm(item,false);
                    else ordered.add(item);
                }
                ordered.sort(java.util.Comparator.comparing(item -> {
                    Object[] rowArgs = args.clone(); rowArgs[index] = item;
                    return hydrationObjectId(rule, rowArgs);
                }));
                for (Object item : ordered) {
                    Object[] rowArgs = args.clone(); rowArgs[index] = item;
                    hydrateParticipant(rule, rowArgs);
                }
                com.servpp.service.runtime.ParticipantIdentity.confirm(collection,
                        !collection.isEmpty() && collection.stream().allMatch(com.servpp.service.runtime.ParticipantIdentity::isIdentified));
                return;
            }
            if (missingDefaultKey(rule,args)) {
                com.servpp.service.runtime.ParticipantIdentity.confirm(target,false);
                return;
            }
            requireAllKeyValues(rule,args);
            if (member.isEmpty() && target instanceof List<?> collection) {
                if (rule.optional()) throw new IllegalStateException("REPOSITORY_SCALAR_QUERY_REQUIRED");
                var values = queryCollectionValues(rule, keyValues(rule,args).get(0), args,
                        entranceCollectionElementType(rule, Integer.parseInt(path[0])));
                com.servpp.service.runtime.ParticipantFacts.hydrateCollection(collection, values);
                com.servpp.service.runtime.ParticipantIdentity.confirmFacts(collection);
                collection.forEach(RepositoryParticipantSnapshots::remember);
                return;
            }
            if(writer!=null && isCollectionWriter(writer)) {
                                var values=queryCollectionValues(rule,keyValues(rule,args).get(0),args,collectionElementType(writer));
                if(List.class.isAssignableFrom(writer.getParameterTypes()[0])) {
                    if(target==null) { target=new java.util.ArrayList<>(); writeResponse(owner,writer,target); }
                    com.servpp.service.runtime.ParticipantFacts.hydrateCollection((List<?>)target,values);
                } else {
                    writeResponse(owner,writer,queryCollection(rule,keyValues(rule,args).get(0),writer,args));
                }
                com.servpp.service.runtime.ParticipantIdentity.confirm(owner,true);
                com.servpp.service.runtime.ParticipantIdentity.confirmFacts(target);
                if (target instanceof Iterable<?> items) items.forEach(RepositoryParticipantSnapshots::remember);
                else if (target != null && target.getClass().isArray())
                    for (int i = 0; i < Array.getLength(target); i++) RepositoryParticipantSnapshots.remember(Array.get(target, i));
                return;
            }
            if(target==null) throw new IllegalStateException("QUERY_PARTICIPANT_INSTANCE_REQUIRED");
            String id=hydrationObjectId(rule,args);
            Optional<?> found;
            boolean predicate=!rule.appDdlInferred() && !usesRoleParticipantIdentity(rule) && !"@singleton".equals(rule.objectIdPath());
            if(predicate) {
                // Explicit predicates are filters, even when their value happens to equal another row's ID.
                var matches=queryCollectionValues(rule,keyValues(rule,args).get(0),args,target.getClass());
                if(matches.size()>1) throw new IllegalStateException("REPOSITORY_SCALAR_QUERY_AMBIGUOUS");
                found=matches.isEmpty()?Optional.empty():Optional.of(matches.get(0));
            } else found=rule.readLocked() ? repositorySupplier.get().findForUpdate(rule.objectType(),id,target.getClass())
                    : repositorySupplier.get().find(rule.objectType(),id,target.getClass());
            com.servpp.service.runtime.ParticipantIdentity.confirm(target,found.isPresent());
            if(found.isEmpty() && predicate)repositorySupplier.get().prepareUnidentifiedParticipant(rule.objectType(),target);
            if(found.isPresent()) {
                com.servpp.service.runtime.ParticipantFacts.hydrate(target,found.get());
                RepositoryParticipantSnapshots.transfer(found.get(), target);
                com.servpp.service.runtime.ParticipantIdentity.confirmFacts(target);
                String readId=RepositoryParticipantSnapshots.objectIdentity(found.get(),rule.objectType()).orElse(id);
                RepositoryParticipantSnapshots.remember(target,repositorySupplier.get(),rule.objectType(),readId);
            }
        }

        private Class<?> entranceCollectionElementType(HydrationRule rule, int index) {
            List<Method> methods = new java.util.ArrayList<>();
            for (Class<?> type : collectInterfaces(delegate.getClass())) methods.addAll(java.util.Arrays.asList(type.getMethods()));
            methods.addAll(java.util.Arrays.asList(delegate.getClass().getMethods()));
            for (Method method : methods) {
                if (!method.getName().equals(rule.methodName()) || method.getParameterCount() <= index) continue;
                var type = method.getGenericParameterTypes()[index];
                if (type instanceof java.lang.reflect.ParameterizedType parameterized
                        && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) return element;
            }
            throw new IllegalStateException("QUERY_COLLECTION_ELEMENT_TYPE_REQUIRED");
        }

        private Object hydrateArgument(HydrationRule rule, Object[] args) {
            Object value = argument(args, rule.argumentIndex());
            if (missingDefaultKey(rule,args)) return null;
            requireAllKeyValues(rule, args);
            String objectId = hydrationObjectId(rule, args);
            try {
                if (rule.readLocked()) {
                    Optional<?> found = repositorySupplier.get().findForUpdate(rule.objectType(), objectId, value.getClass());
                    if (found.isEmpty() && !rule.optional()) {
                        throw new IllegalStateException("REPOSITORY_LOCKED_OBJECT_NOT_FOUND");
                    }
                    return found.orElse(null);
                }
                if (rule.optional()) {
                    if (repositorySupplier.get().hydrateIfPresent(rule.objectType(),objectId,value)) { return value; }
                    // An explicit scalar query can select one member of a composite stored key.
                    // Role/DDL identities continue to use their declared complete object identity.
                    if (rule.appDdlInferred() || rule.responseMethodName() == null || usesRoleParticipantIdentity(rule)) { return null; }
                    List<?> matches = queryCollectionValues(rule, keyValues(rule, args).get(0), args, value.getClass());
                    if (matches.size() > 1) { throw new IllegalStateException("REPOSITORY_SCALAR_QUERY_AMBIGUOUS"); }
                    return matches.isEmpty() ? null : matches.get(0);
                }
                repositorySupplier.get().hydrate(rule.objectType(), objectId, value);
            } catch (IllegalStateException e) {
                throw new IllegalStateException("Repository query " + rule.methodName()
                        + " cannot hydrate " + rule.objectType() + ":" + objectId, e);
            }
            return value;
        }

        private String hydrationObjectId(HydrationRule rule, Object[] args) {
            List<Object> resolvedKeyValues = keyValues(rule, args);
            if (rule.keyTargets().size() == 1) {
                return resolvedKeyValues.get(0).toString();
            }
            return CompositeObjectId.encode(resolvedKeyValues);
        }

        private void requireAllKeyValues(HydrationRule rule, Object[] args) {
            keyValues(rule, args);
        }

        private boolean missingDefaultKey(HydrationRule rule, Object[] args) {
            if (!rule.appDdlInferred() || roleIdentity(rule,args).isPresent()) return false;
            for (var key : rule.keyTargets()) {
                if ("@singleton".equals(key.objectIdPath())) continue;
                Object value = "@storageIdentity".equals(key.objectIdPath())
                        ? RepositoryParticipantSnapshots.objectIdentity(argument(args,key.argumentIndex()),key.objectType()).orElse(null)
                        : readPath(argument(args,key.argumentIndex()),key.objectIdPath());
                if (value == null || value.toString().isBlank()) return true;
            }
            return false;
        }

        private List<Object> keyValues(HydrationRule rule, Object[] args) {
            Optional<RoleIdentity> roleIdentity = roleIdentity(rule, args);
            if (roleIdentity.isPresent()) {
                return roleIdentity.orElseThrow().keyValues();
            }
            List<Object> values = new ArrayList<>();
            for (HydrationRule.KeyTarget target : rule.keyTargets()) {
                values.add("@storageIdentity".equals(target.objectIdPath())
                        ? RepositoryParticipantSnapshots.objectIdentity(argument(args,target.argumentIndex()),target.objectType())
                            .orElseThrow(() -> new IllegalStateException("PERSISTENCE_READ_IDENTITY_REQUIRED"))
                        : readRequiredValue(argument(args, target.argumentIndex()), target.objectIdPath()));
            }
            return List.copyOf(values);
        }

        private Optional<RoleIdentity> roleIdentity(HydrationRule rule, Object[] args) {
            if (!usesRoleParticipantIdentity(rule)) {
                return Optional.empty();
            }
            return roleIdentityResolver.resolve(rule.objectType(), args)
                    .filter(identity -> identity.complete()
                            && identity.keyValues().size() == rule.keyTargets().size());
        }

        private boolean usesRoleParticipantIdentity(HydrationRule rule) {
            String sourceHint = rule.sourceHint();
            return "APP_DDL_ROLE_TAKE".equals(sourceHint)
                    || "APP_DDL_ROLE_TAKE_PRIMARY_KEY".equals(sourceHint);
        }

        private static Object argument(Object[] args, int index) {
            if (args == null || args.length <= index || args[index] == null) {
                throw new IllegalStateException("Argument " + index + " is required for repository query");
            }
            return args[index];
        }

        private static Object readRequiredValue(Object value, String path) {
            if ("@singleton".equals(path)) return "singleton";
            Object resolved = readPath(value, path);
            if (resolved == null || resolved.toString().isBlank()) {
                throw new IllegalStateException(path + " is required for repository query");
            }
            return resolved;
        }

        private static Object readPath(Object value, String path) {
            Object current = value;
            for (String property : path.split("\\.")) {
                if (current == null) {
                    return null;
                }
                current = "@storageIdentity".equals(property)
                        ? RepositoryParticipantSnapshots.objectIdentity(current,current.getClass().getSimpleName()).orElse(null)
                        : readProperty(current, property);
            }
            return current;
        }

        private static Object readProperty(Object value, String property) {
            Class<?> type = value.getClass();
            String accessorSuffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            for (String methodName : List.of("get" + accessorSuffix, "is" + accessorSuffix, property)) {
                try {
                    Method method = type.getMethod(methodName);
                    if (method.getParameterCount() == 0) {
                        return method.invoke(value);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try the next JavaBean style before falling back to field access.
                }
            }
            Class<?> current = type;
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(property);
                    field.setAccessible(true);
                    return field.get(value);
                } catch (ReflectiveOperationException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new IllegalStateException("Cannot read property " + property + " from " + type.getName());
        }

        private static void writeResponse(Object response, String methodName, Object value) {
            Optional<Method> writer = findResponseWriter(response.getClass(), methodName);
            if (writer.isPresent()) {
                writeResponse(response, writer.orElseThrow(), value);
                return;
            }
            throw new IllegalStateException("Cannot find response method " + methodName
                    + " on " + response.getClass().getName());
        }

        private static void writeResponse(Object response, Method method, Object value) {
            try {
                method.invoke(response, value);
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw new IllegalStateException("Cannot write repository query response with "
                        + method.getName(), e);
            }
        }

        private static Optional<Method> findResponseWriter(Class<?> responseType, String methodName) {
            for (Method method : responseType.getMethods()) {
                if (methodName.equals(method.getName()) && method.getParameterCount() == 1) {
                    return Optional.of(method);
                }
            }
            return Optional.empty();
        }

        private Object queryCollection(HydrationRule rule, Object keyValue, Method responseWriter, Object[] args) {
            Class<?> valueType = collectionElementType(responseWriter);
            List<?> values = queryCollectionValues(rule, keyValue, args, valueType);
            Class<?> responseType = responseWriter.getParameterTypes()[0];
            if (responseType.isArray()) {
                Object array = Array.newInstance(responseType.getComponentType(), values.size());
                for (int i = 0; i < values.size(); i++) {
                    Array.set(array, i, values.get(i));
                }
                return array;
            }
            return values;
        }

        private List<?> queryCollectionValues(HydrationRule rule, Object keyValue, Object[] args, Class<?> valueType) {
            if (rule.keyTargets().size() == 1) {
                HydrationRule.KeyTarget firstKey = rule.firstKey();
                return rule.readLocked()
                        ? repositorySupplier.get().findByForUpdate(rule.objectType(), List.of(firstKey.repositoryObjectIdPath()), List.of(keyValue), valueType)
                        : repositorySupplier.get().findBy(rule.objectType(), firstKey.repositoryObjectIdPath(), keyValue, valueType);
            }
            List<String> repositoryPaths = new ArrayList<>();
            List<Object> keyValues = new ArrayList<>();
            for (int i = 0; i < rule.keyTargets().size(); i++) {
                HydrationRule.KeyTarget target = rule.keyTargets().get(i);
                repositoryPaths.add(target.repositoryObjectIdPath());
                keyValues.add(i == 0 ? keyValue : keyValues(rule, args).get(i));
            }
            return rule.readLocked() ? repositorySupplier.get().findByForUpdate(rule.objectType(), repositoryPaths, keyValues, valueType)
                    : repositorySupplier.get().findBy(rule.objectType(), repositoryPaths, keyValues, valueType);
        }

        private static boolean isCollectionWriter(Method responseWriter) {
            Class<?> responseType = responseWriter.getParameterTypes()[0];
            return responseType.isArray() || List.class.isAssignableFrom(responseType);
        }

        private static Class<?> collectionElementType(Method responseWriter) {
            Class<?> responseType = responseWriter.getParameterTypes()[0];
            if (responseType.isArray()) {
                return responseType.getComponentType();
            }
            Type genericType = responseWriter.getGenericParameterTypes()[0];
            if (genericType instanceof ParameterizedType parameterizedType) {
                Type argument = parameterizedType.getActualTypeArguments()[0];
                if (argument instanceof Class<?> elementType) {
                    return elementType;
                }
            }
            throw new IllegalStateException("Repository query response method " + responseWriter.getName()
                    + " must declare a concrete List element type.");
        }

        private Object invokeObject(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> delegate.toString();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new IllegalStateException("Unsupported Object method " + method.getName());
            };
        }
    }
}
