package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.app.AppHeader;
import com.servpp.lang.models.app.ScopeItem;
import com.servpp.lang.models.app.SppExtendField;
import com.servpp.lang.models.app.expr.IAssignOperation;
import com.servpp.lang.models.app.expr.IExpression;
import com.servpp.lang.models.app.expr.IJavaStatement;
import com.servpp.lang.models.app.expr.IOperationExpression;
import com.servpp.lang.models.app.expr.Literal;
import com.servpp.lang.models.app.expr.Reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author lidong@date 2023-11-02@version 1.0
 */
public class SppService extends SppClass{
    private boolean systemIdempotencyEnabled = true;
    private boolean systemDuplicateGuardEnabled = true;
    private boolean systemPartialUpdateEnabled = true;
    public void setSystemPartialUpdateEnabled(boolean enabled) { systemPartialUpdateEnabled = enabled; }
    public void setSystemRequestServices(boolean idempotency, boolean duplicateGuard) {
        systemIdempotencyEnabled = idempotency;
        systemDuplicateGuardEnabled = duplicateGuard;
    }
    private List<com.servpp.lang.models.app.TechnicalServices.Binding> technicalBindings = List.of();
    public void setTechnicalBindings(List<com.servpp.lang.models.app.TechnicalServices.Binding> bindings) { technicalBindings = bindings; }
    public List<com.servpp.lang.models.app.TechnicalServices.Binding> getTechnicalBindings() { return technicalBindings; }
    private boolean implicitParticipantCrud;
    public boolean isImplicitParticipantCrud() { return implicitParticipantCrud; }
    public void setImplicitParticipantCrud(boolean value) { implicitParticipantCrud = value; }
    private SppClass implicitCrudTarget;
    public SppClass getImplicitCrudTarget() { return implicitCrudTarget; }
    public void setImplicitCrudTarget(SppClass value) { implicitCrudTarget = value; }
    private Map<String, SppConstant> businessConstants = Map.of();
    public void setBusinessConstants(Map<String, SppConstant> constants) { businessConstants = constants; }
    public SppConstant getBusinessConstant(String name) { return businessConstants.get(name); }
    private boolean domainQualifiedDeployment;
    public void setDomainQualifiedDeployment(boolean qualified) { domainQualifiedDeployment = qualified; }
    public String getDeploymentServiceName() { return domainQualifiedDeployment ? getDomainName() + "." + getFuncName() : getFuncName(); }
    private com.servpp.lang.models.app.LocalServiceBinding localServiceBinding;
    private SppService localServiceProvider;
    private String persistenceOperation;
    public String getPersistenceOperation() { return persistenceOperation; }
    public void setPersistenceOperation(String operation) { persistenceOperation = operation; }
    public boolean hasLocalServiceBinding() { return localServiceBinding != null; }
    public void setLocalServiceProvider(SppService provider) { localServiceProvider = provider; }
    public void setLocalServiceBinding(com.servpp.lang.models.app.LocalServiceBinding binding) { localServiceBinding = binding; }
    public String getLocalDelegateMetadataInterface() {
        return localServiceBinding == null ? "" : ", com.servpp.service.runtime.LocalServiceDelegateProvider";
    }
    public String getLocalDelegateMetadataMethod() {
        return localServiceBinding == null ? "" : "    @Override public String getLocalDelegateServiceName() { return \""
                + localServiceBinding.getTarget() + "\"; }";
    }
    public String getLocalServiceBindingStatement() {
        if (localServiceBinding == null) return "";
        if (localServiceBinding.isRoleBinding()) return getParticipantRoleBindingStatement();
        var participants = getServiceBody().getSppLocalVarList();
        String arguments = participants.stream().map(SppLocalVar::getName).collect(java.util.stream.Collectors.joining(", "));
        String facts = java.util.stream.IntStream.range(0, participants.size()).mapToObj(index ->
                "new String[]{" + localServiceBinding.getParticipantFacts().get(index).stream()
                    .map(name -> "\"" + name + "\"").collect(java.util.stream.Collectors.joining(",")) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String identities = java.util.stream.IntStream.range(0, participants.size()).mapToObj(index -> "new String[]{"
                + localServiceBinding.getParticipantIdentities().getOrDefault(index, List.of()).stream()
                .map(name -> "\"" + name + "\"").collect(java.util.stream.Collectors.joining(",")) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String existing = java.util.stream.IntStream.range(0, participants.size()).mapToObj(index -> Boolean.toString(localServiceBinding.getExistingCollections().contains(index)))
                .collect(java.util.stream.Collectors.joining(","));
        return "        com.servpp.service.runtime.LocalDomainServices.invoke(\"" + localServiceBinding.getTarget()
                + "\", new Object[]{" + arguments + "}, new Class<?>[]{"
                + participants.stream().map(participant -> participant.getType().getName() + ".class").collect(java.util.stream.Collectors.joining(","))
                + "}, new String[][]{" + facts + "}, new String[][]{" + identities + "}, new boolean[]{" + existing + "}, "
                + (getServiceType() == IConstance.ServiceType.query) + ");";
    }
    private String getParticipantRoleBindingStatement() {
        var participants = serviceBody.getSppLocalVarList();
        String model = getBasePackage() + "." + getDomainName() + ".model.";
        var adapters = java.util.stream.IntStream.range(0, participants.size()).mapToObj(index -> {
            var role = localServiceBinding.getParticipantRoles().get(index);
            if (role == null) throw new IllegalStateException("Missing participant role binding " + getFuncName());
            var keys = localServiceBinding.getParticipantIdentities().getOrDefault(index, List.of());
            var facts = localServiceBinding.getParticipantFacts().get(index);
            String viewClass = role.performerView() ? getBasePackage() + "." + role.mapperDomain() + ".model." + role.view().getName() : model + role.view().getName();
            String mapperClass = role.mapper() == null ? null : role.performerView()
                    ? getBasePackage() + "." + role.mapperDomain() + ".model." + role.mapper().getName() : model + role.mapper().getName();
            return "new com.servpp.service.runtime.RoleBoundArguments.Adapter(" + viewClass + ".class, "
                    + (role.mapper() == null ? "null" : "new " + mapperClass + "()") + ", "
                    + (role.performerName() == null ? "null" : "\"" + role.performerName() + "\"")
                    + ", new String[]{" + facts.stream().map(f -> "\"" + f + "\"").collect(Collectors.joining(",")) + "}"
                    + ", new String[]{" + keys.stream().map(f -> "\"" + f + "\"").collect(Collectors.joining(",")) + "}, "
                    + localServiceBinding.getExistingCollections().contains(index) + ", " + role.performerView() + ")";
        }).collect(Collectors.joining(","));
        return "        com.servpp.service.runtime.RoleBoundArguments.invoke(\"" + localServiceBinding.getTarget()
                + "\", new Object[]{" + participants.stream().map(SppLocalVar::getName).collect(Collectors.joining(","))
                + "}, new Class<?>[]{" + participants.stream().map(p -> p.getType().getName() + ".class").collect(Collectors.joining(","))
                + "}, new com.servpp.service.runtime.RoleBoundArguments.Adapter[]{" + adapters + "}, "
                + (getServiceType() == IConstance.ServiceType.query) + ");";
    }
    private boolean queryFromPerformers;
    public boolean isQueryFromPerformers() { return queryFromPerformers; }
    public void setQueryFromPerformers(boolean value) { queryFromPerformers = value; }
    public record RoleHydration(int participantIndex, com.servpp.lang.models.app.LocalServiceBinding binding,
                                SppRoleMapper mapper, SppClass performer) { }
    private final List<RoleHydration> roleHydrations = new ArrayList<>();
    public List<RoleHydration> getRoleHydrations() { return roleHydrations; }
    public record OwnedRoleWrite(int participantIndex, String operation, SppRoleMapper mapper, SppClass performer, List<String> keys) { }
    private final List<OwnedRoleWrite> ownedRoleWrites = new ArrayList<>();
    public List<OwnedRoleWrite> getOwnedRoleWrites() { return ownedRoleWrites; }
    private String roleDefinition(SppRoleMapper mapper) {
        String model = getBasePackage() + "." + getDomainName() + ".model.";
        return "new com.servpp.service.runtime.MaterializedRolePerformers.Definition(" + model + mapper.getRole().getName()
                + ".class, java.util.List.of(" + mapper.getEntities().stream().map(entity -> model + entity.getName() + ".class")
                .collect(Collectors.joining(",")) + "), new " + model + mapper.getName() + "(), java.util.List.of("
                + mapper.getRole().getSppFieldList().stream().filter(SppField::isSnapshotStorageFact)
                    .map(field -> "\"" + field.getName() + "\"").collect(Collectors.joining(", ")) + "), " + roleAlternatives(mapper, model) + ")";
    }
    private String roleAlternatives(SppRoleMapper mapper, String model) {
        return "java.util.List.of(" + mapper.getAlternatives().stream().map(actors -> "java.util.List.of("
                + actors.stream().map(actor -> model + actor.getName() + ".class").collect(Collectors.joining(","))
                + ")").collect(Collectors.joining(",")) + ")";
    }
    public String getRoleHydrationStatement() {
        String model = getBasePackage() + "." + getDomainName() + ".model.";
        String begin = queryFromPerformers ? "        com.servpp.service.runtime.ParticipantRoleQueries.begin(" + serviceBody.getLocalVar(0).getName() + ");\n" : "";
        return begin + roleHydrations.stream().map(hydration -> {
            var parameter = serviceBody.getLocalVar(hydration.participantIndex());
            var facts = hydration.binding().getParticipantFacts().get(0).stream()
                    .map(fact -> "\"" + fact + "\"").collect(Collectors.joining(","));
            return "        com.servpp.service.runtime.ParticipantRoleQueries.complete(" + parameter.getName()
                    + ", new com.servpp.service.runtime.ParticipantRoleQueries.Step(\"" + hydration.binding().getTarget()
                    + "\", " + model + hydration.performer().getName() + ".class, new String[]{" + facts + "}, "
                    + roleDefinition(hydration.mapper()) + "));";
        }).collect(Collectors.joining("\n"));
    }
    private String ownedRolePersistenceStatement() {
        String model = getBasePackage() + "." + getDomainName() + ".model.";
        return ownedRoleWrites.stream().map(write ->
                "        com.servpp.service.runtime.ParticipantRolePersistence.persist(" + serviceBody.getLocalVar(write.participantIndex()).getName()
                        + ", this.$sppRepositorySupplier, com.servpp.service.repository.RepositoryWriteRule.Operation." + write.operation().toUpperCase()
                        + ", new com.servpp.service.runtime.ParticipantRolePersistence.Step(" + model + write.performer().getName()
                        + ".class, java.util.List.of(" + write.keys().stream().map(key -> "\"" + key + "\"").collect(Collectors.joining(","))
                        + "), " + roleDefinition(write.mapper()) + "));" ).collect(Collectors.joining("\n"));
    }
    protected IConstance.ServiceType serviceType;
    protected String funcName;

    protected ServiceBody serviceBody;

    private ScopeItem scopeItem;

    private List<IOperationExpression> expressions;
    private List<IJavaStatement> javaStatements;
    private boolean numericGeneration;
    private boolean businessImplementation;
    public boolean hasBusinessImplementation() { return businessImplementation; }
    private List<IJavaStatement> declaredBusinessStatements;
    public List<IJavaStatement> getDeclaredJavaStatements() { return declaredBusinessStatements == null ? javaStatements : declaredBusinessStatements; }
    public void setBusinessImplementation(List<IJavaStatement> statements) {
        businessImplementation = true;
        numericGeneration = true;
        javaStatements = statements;
        declaredBusinessStatements = statements;
        expressions = new java.util.ArrayList<>();
        collectBusinessAssignments(statements);
    }
    private void collectBusinessAssignments(List<IJavaStatement> statements) {
        for (IJavaStatement statement : statements) {
            if (statement instanceof IOperationExpression expression) { expressions.add(expression); }
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationControl control) {
                collectBusinessAssignments(control.children());
            }
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationRule rule) {
                collectBusinessAssignments(rule.getWhenTrue()); collectBusinessAssignments(rule.getWhenFalse());
            }
        }
    }
    public void setNumericGeneration(boolean value) { numericGeneration = value; }


    private String deploymentTransactionMode;
    public String getDeploymentTransactionMode() { return deploymentTransactionMode; }
    public void setDeploymentTransactionMode(String mode) { deploymentTransactionMode = mode; }
    private boolean quantumService;
    private boolean declaredQuantumService;
    private List<QuantumServiceAttribute> quantumServiceAttributes = new ArrayList<>();

    private String requestSuffix = "Request";
    private String responseSuffix = "Response";

    private ServiceBody returns = new ServiceBody(this);

    private SppCompilationUnit defaultClass;

    public SppCompilationUnit getDefaultClass() {
        return defaultClass;
    }

    public void setDefaultClass(SppCompilationUnit defaultClass) {
        this.defaultClass = defaultClass;
    }

    public String getReturnType(){
        String ret = "void";
        List<SppLocalVar> sppLocalVarList = returns.getSppLocalVarList();
        if(sppLocalVarList.size() == 1){
            SppLocalVar sppLocalVar = sppLocalVarList.get(0);
            ret = sppLocalVar.getJavaType();
            if(sppLocalVar.getArrayDimension() != 0){
                ret = "List<"+ret+">";
            }
        }else if(sppLocalVarList.size() > 1){
            ret = "List";
        }
        return ret;
    }
    public SppService(String name) {
        super(name);
        this.serviceBody = new ServiceBody(this);
    }
    public SppService(String name, IConstance.CompilationUnitType type) {
        super(name,type);
        this.serviceBody = new ServiceBody(this);
    }

    public ServiceBody getReturns() {
        return returns;
    }

    public String getRequestSuffix() {
        return requestSuffix;
    }

    public void setRequestSuffix(String requestSuffix) {
        this.requestSuffix = requestSuffix;
    }

    public String getResponseSuffix() {
        return responseSuffix;
    }

    public void setResponseSuffix(String responseSuffix) {
        this.responseSuffix = responseSuffix;
    }

    public List<IOperationExpression> getExpressions() {
        return expressions;
    }

    public List<IJavaStatement> getJavaStatements() { return getPhysicalJavaStatements(); }

    public List<IJavaStatement> getPhysicalJavaStatements() {
        List<IJavaStatement> implementation = javaStatements;
        if (declaredQuantumService && javaStatements == declaredBusinessStatements && implementation != null)
            implementation = implementation.stream().filter(statement -> !(statement instanceof com.servpp.lang.models.app.expr.RequireStatement)).toList();
        if (!numericGeneration || implementation == null) { return implementation; }
        var renderer = new com.servpp.lang.models.app.expr.NumericJavaRenderer(this);
        return implementation.stream().map(statement -> (IJavaStatement) new com.servpp.lang.models.app.expr.RenderedJavaStatement(
                renderer.statement(statement), statement.getParent())).toList();
    }

    public boolean isQuantumService() {
        return quantumService;
    }

    public String getQuantumExecutionRequirementStatement() {
        return declaredQuantumService
                ? "        com.servpp.service.transaction.SourceTransactionBoundary.requireQuantumExecution();"
                : "";
    }

    public boolean isDeclaredQuantumService() {
        return declaredQuantumService;
    }

    public void setDeclaredQuantumService(boolean declaredQuantumService) {
        this.declaredQuantumService = declaredQuantumService;
    }

    public String getServiceInterfaceName() {
        return quantumService ? "QuantumService" : "AtomicService";
    }

    public SppService getLocalServiceProvider() { return localServiceProvider; }

    public String getTransactionModeName() {
        if (localServiceProvider != null) return localServiceProvider.getTransactionModeName();
        if (serviceType == IConstance.ServiceType.update && "LOCAL".equals(deploymentTransactionMode)) return "LOCAL";
        if (!declaredQuantumService && serviceType == IConstance.ServiceType.update && "XA".equals(deploymentTransactionMode)) return "XA";
        if (scopeItem == null) {
            return quantumService ? "QUANTUM" : "AT";
        }
        return scopeItem.getTransactionModeName();
    }

    public void setQuantumService(boolean quantumService) {
        this.quantumService = quantumService;
    }

    public List<QuantumServiceAttribute> getQuantumServiceAttributes() {
        if (quantumServiceAttributes.isEmpty() && quantumService) {
            collectQuantumServiceAttributes();
        }
        return quantumServiceAttributes;
    }

    public boolean isHasQuantumServiceAttributes() {
        return !getQuantumServiceAttributes().isEmpty();
    }

    public void addQuantumServiceAttribute(QuantumServiceAttribute attribute) {
        quantumServiceAttributes.add(attribute);
    }

    public String getQuantumConservationImports() {
        if (getQuantumServiceAttributes().isEmpty()) {
            return "";
        }
        return "import java.util.List;\n"
                + "import com.servpp.service.runtime.QuantumServiceMetadataProvider;\n"
                + "import com.servpp.service.transaction.QuantumConservationParticipant;\n"
                + "import com.servpp.service.transaction.TransactionParticipant;\n";
    }

    public String getQuantumServiceMetadataInterface() {
        return (getQuantumServiceAttributes().isEmpty() ? "" : ", QuantumServiceMetadataProvider")
                + (hasQuantumConstraints() ? ", com.servpp.service.transaction.QuantumConstraintProvider" : "");
    }

    private boolean hasQuantumConstraints() {
        return declaredQuantumService && declaredBusinessStatements != null && declaredBusinessStatements.stream()
                .anyMatch(statement -> statement instanceof com.servpp.lang.models.app.expr.RequireStatement);
    }
    private String quantumConstraintMethod() {
        if (!hasQuantumConstraints()) return "";
        StringBuilder method = new StringBuilder("    @Override\n    public void validateQuantumConstraints(Object[] participants) {\n");
        int index = 0;
        for (var participant : serviceBody.getSppLocalVarList()) method.append("        ")
                .append(participant.getServiceJavaType()).append(" ").append(participant.getName())
                .append(" = (").append(participant.getServiceJavaType()).append(") participants[")
                .append(index++).append("];\n");
        var renderer = new com.servpp.lang.models.app.expr.NumericJavaRenderer(this);
        for (var constraint : declaredBusinessStatements) if (constraint instanceof com.servpp.lang.models.app.expr.RequireStatement)
            method.append("        ").append(renderer.statement(constraint)).append("\n");
        return method.append("    }\n\n").toString();
    }

    public String getQuantumServiceMetadataMethod() {
        List<QuantumServiceAttribute> attributes = getQuantumServiceAttributes();
        if (attributes.isEmpty()) {
            return quantumConstraintMethod();
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public List<String> getQuantumAttributeIds() {\n");
        method.append("        return List.of(");
        for (int i = 0; i < attributes.size(); i++) {
            if (i > 0) {
                method.append(", ");
            }
            method.append("\"").append(attributes.get(i).getAttributeId()).append("\"");
        }
        method.append(");\n");
        method.append("    }\n\n");
        method.append(quantumConstraintMethod());
        return method.toString();
    }

    public String getQuantumConservationParticipantMethod() {
        List<QuantumServiceAttribute> attributes = getQuantumServiceAttributes();
        if (attributes.isEmpty()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public TransactionParticipant getTransactionParticipant() {\n");
        method.append("        return new QuantumConservationParticipant(getTransactionResourceId())\n");
        for (QuantumServiceAttribute attribute : attributes) {
            method.append(serviceBody.getSppLocalVarList().get(attribute.getArgumentIndex()).isParticipantCollection()
                    ? "                .conserveEachArgument(" : "                .conserveArgument(")
                    .append(attribute.getArgumentIndex()).append(", \"")
                    .append(attribute.getGroupId()).append("\", \"")
                    .append(attribute.getAttributeId()).append("\", \"")
                    .append(attribute.getPropertyName()).append("\")\n");
        }
        method.append("                ;\n");
        method.append("    }\n\n");
        return method.toString();
    }

    public String getScenarioBodyJava() {
        if (serviceBody == null || serviceBody.getSppServiceCallList() == null) {
            return "";
        }
        StringBuilder java = new StringBuilder();
        if (!technicalBindings.isEmpty()) java.append("        executeWithTechnicalInterceptors(new Object[]{")
                .append(serviceBody.getSppLocalVarList().stream().map(SppLocalVar::getName).collect(Collectors.joining(", ")))
                .append("}, () -> {\n");
        if (this instanceof SppScenario scene && scene.isUsesSystemResult()) {
            String type = serviceBody.getLocalVar("ResultInfo").getType().getPackageName() + ".ResultInfo";
            java.append("        final ").append(type).append(" ResultInfo = new ").append(type).append("();\n")
                    .append("        com.servpp.service.transaction.ScenarioResults.initialize(ResultInfo);\n");
            for (var participant : serviceBody.getSppLocalVarList())
                if (participant.getArrayDimension() == 0 && participant.getType().getType() == IConstance.CompilationUnitType.contract)
                    java.append("        com.servpp.service.transaction.ScenarioResults.attach(").append(participant.getName())
                            .append(", ResultInfo);\n");
        }
        scenarioBlocks.clear();
        scenarioBlockSequence = 0;
        appendScenarioBlock(java, serviceBody.getSppServiceCallList(), 8);
        if (this instanceof SppScenario scene && scene.isUsesSystemResult())
            java.append("        com.servpp.service.transaction.ScenarioResults.complete(ResultInfo);\n");
        if (!technicalBindings.isEmpty()) java.append("        return null;\n        });\n");
        return java.toString();
    }

    private final java.util.Deque<String> scenarioBlocks = new java.util.ArrayDeque<>();
    private int scenarioBlockSequence;
    private void appendScenarioBlock(StringBuilder java, List<IServiceBodyStatement> statements, int indent) {
        statements = readyQuantumFirst(statements);
        String label = "$sppBlock" + scenarioBlockSequence++;
        scenarioBlocks.push(label);
        indent(java, indent).append(label).append(": {\n");
        try {
            if (statements != null) for (int index = 0; index < statements.size();) {
                var statement = statements.get(index);
                if (statement instanceof SppServiceCall call && insideParallelTransaction(statement) && preparedOrdinaryUpdate(call)) {
                    var batch = new ArrayList<SppServiceCall>();
                    while (index < statements.size() && statements.get(index) instanceof SppServiceCall next && preparedOrdinaryUpdate(next)) {
                        batch.add(next); index++;
                    }
                    if (batch.size() > 1) {
                        indent(java, indent + 4).append("com.servpp.service.transaction.SourceTransactionUpdates.executeParallel(java.util.List.of(\n");
                        for (int member = 0; member < batch.size(); member++) {
                            if (member > 0) java.append(",\n");
                            appendExecutor(java, batch.get(member), indent + 8);
                        }
                        java.append("));\n");
                    } else appendScenarioStatement(java, batch.get(0), indent + 4);
                } else { appendScenarioStatement(java, statement, indent + 4); index++; }
            }
        }
        finally { scenarioBlocks.pop(); }
        indent(java, indent).append("}\n");
    }
    private boolean isCheckCall(SppServiceCall call) {
        return call.getIdentityTarget() != null || (call.getCallee() != null
                && call.getCallee().getServiceType() == IConstance.ServiceType.check);
    }
    private void appendCheck(StringBuilder java, SppServiceCall call, int indent) {
        String condition = call.getIdentityTarget() != null
                ? "com.servpp.service.runtime.BusinessChecks.identify(" + NameUtil.getNameCall(call.getIdentityTarget()) + ")"
                : "com.servpp.service.runtime.BusinessChecks.evaluate(() -> " + scenarioInvocation(call) + ").passed()";
        if (call.getGuardStatements() != null) {
            indent(java, indent).append("if (").append(condition).append(") {\n");
            appendScenarioBlock(java, call.getGuardStatements(), indent + 4);
            indent(java, indent).append("}\n");
        } else {
            indent(java, indent).append("if (!(").append(condition).append(")) {\n");
            if (call.isBreaksTransaction()) indent(java, indent + 4)
                    .append("com.servpp.service.transaction.SourceTransactionBoundary.rejectCurrent(com.servpp.service.runtime.BusinessChecks.lastResult().code());\n");
            indent(java, indent + 4).append("com.servpp.service.runtime.BusinessChecks.recordBlockFailure();\n");
            indent(java, indent + 4).append("break ").append(scenarioBlocks.element()).append(";\n");
            indent(java, indent).append("}\n");
        }
    }
    private boolean containsChecks(List<IServiceBodyStatement> statements) {
        if (statements == null) return false;
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call && isCheckCall(call)) return true;
            if (statement instanceof ExecuteBlock block && containsChecks(block.getExecuteCallList())) return true;
            if (statement instanceof TransactionBlock block && containsChecks(block.getTransactionCallList())) return true;
            if (statement instanceof ConditionalBlock block && (containsChecks(block.getThenStatements()) || containsChecks(block.getElseStatements()))) return true;
        }
        return false;
    }
    private void appendRepositoryScopeServices(StringBuilder java, List<IServiceBodyStatement> statements, int indent) {
        Set<String> services=new java.util.LinkedHashSet<>();collectRepositoryScopeServices(statements,services);
        if(!services.isEmpty())indent(java,indent).append("public java.util.List<Service> getRepositoryScopeServices() { return java.util.List.of(")
                .append(String.join(",",services)).append("); }\n");
    }
    private void collectRepositoryScopeServices(List<IServiceBodyStatement> statements, Set<String> services) {
        if(statements==null)return;
        for(var statement:statements) {
            if(statement instanceof SppServiceCall call) {
                if(call.getCallee()!=null && call.getCallee().getServiceType()==IConstance.ServiceType.update)services.add(call.getCallee().getFuncName());
                collectRepositoryScopeServices(call.getGuardStatements(),services);
            } else if(statement instanceof ExecuteBlock block)collectRepositoryScopeServices(block.getExecuteCallList(),services);
            else if(statement instanceof ConditionalBlock block){collectRepositoryScopeServices(block.getThenStatements(),services);collectRepositoryScopeServices(block.getElseStatements(),services);}
            else if(statement instanceof TransactionBlock block && "REQUIRED".equals(block.getPropagation()))collectRepositoryScopeServices(block.getTransactionCallList(),services);
        }
    }
    private void appendBlockExecutor(StringBuilder java, List<IServiceBodyStatement> statements, int indent) {
        indent(java, indent).append("new IExecutor() {\n");
        indent(java, indent + 4).append("public Service getService() { return null; }\n");
        appendRepositoryScopeServices(java,statements,indent+4);
        if (statements != null && !statements.isEmpty() && insideParallelTransaction(statements.get(0))) {
            for (ServiceBaseBody parent = statements.get(0).getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof TransactionBlock block && block.getDeclaredMode() != null) {
                    indent(java, indent + 4).append("public com.servpp.service.transaction.TransactionMode getTransactionModeHint() { return com.servpp.service.transaction.TransactionMode.")
                            .append(block.getDeclaredMode()).append("; }\n");
                    break;
                }
            }
        }
        indent(java, indent + 4).append("public Object[] getArguments() { return new Object[]{ ")
                .append(serviceBody.getSppLocalVarList().stream().map(SppLocalVar::getName).collect(Collectors.joining(","))).append(" }; }\n");
        indent(java, indent + 4).append("public void execute() {\n");
        // A new executor is a Java method boundary; outer block labels cannot be crossed.
        var outer = new java.util.ArrayDeque<>(scenarioBlocks); scenarioBlocks.clear();
        try { appendScenarioBlock(java, statements, indent + 8); }
        finally { scenarioBlocks.clear(); scenarioBlocks.addAll(outer); }
        indent(java, indent + 4).append("}\n");
        indent(java, indent).append("}");
    }

    private String quantumBoundsExpression(SppServiceCall call) {
        var renderer = new com.servpp.lang.models.app.expr.NumericJavaRenderer(this);
        String intervals = call.getQuantumBounds().stream().map(bound ->
            "new com.servpp.service.transaction.QuantumInvocationBounds.Bound("
            + NameUtil.getNameCall(bound.participantPath) + ", \"" + bound.property + "\", "
            + renderer.boundEndpoint(bound.minimum, ((SppClass) bound.participant.getType()).getField(bound.property))
            + ", " + renderer.boundEndpoint(bound.maximum, ((SppClass) bound.participant.getType()).getField(bound.property))
            + ", " + renderer.boundAdapter(((SppClass) bound.participant.getType()).getField(bound.property)) + ")")
            .collect(Collectors.joining(", "));
        return "java.util.List.of(" + intervals + ")";
    }

    private String scenarioInvocation(SppServiceCall call) {
        String value=call.getCallee().getFuncName() + "." + call.getCallee().getFuncName() + "(" + joinScenarioArguments(call) + ")";
        value = transactionQueryInvocation(call, value);
        if (call.getResultTarget()!=null) {
            SppLocalVar result=call.getCallee().getReturns().getSppLocalVarList().get(0);
            value += ".get" + NameUtil.firstToLowerCase(result.getName(),false) + "()";
            value = NameUtil.getNameCall(call.getResultTarget(),true) + "(" + value + ")";
        }
        String site=getDomainName()+"."+getFuncName()+":"+call.getInvocationOrdinal();
        return "com.servpp.service.runtime.DomainServiceRouter.atCallSite(\""+site+"\", () -> { "+value+"; })";
    }

    private static boolean hasQuantumFields(SppCompilationUnit type) {
        return type instanceof SppClass participant && participant.getSppFieldList().stream().anyMatch(SppField::isQuantum);
    }

    /** Whether a query requires a database lock in its enclosing source transaction. */
    public boolean requiresLockedQuery(SppServiceCall call) {
        SppService provider=call.getCallee();
        while (provider!=null && provider.localServiceProvider!=null) provider=provider.localServiceProvider;
        if (provider==null || provider.serviceType!=IConstance.ServiceType.query || provider.scopeItem==null) return false;
        TransactionBlock boundary=null;
        for (var parent=call.getParent();parent!=null;parent=parent.getParent())
            if (parent instanceof TransactionBlock transaction) { boundary=transaction; break; }
        if (boundary==null) return false;
        Set<String> mutations=new LinkedHashSet<>();
        collectOrdinarySceneMutations(boundary.getTransactionCallList(),mutations);
        for (var rule : provider.scopeItem.getRepositoryHydrationRules()) {
            String[] target=rule.responseMethodName().split("\\.",2);
            var parameters=provider.serviceBody.getSppLocalVarList();
            for (int index=0;index<parameters.size() && index<call.getSppParameterList().size();index++) {
                if (!parameters.get(index).getName().equals(target[0])) continue;
                String path=call.getSppParameterList().get(index).getName()+(target.length==2?"."+target[1]:"");
                var participant=serviceBody.getQualifieField(path);
                if (participant==null || !hasQuantumFields(participant.getType()) || mutations.stream().anyMatch(changed ->
                        changed.equals(path) || changed.startsWith(path+".") || path.startsWith(changed+"."))) return true;
            }
        }
        return false;
    }
    private String transactionQueryInvocation(SppServiceCall call, String invocation) {
        SppService provider = call.getCallee();
        while (provider != null && provider.localServiceProvider != null) provider = provider.localServiceProvider;
        if (provider == null || provider.serviceType != IConstance.ServiceType.query || provider.scopeItem == null) return invocation;
        TransactionBlock transaction = null;
        for (var parent = call.getParent(); parent != null; parent = parent.getParent())
            if (parent instanceof TransactionBlock boundary) { transaction = boundary; break; }
        if (transaction == null) return invocation;
        Set<String> mutations = new LinkedHashSet<>();
        collectOrdinarySceneMutations(transaction.getTransactionCallList(), mutations);
        List<String> participants = new ArrayList<>(), policies = new ArrayList<>();
        for (var rule : provider.scopeItem.getRepositoryHydrationRules()) {
            String[] target = rule.responseMethodName().split("\\.", 2);
            var parameters = provider.serviceBody.getSppLocalVarList();
            for (int index = 0; index < parameters.size(); index++) {
                if (!parameters.get(index).getName().equals(target[0]) || index >= call.getSppParameterList().size()) continue;
                String path = call.getSppParameterList().get(index).getName() + (target.length == 2 ? "." + target[1] : "");
                var participant = serviceBody.getQualifieField(path);
                if (participant == null || !hasQuantumFields(participant.getType())) continue;
                participants.add(NameUtil.getNameCall(path));
                policies.add(Boolean.toString(mutations.stream().anyMatch(changed -> changed.equals(path)
                        || changed.startsWith(path + ".") || path.startsWith(changed + "."))));
            }
        }
        if (participants.isEmpty()) return invocation;
        return "com.servpp.service.repository.TransactionQueryReads.execute(new Object[]{" + String.join(",", participants)
                + "}, new boolean[]{" + String.join(",", policies) + "}, () -> " + invocation + ")";
    }

    private void collectOrdinarySceneMutations(List<IServiceBodyStatement> statements, Set<String> paths) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call) {
                if (call.getCallee() != null) mapOrdinaryMutations(call.getCallee(),
                        call.getSppParameterList().stream().map(parameter -> parameter.getName()).toList(), paths);
                collectOrdinarySceneMutations(call.getGuardStatements(), paths);
            } else if (statement instanceof ConditionalBlock branch) {
                collectOrdinarySceneMutations(branch.getThenStatements(), paths);
                collectOrdinarySceneMutations(branch.getElseStatements(), paths);
            } else if (statement instanceof ExecuteBlock block) collectOrdinarySceneMutations(block.getExecuteCallList(), paths);
            else if (statement instanceof TransactionBlock block && !"REQUIRES_NEW".equals(block.getPropagation()))
                collectOrdinarySceneMutations(block.getTransactionCallList(), paths);
        }
    }

    private static void mapOrdinaryMutations(SppService service, List<String> arguments, Set<String> paths) {
        mapOrdinaryMutations(service, arguments, paths, new java.util.HashSet<>());
    }

    private static void mapOrdinaryMutations(SppService service, List<String> arguments, Set<String> paths, Set<SppService> visiting) {
        var parameters = service.serviceBody.getSppLocalVarList();
        for (String mutation : service.ordinaryMutationPaths(visiting))
            for (int index = 0; index < parameters.size() && index < arguments.size(); index++) {
                String name = parameters.get(index).getName();
                if (mutation.equals(name) || mutation.startsWith(name + "."))
                    paths.add(arguments.get(index) + mutation.substring(name.length()));
            }
    }

    private Set<String> ordinaryMutationPaths(Set<SppService> visiting) {
        Set<String> paths = new LinkedHashSet<>();
        if (!visiting.add(this)) return paths;
        if (localServiceProvider != null) {
            mapOrdinaryMutations(localServiceProvider, serviceBody.getSppLocalVarList().stream().map(SppLocalVar::getName).toList(), paths, visiting);
        } else if (serviceType == IConstance.ServiceType.calculate || serviceType == IConstance.ServiceType.update) {
            if (expressions != null) for (var expression : expressions) {
                if (!(expression.getOperation() instanceof IAssignOperation) || !(expression.getLeft() instanceof Reference reference)) continue;
                SppLocalVar field = reference instanceof com.servpp.lang.models.app.expr.CalculationReference resolved
                        ? resolved.field : serviceBody.getQualifieField(reference.getValue());
                if (field == null || field.isQuantum()) continue;
                String path = reference.getValue();
                int separator = path.lastIndexOf('.');
                if (separator < 0) continue;
                if (reference instanceof com.servpp.lang.models.app.expr.CalculationReference resolved && resolved.local) {
                    if (resolved.root != null) for (var parameter : serviceBody.getSppLocalVarList())
                        if (parameter.getType().equals(resolved.root.getType())) paths.add(parameter.getName());
                } else paths.add(path.substring(0, separator));
            }
            collectOrdinaryCalculationCalls(getDeclaredJavaStatements(), paths, visiting);
        }
        visiting.remove(this);
        return paths;
    }

    private static void collectOrdinaryCalculationCalls(List<IJavaStatement> statements, Set<String> paths, Set<SppService> visiting) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationServiceCall call && call.callee != null)
                mapOrdinaryMutations(call.callee, call.resolvedArguments.stream().map(Reference::getValue).toList(), paths, visiting);
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationControl control)
                collectOrdinaryCalculationCalls(control.children(), paths, visiting);
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationRule branch) {
                collectOrdinaryCalculationCalls(branch.getWhenTrue(), paths, visiting); collectOrdinaryCalculationCalls(branch.getWhenFalse(), paths, visiting);
            }
        }
    }

    private int invocationSequence;
    public int allocateInvocationOrdinal() { return invocationSequence++; }

    private final Map<String,String> caughtScenarioFailures=new LinkedHashMap<>();
    private int caughtScenarioSequence;
    private String failureVariable(String prefix) {
        String name;
        do { name=prefix + caughtScenarioSequence++; } while(getLocalVar(name)!=null);
        return name;
    }
    private void appendTry(StringBuilder java,TryBlock block,int indent) {
        String original=failureVariable("$sppCaught"), secondary=failureVariable("$sppHandlerFailure");
        indent(java,indent).append("try {\n");
        for(var member:block.getTryStatements()) { appendScenarioStatement(java,member,indent+4); }
        indent(java,indent).append("} catch (RuntimeException | Error ").append(original).append(") {\n");
        indent(java,indent+4).append("try {\n");
        indent(java,indent+8).append("com.servpp.service.scenario.ScenarioFailures.describe(")
                .append(original).append(", ")
                .append(NameUtil.getNameCall(block.getFailureTarget())).append(");\n");
        caughtScenarioFailures.put(block.getFailureTarget(),original);
        try { for(var member:block.getCatchStatements()) { appendScenarioStatement(java,member,indent+8); } }
        finally { caughtScenarioFailures.remove(block.getFailureTarget()); }
        indent(java,indent+4).append("} catch (RuntimeException | Error ").append(secondary).append(") {\n");
        indent(java,indent+8).append("if (").append(secondary).append(" != ").append(original).append(") ")
                .append(original).append(".addSuppressed(").append(secondary).append(");\n");
        indent(java,indent+8).append("throw ").append(original).append(";\n");
        indent(java,indent+4).append("}\n");
        indent(java,indent).append("}\n");
    }

    private List<IServiceBodyStatement> readyQuantumFirst(List<IServiceBodyStatement> statements) {
        if (statements == null) return null;
        var result = new ArrayList<IServiceBodyStatement>();
        var pending = new ArrayList<SppServiceCall>();
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call && insideParallelTransaction(call)
                    && (preparedOrdinaryUpdate(call) || quantumUpdate(call))) pending.add(call);
            else { flushReadyCalls(pending, result); result.add(statement); }
        }
        flushReadyCalls(pending, result); return result;
    }
    private void flushReadyCalls(List<SppServiceCall> pending, List<IServiceBodyStatement> result) {
        pending.stream().filter(call -> quantumUpdate(call)).forEach(result::add);
        pending.stream().filter(call -> !quantumUpdate(call)).forEach(result::add);
        pending.clear();
    }
    private boolean quantumUpdate(SppServiceCall call) {
        var callee = call.getCallee();
        if (callee == null || callee.getServiceType() != IConstance.ServiceType.update) return false;
        while (callee.getLocalServiceProvider() != null) callee = callee.getLocalServiceProvider();
        return callee.isDeclaredQuantumService() || !callee.getQuantumServiceAttributes().isEmpty();
    }
    private boolean preparedOrdinaryUpdate(SppServiceCall call) {
        var callee = call.getCallee();
        if (callee == null || callee.getServiceType() != IConstance.ServiceType.update
                || callee.isDeclaredQuantumService() || !callee.getQuantumServiceAttributes().isEmpty()) return false;
        while (callee.getLocalServiceProvider() != null) callee = callee.getLocalServiceProvider();
        return !callee.hasBusinessImplementation() && !callee.isDeclaredQuantumService()
                && callee.getQuantumServiceAttributes().isEmpty();
    }
    private boolean insideParallelTransaction(IServiceBodyStatement statement) {
        for (ServiceBaseBody body = statement.getParent(); body != null; body = body.getParent())
            if (body instanceof TransactionBlock block) return block.isParallelOrdinaryUpdates();
        return false;
    }
    private boolean insideDeclaredTransaction(IServiceBodyStatement statement) {
        for (ServiceBaseBody body = statement.getParent(); body != null; body = body.getParent())
            if (body instanceof TransactionBlock) return true;
        return false;
    }

    private void appendScenarioStatement(StringBuilder java, IServiceBodyStatement statement, int indent) {
        if (statement instanceof TransactionFailureBlock failureBlock) {
            indent(java, indent).append("com.servpp.service.transaction.TransactionFailureHandling.execute(ResultInfo, ")
                    .append(NameUtil.getNameCall(failureBlock.getContractTarget())).append(", () -> {\n");
            appendTransactionGroup(java, failureBlock.getTransaction(), indent + 4, true);
            indent(java, indent).append("}, () -> {\n");
            appendScenarioBlock(java, failureBlock.getFailureHandler().getExecuteCallList(), indent + 4);
            indent(java, indent).append("});\n");
        } else if (statement instanceof ScenarioThrow rethrow) {
            indent(java,indent).append("throw ").append(caughtScenarioFailures.get(rethrow.getTarget())).append(";\n");
        } else if (statement instanceof TryBlock block) {
            appendTry(java,block,indent);
        } else if (statement instanceof SppServiceCall serviceCall) {
            if (isCheckCall(serviceCall)) { appendCheck(java, serviceCall, indent); return; }
            if (serviceCall.getCallee().getType() == IConstance.CompilationUnitType.atomicservice
                    && serviceCall.getCallee().getServiceType() == IConstance.ServiceType.update
                    && insideDeclaredTransaction(statement)) {
                indent(java, indent).append("com.servpp.service.transaction.SourceTransactionUpdates.execute(\n");
                appendExecutor(java, serviceCall, indent + 4);
                java.append(");\n");
            } else {
                indent(java, indent).append(scenarioInvocation(serviceCall)).append(";\n");
            }
        } else if (statement instanceof ConditionalBlock conditional) {
            appendConditional(java, conditional, indent);
        } else if (statement instanceof ExecuteBlock executeBlock) {
            appendExecuteGroup(java, executeBlock.getUpperType(), executeBlock.getExecuteCallList(), indent, true);
        } else if (statement instanceof TransactionBlock transactionBlock) {
            appendTransactionGroup(java, transactionBlock, indent, true);
        }
    }

    private void appendExecutor(StringBuilder java, IServiceBodyStatement statement, int indent) {
        if (statement instanceof TransactionFailureBlock || statement instanceof TryBlock || statement instanceof ScenarioThrow) {
            indent(java,indent).append("new IExecutor() {\n");
            indent(java,indent+4).append("public Service getService() { return null; }\n");
            indent(java,indent+4).append("public void execute() {\n");
            appendScenarioStatement(java,statement,indent+8);
            indent(java,indent+4).append("}\n");
            indent(java,indent).append("}");
        } else if (statement instanceof SppServiceCall serviceCall) {
            if (isCheckCall(serviceCall)) { appendBlockExecutor(java, List.of(serviceCall), indent); return; }
            indent(java, indent).append("new IExecutor() {\n");
            indent(java, indent + 4).append("@Override\n");
            indent(java, indent + 4).append("public Service getService() {\n");
            indent(java, indent + 8).append("return ").append(serviceCall.getCallee().getFuncName()).append(";\n");
            indent(java, indent + 4).append("}\n\n");
            indent(java, indent + 4).append("@Override\n");
            if (insideParallelTransaction(serviceCall) && serviceCall.getCallee().getServiceType() == IConstance.ServiceType.update) {
                indent(java, indent + 4).append("public com.servpp.service.transaction.TransactionMode getTransactionModeHint() { return com.servpp.service.transaction.TransactionMode.")
                        .append(serviceCall.getCallee().getTransactionModeName()).append("; }\n");
                indent(java, indent + 4).append("@Override\n");
            }
            if (preparedOrdinaryUpdate(serviceCall)) {
                indent(java, indent + 4).append("public boolean isPreparedOrdinaryUpdate() { return true; }\n");
                indent(java, indent + 4).append("public void prepareForParallelExecution() {\n");
                var callee = serviceCall.getCallee();
                if (callee.getScopeItem() != null) for (var number : callee.getScopeItem().getBusinessNumbers()) {
                    indent(java, indent + 8).append("com.servpp.service.runtime.BusinessNumbering.assign(")
                        .append(serviceCall.getSppParameterList().get(number.target().argumentIndex()).getNameCall())
                        .append(", \"").append(number.target().getObjectIdPath()).append("\", \"")
                        .append(number.prefix()).append("\");\n");
                }
                indent(java, indent + 4).append("}\n");
                indent(java, indent + 4).append("@Override\n");
            }
            if (quantumUpdate(serviceCall)) {
                indent(java, indent + 4).append("public boolean isQuantumUpdate() { return true; }\n");
                indent(java, indent + 4).append("@Override\n");
            }
            indent(java, indent + 4).append("public Object[] getArguments() {\n");
            indent(java, indent + 8).append("return new Object[]{ ")
                    .append(joinScenarioArguments(serviceCall)).append(" };\n");
            indent(java, indent + 4).append("}\n\n");
            indent(java, indent + 4).append("@Override\n");
            if (!serviceCall.getQuantumBounds().isEmpty()) {
                indent(java, indent + 4).append("public java.util.List<com.servpp.service.transaction.QuantumInvocationBounds.Bound> getQuantumBounds() {\n");
                indent(java, indent + 8).append("return ").append(quantumBoundsExpression(serviceCall)).append(";\n");
                indent(java, indent + 4).append("}\n");
                indent(java, indent + 4).append("@Override\n");
            }
            indent(java, indent + 4).append("public void execute() {\n");
            indent(java, indent + 8).append(scenarioInvocation(serviceCall)).append(";\n");
            indent(java, indent + 4).append("}\n");
            indent(java, indent).append("}");
        } else if (statement instanceof ConditionalBlock conditional) {
            indent(java, indent).append("new IExecutor() {\n");
            indent(java, indent + 4).append("public Service getService() { return null; }\n");
            appendRepositoryScopeServices(java,List.of(conditional),indent+4);
            indent(java, indent + 4).append("public void execute() {\n");
            var outer = new java.util.ArrayDeque<>(scenarioBlocks); scenarioBlocks.clear();
            try { appendScenarioBlock(java, List.of(conditional), indent + 8); }
            finally { scenarioBlocks.clear(); scenarioBlocks.addAll(outer); }
            indent(java, indent + 4).append("}\n");
            indent(java, indent).append("}");
        } else if (statement instanceof ExecuteBlock executeBlock) {
            appendExecuteGroup(java, executeBlock.getUpperType(), executeBlock.getExecuteCallList(), indent, false);
        } else if (statement instanceof TransactionBlock transactionBlock) {
            appendTransactionGroup(java, transactionBlock, indent, false);
        }
    }

    private void appendExecuteGroup(StringBuilder java, String groupType,
                                    List<IServiceBodyStatement> statements, int indent, boolean execute) {
        if ("Serial".equals(groupType)) {
            if (execute) appendScenarioBlock(java, statements, indent);
            else appendBlockExecutor(java, statements, indent);
            return;
        }
        indent(java, indent).append("new I").append(groupType).append("ExecuteGroup() {\n");
        appendGroupExecutors(java, statements, indent);
        indent(java, indent).append("}");
        if (execute) {
            java.append(".execute();");
        }
        java.append("\n");
    }

    private void appendConditional(StringBuilder java, ConditionalBlock block, int indent) {
        String condition = new com.servpp.lang.models.app.expr.NumericJavaRenderer(this)
                .condition(block.getCondition());
        indent(java, indent).append("if (").append(condition).append(") {\n");
        appendScenarioBlock(java, block.getThenStatements(), indent + 4);
        indent(java, indent).append("}");
        if (!block.getElseStatements().isEmpty()) {
            java.append(" else {\n");
            appendScenarioBlock(java, block.getElseStatements(), indent + 4);
            indent(java, indent).append("}");
        }
        java.append("\n");
    }

    private void appendTransactionGroup(StringBuilder java,
                                        TransactionBlock block, int indent, boolean execute) {
        indent(java, indent).append("new ITransactionGroup() {\n");
        if (block.isParallelOrdinaryUpdates()) indent(java, indent + 4).append("@Override public boolean parallelOrdinaryUpdates() { return true; }\n");
        if (block.isSourcePlan()) indent(java, indent + 4).append("@Override public boolean usesSourceTransactionPlan() { return true; }\n");
        if (block.getDeclaredMode() != null) {
            indent(java, indent + 4).append("@Override\n");
            indent(java, indent + 4).append("public com.servpp.service.transaction.TransactionMode getDeclaredTransactionMode() {\n");
            indent(java, indent + 8).append("return com.servpp.service.transaction.TransactionMode.")
                    .append(block.getDeclaredMode()).append(";\n");
            indent(java, indent + 4).append("}\n");
        }
        indent(java, indent + 4).append("@Override public com.servpp.service.transaction.TransactionPropagation getPropagation() { return com.servpp.service.transaction.TransactionPropagation.")
                .append(block.getPropagation()).append("; }\n");
        if (containsChecks(block.getTransactionCallList())) {
            indent(java, indent + 4).append("public IExecutor[] getServices() { return new IExecutor[]{\n");
            appendBlockExecutor(java, block.getTransactionCallList(), indent + 8);
            java.append("\n"); indent(java, indent + 4).append("}; }\n");
        } else appendGroupExecutors(java, block.getTransactionCallList(), indent);
        indent(java, indent).append("}");
        if (execute) {
            java.append(".execute();");
        }
        java.append("\n");
    }

    private void appendGroupExecutors(StringBuilder java, List<IServiceBodyStatement> statements, int indent) {
        indent(java, indent + 4).append("@Override\n");
        indent(java, indent + 4).append("public IExecutor[] getServices() {\n");
        indent(java, indent + 8).append("return new IExecutor[]{\n");
        if (statements != null) {
            for (int i = 0; i < statements.size(); i++) {
                if (i > 0) {
                    java.append(",\n");
                }
                appendExecutor(java, statements.get(i), indent + 12);
            }
            if (!statements.isEmpty()) {
                java.append("\n");
            }
        }
        indent(java, indent + 8).append("};\n");
        indent(java, indent + 4).append("}\n");
    }

    private String joinScenarioArguments(SppServiceCall serviceCall) {
        return serviceCall.getSppParameterList().stream()
                .map(SppParameter::getNameCall)
                .collect(Collectors.joining(","));
    }

    private StringBuilder indent(StringBuilder java, int spaces) {
        for (int i = 0; i < spaces; i++) {
            java.append(' ');
        }
        return java;
    }

    public String getTccParticipantImports() {
        return isTccService() && getQuantumServiceAttributes().isEmpty()
                ? "import com.servpp.service.transaction.DefaultTccTransactionParticipant;\n"
                + "import com.servpp.service.transaction.TransactionParticipant;\n"
                + getTccLifecycleRuleProviderImport()
                + getTccReservationRuleProviderImport()
                : "";
    }

    private String getTccLifecycleRuleProviderImport() {
        return hasTccLifecycleServices()
                ? "import com.servpp.service.transaction.TccLifecycleRuleProvider;\n"
                : "";
    }

    private String getTccReservationRuleProviderImport() {
        return hasTccReservationRules()
                ? "import com.servpp.service.transaction.TccReservationRule;\n"
                + "import com.servpp.service.transaction.TccReservationRuleProvider;\n"
                + "import java.util.List;\n"
                : "";
    }

    public String getTccTransactionParticipantMethod() {
        if (!isTccService() || !getQuantumServiceAttributes().isEmpty()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public TransactionParticipant getTransactionParticipant() {\n");
        method.append("        return new DefaultTccTransactionParticipant(getTransactionResourceId(), getName())");
        if (scopeItem != null && scopeItem.hasTccLifecycleServices()) {
            method.append("\n")
                    .append("                .confirmWithService(\"")
                    .append(scopeItem.getTccConfirmServiceName())
                    .append("\")\n")
                    .append("                .cancelWithService(\"")
                    .append(scopeItem.getTccCancelServiceName())
                    .append("\")");
        }
        method.append(";\n");
        method.append("    }\n\n");
        return method.toString();
    }

    public String getTccLifecycleInterface() {
        StringBuilder interfaces = new StringBuilder();
        if (hasTccLifecycleServices()) {
            interfaces.append(", TccLifecycleRuleProvider");
        }
        if (hasTccReservationRules()) {
            interfaces.append(", TccReservationRuleProvider");
        }
        return interfaces.toString();
    }

    public String getTccLifecycleRuleMethods() {
        if (!hasTccLifecycleServices()) {
            return "";
        }
        return "    @Override\n"
                + "    public String getTccConfirmServiceName() {\n"
                + "        return \"" + scopeItem.getTccConfirmServiceName() + "\";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public String getTccCancelServiceName() {\n"
                + "        return \"" + scopeItem.getTccCancelServiceName() + "\";\n"
                + "    }\n\n";
    }

    private String getTechnicalInterceptorMethods() {
        if (technicalBindings.isEmpty()) return "";
        return "    @Override\n    public com.servpp.service.technical.ServiceInterceptors getTechnicalInterceptors() {\n"
                + "        return com.servpp.service.technical.ServiceInterceptors.create(getClass().getClassLoader(), java.util.List.of("
                + technicalBindings.stream().map(com.servpp.lang.models.app.TechnicalServices::javaBinding).collect(Collectors.joining(",\n            "))
                + "));\n    }\n";
    }

    public String getTccReservationRuleMethods() {
        if (!hasTccReservationRules()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public List<TccReservationRule> getTccReservationRules() {\n");
        method.append("        return List.of(\n");
        List<com.servpp.lang.models.app.TccReservationRule> rules = scopeItem.getTccReservationRules();
        for (int i = 0; i < rules.size(); i++) {
            com.servpp.lang.models.app.TccReservationRule rule = rules.get(i);
            method.append("                new TccReservationRule(\"")
                    .append(rule.sourceAttributeId())
                    .append("\", \"")
                    .append(rule.reservationAttributeId())
                    .append("\")");
            if (i < rules.size() - 1) {
                method.append(",");
            }
            method.append("\n");
        }
        method.append("        );\n");
        method.append("    }\n\n");
        return method.toString();
    }

    public String getObjectSnapshotImports() {
        if (!hasObjectSnapshotRules() && !hasRepositoryHydrationRules()) {
            return "";
        }
        StringBuilder imports = new StringBuilder();
        imports.append("import java.util.List;\n");
        if (hasObjectSnapshotRules()) {
            imports.append("import com.servpp.service.repository.ObjectSnapshotRule;\n")
                    .append("import com.servpp.service.repository.ObjectSnapshotRuleProvider;\n");
        }
        if (hasRepositoryHydrationRules()) {
            imports.append("import com.servpp.service.repository.RepositoryHydrationRuleProvider;\n")
                    .append("import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;\n");
        }
        return imports.toString();
    }

    public String getCalculationMetadataImports() {
        return hasCalculationAssignments()
                ? "import com.servpp.service.runtime.CalculationAssignment;\n"
                + "import com.servpp.service.runtime.CalculationMetadataProvider;\n"
                : "";
    }

    public String getCalculationMetadataInterface() {
        return hasCalculationAssignments() ? ", CalculationMetadataProvider" : "";
    }

    public String getCalculationAssignmentsMethod() {
        List<IOperationExpression> assignments = calculationAssignmentExpressions();
        if (assignments.isEmpty()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public java.util.List<CalculationAssignment> getCalculationAssignments() {\n");
        method.append("        return java.util.List.of(\n");
        for (int i = 0; i < assignments.size(); i++) {
            IOperationExpression expression = assignments.get(i);
            method.append("                new CalculationAssignment(\"")
                    .append(escapeJava(typedCalculationReference((Reference) expression.getLeft())))
                    .append("\", java.util.List.of(");
            List<String> sourcePaths = sourceReferencePaths(expression);
            for (int sourceIndex = 0; sourceIndex < sourcePaths.size(); sourceIndex++) {
                if (sourceIndex > 0) {
                    method.append(", ");
                }
                method.append("\"").append(escapeJava(sourcePaths.get(sourceIndex))).append("\"");
            }
            method.append("), \"")
                    .append(escapeJava(metadataExpression(expression.getRight())))
                    .append("\")");
            if (i < assignments.size() - 1) {
                method.append(",");
            }
            method.append("\n");
        }
        method.append("        );\n");
        method.append("    }\n\n");
        return method.toString();
    }

    private boolean hasCalculationAssignments() {
        return !calculationAssignmentExpressions().isEmpty();
    }

    private List<IOperationExpression> calculationAssignmentExpressions() {
        if (serviceType != IConstance.ServiceType.calculate || expressions == null) {
            return List.of();
        }
        return expressions.stream()
                .filter(expression -> expression != null
                        && expression.getOperation() instanceof IAssignOperation
                        && expression.getLeft() instanceof Reference reference
                        && (!businessImplementation || reference.getValue().contains(".")))
                .toList();
    }

    private List<String> sourceReferencePaths(IOperationExpression expression) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (expression.getOperation() instanceof IAssignOperation assignOperation
                && assignOperation.getAssocOperation() != null
                && !assignOperation.getAssocOperation().isBlank()
                && expression.getLeft() instanceof Reference leftReference) {
            paths.add(typedCalculationReference(leftReference));
        }
        collectSourceReferencePaths(expression.getRight(), paths);
        return List.copyOf(paths);
    }

    private void collectSourceReferencePaths(IExpression expression, LinkedHashSet<String> paths) {
        if (expression == null) {
            return;
        }
        if (expression instanceof Reference reference) {
            if (!(reference instanceof com.servpp.lang.models.app.expr.CalculationReference ref && ref.local && !ref.getValue().contains("."))) {
                paths.add(typedCalculationReference(reference));
            }
            return;
        }
        if (expression instanceof IOperationExpression operationExpression) {
            collectSourceReferencePaths(operationExpression.getLeft(), paths);
            collectSourceReferencePaths(operationExpression.getRight(), paths);
        }
        if (expression instanceof com.servpp.lang.models.app.expr.CalculationFunction function) {
            function.getArguments().forEach(argument -> collectSourceReferencePaths(argument, paths));
        }
        if (expression instanceof com.servpp.lang.models.app.expr.ConditionalExpression conditional) {
            collectSourceReferencePaths(conditional.getCondition(), paths);
            collectSourceReferencePaths(conditional.getWhenTrue(), paths);
            collectSourceReferencePaths(conditional.getWhenFalse(), paths);
        }
    }

    private String metadataExpression(IExpression expression) {
        if (expression == null) {
            return "";
        }
        if (expression instanceof Reference reference) {
            return reference.getValue() == null ? "" : reference.getValue();
        }
        if (expression instanceof Literal literal) {
            return literal.getValue() == null ? "" : literal.getValue();
        }
        if (expression instanceof com.servpp.lang.models.app.expr.CalculationFunction function) {
            return function.getName() + "(" + function.getArguments().stream().map(this::metadataExpression)
                    .collect(Collectors.joining(", ")) + ")";
        }
        if (expression instanceof com.servpp.lang.models.app.expr.ConditionalExpression conditional) {
            return "(" + metadataExpression(conditional.getCondition()) + " ? " + metadataExpression(conditional.getWhenTrue())
                    + " : " + metadataExpression(conditional.getWhenFalse()) + ")";
        }
        if (expression instanceof IOperationExpression operationExpression) {
            String operator = operationExpression.getOperation() == null
                    ? ""
                    : operationExpression.getOperation().getOperator();
            String left = metadataExpression(operationExpression.getLeft());
            String right = metadataExpression(operationExpression.getRight());
            if (left.isBlank()) {
                return operator + right;
            }
            if (right.isBlank()) {
                return left + operator;
            }
            return left + " " + operator + " " + right;
        }
        return expression.getExpr();
    }

    private String typedCalculationReference(Reference reference) {
        if (reference instanceof com.servpp.lang.models.app.expr.CalculationReference ref && ref.local && ref.root != null
                && reference.getValue().contains(".")) {
            return ref.root.getType().getName() + reference.getValue().substring(reference.getValue().indexOf('.'));
        }
        return typedReferencePath(reference.getValue());
    }

    private String typedReferencePath(String path) {
        if (path == null || path.isBlank() || serviceBody == null) {
            return path;
        }
        int dot = path.indexOf('.');
        if (dot <= 0) {
            return path;
        }
        String root = path.substring(0, dot);
        String suffix = path.substring(dot + 1);
        for (SppLocalVar localVar : serviceBody.getSppLocalVarList()) {
            if (root.equals(localVar.getName()) && localVar.getType() != null) {
                return localVar.getType().getName() + "." + suffix;
            }
        }
        return path;
    }

    private String escapeJava(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public String getObjectSnapshotInterface() {
        StringBuilder interfaces = new StringBuilder();
        if (hasObjectSnapshotRules()) {
            interfaces.append(", ObjectSnapshotRuleProvider");
        }
        if (hasRepositoryHydrationRules()) {
            interfaces.append(", RepositoryHydrationRuleProvider");
        }
        return interfaces.toString();
    }

    public String getCompensationImports() {
        if (!hasCompensationServices()) {
            return "";
        }
        return ((hasObjectSnapshotRules() || hasRepositoryHydrationRules()) ? "" : "import java.util.List;\n")
                + "import com.servpp.service.transaction.CompensationRuleProvider;\n";
    }

    public String getCompensationInterface() {
        return hasCompensationServices() ? ", CompensationRuleProvider" : "";
    }

    public String getCompensationScenarioInterface() {
        return hasCompensationServices() ? " implements CompensationRuleProvider" : "";
    }

    public String getRemoteCompensationImports() {
        if (!hasRemoteCompensationServices()) {
            return "";
        }
        return "import com.servpp.service.runtime.RemoteCompensationRuleProvider;\n"
                + "import java.util.List;\n";
    }

    public String getRemoteCompensationInterface() {
        return hasRemoteCompensationServices() ? ", RemoteCompensationRuleProvider" : "";
    }

    public String getScenarioServiceInterfaces() {
        List<String> interfaces = new ArrayList<>();
        if (hasCompensationServices()) {
            interfaces.add("CompensationRuleProvider");
        }
        if (hasSagaTransactionCallServices()) {
            interfaces.add("SagaTransactionCallProvider");
        }
        interfaces.add("ScopeMetadataProvider");
        interfaces.add("GeneratedServiceKindProvider");
        return " implements " + String.join(", ", interfaces);
    }

    public boolean isIdempotencyExecutionRequired() {
        return systemIdempotencyEnabled && (serviceType == IConstance.ServiceType.update
                || getType() == IConstance.CompilationUnitType.scenario && updateEntry)
                || systemDuplicateGuardEnabled && getType() == IConstance.CompilationUnitType.scenario
                    && scopeItem != null && !scopeItem.getIdempotencyKeyPaths().isEmpty();
    }

    // The service template engine treats every non-null section value as present, including false.
    public SppService getIdempotencyExecution() { return isIdempotencyExecutionRequired() ? this : null; }
    public SppService getEntryExecution() { return serviceType == IConstance.ServiceType.update ? this : null; }

    public String getIdempotencyInvocationCondition() {
        List<String> origins = new ArrayList<>();
        if (systemIdempotencyEnabled && updateEntry) origins.add("invocation.origin() == InvocationContext.Origin.SERVICE");
        if (systemDuplicateGuardEnabled && getType() == IConstance.CompilationUnitType.scenario
                && scopeItem != null && !scopeItem.getIdempotencyKeyPaths().isEmpty())
            origins.add("invocation.origin() == InvocationContext.Origin.USER");
        return "invocation.mode() == InvocationContext.Mode.REMOTE && (" + String.join(" || ", origins) + ")";
    }

    public String getGeneratedServiceKindLiteral() {
        if (getType() == IConstance.CompilationUnitType.scenario) {
            return "SCENARIO";
        }
        if (serviceType == IConstance.ServiceType.query) {
            return "ATOMIC_QUERY";
        }
        if (serviceType == IConstance.ServiceType.update) {
            return "ATOMIC_UPDATE";
        }
        if (serviceType == IConstance.ServiceType.check) {
            return "ATOMIC_CHECK";
        }
        if (serviceType == IConstance.ServiceType.calculate) {
            return "ATOMIC_CALCULATE";
        }
        return "UNKNOWN";
    }

    public String getGeneratedServiceKindMethods() {
        return "    @Override\n"
                + "    public GeneratedServiceKind getGeneratedServiceKind() {\n"
                + "        return GeneratedServiceKind." + getGeneratedServiceKindLiteral() + ";\n"
                + "    }\n\n" + getTechnicalInterceptorMethods();
    }

    public String getScopeInputHeaderNameLiteral() {
        return headerNameLiteral(scopeItem == null || scopeItem.getScopeDefine() == null
                ? null
                : scopeItem.getScopeDefine().getIn());
    }

    public String getScopeOutputHeaderNameLiteral() {
        return headerNameLiteral(scopeItem == null || scopeItem.getScopeDefine() == null
                ? null
                : scopeItem.getScopeDefine().getOut());
    }

    public String getScopeLocalLiteral() {
        return String.valueOf(scopeItem == null || scopeItem.isLocal());
    }

    public String getScopeAsyncLiteral() {
        return String.valueOf(scopeItem != null && scopeItem.isAsync());
    }

    public String getIdempotencyKeyRequiredLiteral() {
        return String.valueOf(requiresIdempotencyKey());
    }

    public String getIdempotencyKeyFieldNameLiteral() {
        if (scopeItem == null || scopeItem.getScopeDefine() == null || scopeItem.getScopeDefine().getIn() == null) {
            return "null";
        }
        String fieldName = scopeItem.getScopeDefine().getIn().getPreferredUniqueIdempotencyFieldName();
        if (fieldName == null || fieldName.isBlank()) {
            return "null";
        }
        return "\"" + fieldName + "\"";
    }

    private boolean requiresIdempotencyKey() {
        return systemIdempotencyEnabled && scopeItem != null && !scopeItem.isLocal() && updateEntry;
    }

    private boolean requiresLocalAtomicUpdateBinding() {
        return scopeItem != null
                && (scopeItem.getResourceId() != null
                || scopeItem.getTransactionType() == IConstance.TransactionType.at
                || scopeItem.getTransactionType() == IConstance.TransactionType.tcc
                || scopeItem.getTransactionType() == IConstance.TransactionType.quantum);
    }

    public boolean isBusinessManagedIdempotency() { return scopeItem != null && scopeItem.isBusinessManagedIdempotency(); }

    private boolean updateEntry;
    public void setUpdateEntry(boolean value) { updateEntry = value; }
    public boolean isUpdateEntry() { return updateEntry; }

    public String getDeclaredIdempotencyMethods() {
        if (scopeItem == null || scopeItem.getIdempotencyKeyPaths().isEmpty()) return "";
        String keys = scopeItem.getIdempotencyKeyPaths().stream().map(path -> "\"" + path + "\"")
                .collect(Collectors.joining(",", "java.util.List.of(", ")"));
        return (scopeItem.getDuplicateWindow() == null ? "" : "    @Override public String getDuplicateWindow() { return \"" + scopeItem.getDuplicateWindow() + "\"; }\n")
                + "    @Override public java.util.List<String> getDeclaredIdempotencyKeyPaths() { return " + keys + "; }\n"
                + (scopeItem.getIdempotencyFactsPath() == null ? "" : "    @Override public String getDeclaredIdempotencyFactsPath() { return \"" + scopeItem.getIdempotencyFactsPath() + "\"; }\n")
                + "    @Override public java.util.List<String> getDeclaredIdempotencyFactsPaths() { return "
                + scopeItem.getIdempotencyFactsPaths().stream().map(path -> "\"" + path + "\"").collect(Collectors.joining(",", "java.util.List.of(", ")")) + "; }\n";
    }

    public String getScopeMetadataMethods() {
        return "    @Override public boolean isSystemIdempotencyEnabled() { return " + systemIdempotencyEnabled + "; }\n"
                + "    @Override public boolean isSystemDuplicateGuardEnabled() { return " + systemDuplicateGuardEnabled + "; }\n"
                + "    @Override public boolean isSystemPartialUpdateEnabled() { return " + systemPartialUpdateEnabled + "; }\n"
                + "    @Override public boolean isUpdateEntry() { return " + updateEntry + "; }\n" + getDeclaredIdempotencyMethods() + "    @Override\n"
                + "    public boolean isLocalScope() {\n"
                + "        return " + getScopeLocalLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public boolean isAsyncScope() {\n"
                + "        return " + getScopeAsyncLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public String getInputHeaderName() {\n"
                + "        return " + getScopeInputHeaderNameLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public String getOutputHeaderName() {\n"
                + "        return " + getScopeOutputHeaderNameLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public boolean requiresPhysicalBackendBinding() {\n"
                + "        return " + getPhysicalBindingRequiredLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public boolean requiresIdempotencyKey() {\n"
                + "        return " + getIdempotencyKeyRequiredLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public String getIdempotencyKeyFieldName() {\n"
                + "        return " + getIdempotencyKeyFieldNameLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public java.util.List<String> getServiceParameterNames() {\n"
                + "        return " + getServiceParameterNamesLiteral() + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public java.util.List<String> getServiceContextParameterNames() {\n"
                + "        return " + getServiceContextParameterNamesLiteral() + ";\n"
                + "    }\n\n";
    }

    private String getServiceParameterNamesLiteral() {
        if (serviceBody == null || serviceBody.getSppLocalVarList().isEmpty()) {
            return "java.util.List.of()";
        }
        return serviceBody.getSppLocalVarList().stream()
                .map(SppLocalVar::getName)
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", ", "java.util.List.of(", ")"));
    }

    private String getServiceContextParameterNamesLiteral() {
        if (serviceBody == null || serviceBody.getSppLocalVarList().isEmpty()) {
            return "java.util.List.of()";
        }
        return serviceBody.getSppLocalVarList().stream()
                .filter(SppLocalVar::isContextParameter)
                .map(SppLocalVar::getName)
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", ", "java.util.List.of(", ")"));
    }

    public String getPhysicalBindingRequiredLiteral() {
        return String.valueOf(scopeItem != null
                && scopeItem.isLocal()
                && (getTransactionResourceIdDeclaredInApp() || "SAGA".equals(getTransactionModeName())));
    }

    private boolean getTransactionResourceIdDeclaredInApp() {
        return scopeItem != null && scopeItem.getResourceId() != null && !scopeItem.getResourceId().isBlank();
    }

    private String headerNameLiteral(AppHeader header) {
        if (header == null || header.getName() == null || header.getName().isBlank()) {
            return "null";
        }
        return "\"" + header.getName() + "\"";
    }

    public boolean hasPersistenceRules() {
        return scopeItem != null && !scopeItem.getPersistenceRules().isEmpty();
    }

    public SppCompilationUnit getDefaultPersistenceClass() {
        return hasPersistenceRules() ? null : defaultClass;
    }

    public String getPersistenceInterface() {
        return hasPersistenceRules() ? ", com.servpp.service.repository.RepositoryWriteRuleProvider" : "";
    }

    public String getPersistenceRuleMethods() {
        if (!hasPersistenceRules()) { return ""; }
        String prefix = "com.servpp.service.repository.";
        java.util.List<String> rules = new java.util.ArrayList<>();
        for (var rule : scopeItem.getPersistenceRules()) {
            var version = scopeItem.getPersistenceVersions().stream()
                    .filter(v -> v.proposed().argumentIndex() == rule.argumentIndex()).findFirst().orElse(null);
            String guard = version == null ? "null" : "new " + prefix + "RepositoryWriteRule.VersionCheck("
                    + version.expected().argumentIndex() + ", \"" + version.expected().getObjectIdPath()
                    + "\", \"" + version.proposed().getObjectIdPath() + "\", " + version.advanceVersion() + ")";
            String keys = rule.keys().stream().map(k -> "\"" + (k.getObjectType().equals(rule.objectType()) ? k.getObjectIdPath() : "@performer:" + k.objectPath()) + "\"")
                    .collect(Collectors.joining(", ", "java.util.List.of(", ")"));
            rules.add("new " + prefix + "RepositoryWriteRule(" + prefix + "RepositoryWriteRule.Operation."
                    + (rule.operation().equals("insertIfAbsent") ? "INSERT_IF_ABSENT" : rule.operation().toUpperCase(java.util.Locale.ROOT)) + ", " + rule.argumentIndex()
                    + ", \"" + rule.objectType() + "\", " + keys + ", " + guard + ")");
        }
        return "    private java.util.function.Supplier<" + prefix + "ObjectRepository> $sppRepositorySupplier;\n"
                + "    @Override public void configureRepositoryWrites(java.util.function.Supplier<" + prefix
                + "ObjectRepository> repository) { this.$sppRepositorySupplier = repository; }\n"
                + "    @Override public Object getRepositoryTransactionResource() {\n"
                + "        if ($sppRepositorySupplier == null) throw new IllegalStateException(\"PERSISTENCE_REPOSITORY_REQUIRED\");\n"
                + "        return $sppRepositorySupplier.get().transactionResourceIdentity();\n    }\n"
                + "    @Override public void executeInRepositoryScope(Runnable action) {\n"
                + "        if ($sppRepositorySupplier == null) throw new IllegalStateException(\"PERSISTENCE_REPOSITORY_REQUIRED\");\n"
                + "        $sppRepositorySupplier.get().executeInTransaction(action);\n    }\n"
                + "    @Override public java.util.List<" + prefix + "RepositoryWriteRule> getRepositoryWriteRules() {\n"
                + "        return java.util.List.of(" + String.join(",\n                ", rules) + ");\n    }\n";
    }

    private java.util.List<SppRoleMapper> roleBindings = java.util.List.of();
    public void setRoleBindings(java.util.List<SppRoleMapper> bindings) { roleBindings = bindings; }
    public String getRoleBindingCompletionStatement() {
        if (isCheckService() || roleBindings.isEmpty()) return "";
        String model = getBasePackage()+"."+getDomainName()+".model.";
        var definitions = roleBindings.stream().map(this::roleDefinition).collect(Collectors.joining(", "));
        var roots = serviceBody.getSppLocalVarList().stream().map(SppLocalVar::getName).collect(Collectors.joining(", "));
        return "        com.servpp.service.runtime.MaterializedRolePerformers.bind(java.util.List.of("+definitions+"), new Object[]{"+roots+"});";
    }
    public String getBusinessNumberAssignmentStatement() {
        if (scopeItem == null) return "";
        return scopeItem.getBusinessNumbers().stream().map(number ->
            "        com.servpp.service.runtime.BusinessNumbering.assign("+serviceBody.getLocalVar(number.target().argumentIndex()).getName()
            +", \""+number.target().getObjectIdPath()+"\", \""+number.prefix()+"\");").collect(Collectors.joining("\n"));
    }

    public String getPersistencePreparationStatement() {
        if (!hasPersistenceRules()) { return ""; }
        String arguments = serviceBody.getSppLocalVarList().stream().map(SppLocalVar::getName)
                .collect(Collectors.joining(", "));
        return "        var $sppPersistenceSession = com.servpp.service.repository.RepositoryWriteSession.prepare("
                + "this.$sppRepositorySupplier, getRepositoryWriteRules(), new Object[]{" + arguments + "});";
    }

    public String getPersistenceCompletionStatement() {
        if (!hasPersistenceRules()) return "";
        String owned = ownedRolePersistenceStatement();
        return owned.isEmpty() ? "        $sppPersistenceSession.persist();"
                : "delete".equals(persistenceOperation) ? "        $sppPersistenceSession.persist();\n" + owned
                : owned + "\n        $sppPersistenceSession.persist();";
    }

    public String getDefaultPersistenceStatement() {
        if (getDefaultPersistenceClass() == null) {
            return "";
        }
        String arguments = serviceBody.getSppLocalVarList().stream()
                .map(SppLocalVar::getName)
                .collect(Collectors.joining(","));
        String mapperCall = "mapper" + defaultClass.getName() + "." + funcName + "(" + arguments + ")";
        if (serviceType == IConstance.ServiceType.query && !returns.getSppLocalVarList().isEmpty()) {
            SppLocalVar returnValue = returns.getSppLocalVarList().get(0);
            return "        ret.set" + returnValue.getType().getName() + "(  " + mapperCall + " );";
        }
        return "        " + mapperCall + ";";
    }

    public String getObjectSnapshotRulesMethod() {
        if (!hasObjectSnapshotRules() && !hasRepositoryHydrationRules()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        if (hasObjectSnapshotRules()) {
            method.append("    @Override\n");
            method.append("    public List<ObjectSnapshotRule> getObjectSnapshotRules() {\n");
            method.append("        return List.of(\n");
            List<com.servpp.lang.models.app.ObjectSnapshotRule> rules = scopeItem.getObjectSnapshotRules();
            for (int i = 0; i < rules.size(); i++) {
                com.servpp.lang.models.app.ObjectSnapshotRule rule = rules.get(i);
                method.append("                ObjectSnapshotRule.");
                if (rule.source() == com.servpp.lang.models.app.ObjectSnapshotRule.Source.argument) {
                    method.append(rule.appDdlInferred() ? "appDdlArgument(" : "argument(")
                            .append(rule.argumentIndex())
                            .append(", \"")
                            .append(rule.getObjectType())
                            .append("\", ");
                    appendObjectSnapshotIdPaths(method, rule);
                    appendObjectSnapshotTimingAndRepository(method, rule);
                    method.append(")");
                } else if (rule.source() == com.servpp.lang.models.app.ObjectSnapshotRule.Source.current) {
                    method.append("current(\"")
                            .append(rule.getObjectType())
                            .append("\", ");
                    appendObjectSnapshotIdPaths(method, rule);
                    appendObjectSnapshotTimingAndRepository(method, rule);
                    method.append(")");
                } else {
                    method.append("transaction(");
                    if (rule.repositoryName() != null) {
                        method.append("ObjectSnapshotRule.Timing.")
                                .append(objectSnapshotRuntimeTimingName(rule.timing()))
                                .append(", \"")
                                .append(rule.repositoryName())
                                .append("\"");
                    } else if (rule.timing() != com.servpp.lang.models.app.ObjectSnapshotRule.Timing.afterCommit) {
                        method.append("ObjectSnapshotRule.Timing.")
                                .append(objectSnapshotRuntimeTimingName(rule.timing()));
                    }
                    method.append(")");
                }
                if (i < rules.size() - 1) {
                    method.append(",");
                }
                method.append("\n");
            }
            method.append("        );\n");
            method.append("    }\n\n");
        }
        if (hasRepositoryHydrationRules()) {
            method.append("    @Override\n");
            method.append("    public List<HydrationRule> getRepositoryHydrationRules() {\n");
            method.append("        return List.of(\n");
            List<com.servpp.lang.models.app.RepositoryHydrationRule> rules =
                    scopeItem.getRepositoryHydrationRules();
            for (int i = 0; i < rules.size(); i++) {
                com.servpp.lang.models.app.RepositoryHydrationRule rule = rules.get(i);
                method.append("                ");
                appendRepositoryHydrationRule(method, rule);
                if (scopeItem.isOptionalRepository()) { method.append(".asOptional()"); }
                var readTarget = serviceBody.getQualifieField(rule.responseMethodName());
                if (readTarget != null) method.append(".transactionRead(").append(!hasQuantumFields(readTarget.getType())).append(")");
                if (scopeItem.isRepositoryReadLocked()) { method.append(".forUpdate()"); }
                if (i < rules.size() - 1) {
                    method.append(",");
                }
                method.append("\n");
            }
            method.append("        );\n");
            method.append("    }\n\n");
        }
        return method.toString();
    }

    private String repositoryParticipantTarget(com.servpp.lang.models.app.RepositoryHydrationRule rule) {
        String[] path = rule.responseMethodName().split("\\.", 2);
        var parameters = serviceBody.getSppLocalVarList();
        for (int index = 0; index < parameters.size(); index++) if (parameters.get(index).getName().equals(path[0]))
            return "@participant:" + index + ":" + (path.length == 1 ? "" : path[1]);
        throw new IllegalStateException("Undefined query participant " + rule.responseMethodName());
    }

    private void appendRepositoryHydrationRule(StringBuilder method,
                                               com.servpp.lang.models.app.RepositoryHydrationRule rule) {
        if (rule.keyTargets().size() <= 1) {
            com.servpp.lang.models.app.RepositoryHydrationRule.KeyTarget target = rule.firstKey();
            if (target.objectPath().equals(target.repositoryObjectPath())) {
                method.append("new HydrationRule(\"")
                        .append(getFuncName())
                        .append("\", ")
                        .append(rule.argumentIndex())
                        .append(", \"")
                        .append(rule.getObjectType())
                        .append("\", \"")
                        .append(rule.getObjectIdPath())
                        .append("\", \"")
                        .append(repositoryParticipantTarget(rule))
                        .append("\", ")
                        .append(rule.repositoryName() == null ? "null" : "\"" + rule.repositoryName() + "\"")
                        .append(", ")
                        .append(rule.appDdlInferred())
                        .append(", ")
                        .append(rule.sourceHint() == null ? "null" : "\"" + rule.sourceHint() + "\"")
                        .append(")");
                return;
            }
            method.append("new HydrationRule(\"")
                    .append(getFuncName())
                    .append("\", \"")
                    .append(rule.getObjectType())
                    .append("\", List.of(new HydrationRule.KeyTarget(")
                    .append(target.argumentIndex())
                    .append(", \"")
                    .append(target.getObjectType())
                    .append("\", \"")
                    .append(target.getObjectIdPath())
                    .append("\", \"")
                    .append(target.getRepositoryObjectIdPath())
                    .append("\")), \"")
                    .append(repositoryParticipantTarget(rule))
                    .append("\", ")
                    .append(rule.repositoryName() == null ? "null" : "\"" + rule.repositoryName() + "\"")
                    .append(", ")
                    .append(rule.appDdlInferred())
                    .append(", ")
                    .append(rule.sourceHint() == null ? "null" : "\"" + rule.sourceHint() + "\"")
                    .append(")");
            return;
        }
        method.append("new HydrationRule(\"")
                .append(getFuncName())
                .append("\", \"")
                .append(rule.getObjectType())
                .append("\", List.of(");
        for (int i = 0; i < rule.keyTargets().size(); i++) {
            com.servpp.lang.models.app.RepositoryHydrationRule.KeyTarget target = rule.keyTargets().get(i);
            method.append("new HydrationRule.KeyTarget(")
                    .append(target.argumentIndex())
                    .append(", \"")
                    .append(target.getObjectType())
                    .append("\", \"")
                    .append(target.getObjectIdPath())
                    .append("\", \"")
                    .append(target.getRepositoryObjectIdPath())
                    .append("\")");
            if (i < rule.keyTargets().size() - 1) {
                method.append(", ");
            }
        }
        method.append("), \"")
                .append(repositoryParticipantTarget(rule))
                .append("\", ")
                .append(rule.repositoryName() == null ? "null" : "\"" + rule.repositoryName() + "\"")
                .append(", ")
                .append(rule.appDdlInferred())
                .append(", ")
                .append(rule.sourceHint() == null ? "null" : "\"" + rule.sourceHint() + "\"")
                .append(")");
    }

    private void appendObjectSnapshotTiming(StringBuilder method,
                                            com.servpp.lang.models.app.ObjectSnapshotRule rule) {
        if (rule.timing() == com.servpp.lang.models.app.ObjectSnapshotRule.Timing.afterCommit) {
            return;
        }
        method.append(", ObjectSnapshotRule.Timing.")
                .append(objectSnapshotRuntimeTimingName(rule.timing()));
    }

    private void appendObjectSnapshotTimingAndRepository(StringBuilder method,
                                                         com.servpp.lang.models.app.ObjectSnapshotRule rule) {
        if (rule.repositoryName() != null) {
            method.append(", ObjectSnapshotRule.Timing.")
                    .append(objectSnapshotRuntimeTimingName(rule.timing()))
                    .append(", \"")
                    .append(rule.repositoryName())
                    .append("\"");
            return;
        }
        appendObjectSnapshotTiming(method, rule);
    }

    private String objectSnapshotRuntimeTimingName(com.servpp.lang.models.app.ObjectSnapshotRule.Timing timing) {
        return switch (timing) {
            case before -> "BEFORE";
            case after -> "AFTER";
            case commit -> "COMMIT";
            case afterCommit -> "AFTER_COMMIT";
        };
    }

    private void appendObjectSnapshotIdPaths(StringBuilder method,
                                             com.servpp.lang.models.app.ObjectSnapshotRule rule) {
        if (rule.objectIdPaths().size() == 1) {
            method.append("\"").append(rule.getObjectIdPath()).append("\"");
            return;
        }
        method.append("List.of(");
        for (int i = 0; i < rule.objectIdPaths().size(); i++) {
            if (i > 0) {
                method.append(", ");
            }
            method.append("\"").append(rule.objectIdPaths().get(i)).append("\"");
        }
        method.append(")");
    }

    public String getCompensationServiceNamesMethod() {
        if (!hasCompensationServices()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public List<String> getCompensationServiceNames() {\n");
        method.append("        return List.of(");
        List<String> serviceNames = scopeItem.getCompensationServiceNames();
        for (int i = 0; i < serviceNames.size(); i++) {
            if (i > 0) {
                method.append(", ");
            }
            method.append("\"").append(serviceNames.get(i)).append("\"");
        }
        method.append(");\n");
        method.append("    }\n\n");
        return method.toString();
    }

    public String getRemoteCompensationServiceNamesMethod() {
        if (!hasRemoteCompensationServices()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public List<String> getRemoteCompensationServiceNames() {\n");
        method.append("        return List.of(");
        List<String> serviceNames = scopeItem.getRemoteCompensationServiceNames();
        for (int i = 0; i < serviceNames.size(); i++) {
            if (i > 0) {
                method.append(", ");
            }
            method.append("\"").append(serviceNames.get(i)).append("\"");
        }
        method.append(");\n");
        method.append("    }\n\n");
        return method.toString();
    }

    public String getSagaTransactionCallImports() {
        if (!hasSagaTransactionCallServices()) {
            return "";
        }
        return (hasCompensationServices() ? "" : "import java.util.List;\n")
                + "import com.servpp.service.runtime.SagaTransactionCallProvider;\n";
    }

    public String getSagaTransactionCallMethods() {
        List<String> serviceNames = getSagaTransactionCallServiceNames();
        if (serviceNames.isEmpty()) {
            return "";
        }
        StringBuilder method = new StringBuilder();
        method.append("    @Override\n");
        method.append("    public List<String> getSagaTransactionCallServiceNames() {\n");
        method.append("        return List.of(");
        for (int i = 0; i < serviceNames.size(); i++) {
            if (i > 0) {
                method.append(", ");
            }
            method.append("\"").append(serviceNames.get(i)).append("\"");
        }
        method.append(");\n");
        method.append("    }\n\n");
        return method.toString();
    }

    public boolean hasSagaTransactionCallServices() {
        return !getSagaTransactionCallServiceNames().isEmpty();
    }

    public List<String> getSagaTransactionCallServiceNames() {
        if (getType() != IConstance.CompilationUnitType.scenario
                || serviceBody == null
                || serviceBody.getSppServiceCallList() == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Set<SppService> delegates = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        delegates.add(this);
        collectSagaTransactionCallServiceNames(serviceBody.getSppServiceCallList(), false, names, delegates);
        return List.copyOf(names);
    }

    private void collectSagaTransactionCallServiceNames(List<IServiceBodyStatement> statements,
                                                        boolean insideTransaction,
                                                        Set<String> names, Set<SppService> delegates) {
        if (statements == null) {
            return;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall serviceCall) {
                collectSagaTransactionCallServiceNames(serviceCall.getGuardStatements(), insideTransaction, names, delegates);
                if (insideTransaction && serviceCall.getCallee() != null) {
                    names.add(serviceCall.getCallee().getFuncName());
                } else if (scopeItem != null && scopeItem.getTransactionType() == IConstance.TransactionType.saga
                        && serviceCall.getCallee() instanceof SppScenario callee
                        && callee.getScopeItem() != null && callee.getScopeItem().isLocal()
                        && delegates.add(callee)) {
                    collectSagaTransactionCallServiceNames(callee.getServiceBody().getSppServiceCallList(), false, names, delegates);
                }
            } else if (statement instanceof TransactionBlock transactionBlock) {
                collectSagaTransactionCallServiceNames(transactionBlock.getTransactionCallList(), true, names, delegates);
            } else if (statement instanceof ExecuteBlock executeBlock) {
                collectSagaTransactionCallServiceNames(executeBlock.getExecuteCallList(), insideTransaction, names, delegates);
            }
        }
    }

    public String getResponseHeaderImports() {
        AppHeader out = getOutHeader();
        if (out == null || !hasResponseHeaderDefaults(out)) {
            return "";
        }
        return "import " + getBasePackage() + "." + getDomainName() + ".model." + toJavaTypeName(out.getName()) + ";\n";
    }

    public String getResponseHeaderInitialization() {
        AppHeader out = getOutHeader();
        if (out == null || !hasResponseHeaderDefaults(out)) {
            return "";
        }
        StringBuilder initialization = new StringBuilder();
        initialization.append("        ret.appHeader(new ")
                .append(toJavaTypeName(out.getName()))
                .append("()");
        for (SppExtendField field : out.getSppExtendFields()) {
            if (field.getDefaultValue() == null || field.getDefaultValue().isBlank()) {
                continue;
            }
            initialization.append(".")
                    .append(field.getName())
                    .append("(")
                    .append(field.getDefaultValue())
                    .append(")");
        }
        initialization.append(");\n");
        return initialization.toString();
    }

    public String getTransactionResourceIdLiteral() {
        if (localServiceProvider != null) return localServiceProvider.getTransactionResourceIdLiteral();
        if (scopeItem != null && scopeItem.getResourceId() != null && !scopeItem.getResourceId().isBlank()) {
            return scopeItem.getResourceId();
        }
        List<QuantumServiceAttribute> attributes = getQuantumServiceAttributes();
        if (!attributes.isEmpty()) {
            return attributes.get(0).getGroupId();
        }
        List<String> assignedResources = collectAssignedResourceIds();
        if (!assignedResources.isEmpty()) {
            return String.join("+", assignedResources);
        }
        List<SppLocalVar> localVars = serviceBody.getSppLocalVarList();
        if (!localVars.isEmpty()) {
            return localVars.get(0).getType().getName();
        }
        return getFuncName();
    }

    private boolean isTccService() {
        return "TCC".equals(getTransactionModeName());
    }

    private boolean hasObjectSnapshotRules() {
        return scopeItem != null && scopeItem.hasObjectSnapshotRules();
    }

    private boolean hasRepositoryHydrationRules() {
        return scopeItem != null && scopeItem.hasRepositoryHydrationRules();
    }

    private boolean hasTccLifecycleServices() {
        return scopeItem != null && scopeItem.hasTccLifecycleServices();
    }

    private boolean hasTccReservationRules() {
        return scopeItem != null && scopeItem.hasTccReservationRules();
    }

    private boolean hasCompensationServices() {
        return scopeItem != null && scopeItem.hasCompensationServices();
    }

    private boolean hasRemoteCompensationServices() {
        return scopeItem != null && scopeItem.hasRemoteCompensationServices();
    }

    public boolean hasWriteOperations() {
        return !collectAssignedResourceIds().isEmpty();
    }

    public List<String> getWriteOperationPaths() {
        return collectAssignedReferencePaths();
    }

    public List<String> getAssignedQuantumAttributeIds() {
        return collectAssignedQuantumServiceAttributes().stream()
                .map(QuantumServiceAttribute::getAttributeId)
                .toList();
    }

    public Map<String, Set<String>> getAssignedQuantumAttributeDirections() {
        if (expressions == null || expressions.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> directions = new LinkedHashMap<>();
        for (IOperationExpression expression : expressions) {
            if (!(expression.getOperation() instanceof IAssignOperation assignOperation)) {
                continue;
            }
            String direction = quantumAssignmentDirection(expression, assignOperation);
            if (direction == null) {
                continue;
            }
            IExpression left = expression.getLeft();
            if (left instanceof Reference reference) {
                QuantumServiceAttribute attribute = toQuantumServiceAttribute(reference);
                if (attribute != null) {
                    directions.computeIfAbsent(attribute.getAttributeId(), key -> new LinkedHashSet<>())
                            .add(direction);
                }
            }
        }
        return directions;
    }

    private String quantumAssignmentDirection(IOperationExpression expression, IAssignOperation assignOperation) {
        String assocOperation = assignOperation.getAssocOperation();
        if ("+".equals(assocOperation)) {
            return "increase";
        }
        if ("-".equals(assocOperation)) {
            return "decrease";
        }
        if (assocOperation == null && isZeroLiteral(expression.getRight())) {
            return "decrease";
        }
        if (assocOperation == null && expression.getLeft() instanceof Reference assignedReference) {
            return inferSimpleAssignmentDirection(assignedReference, expression.getRight());
        }
        return null;
    }

    private String inferSimpleAssignmentDirection(Reference assignedReference, IExpression rightExpression) {
        if (!(rightExpression instanceof IOperationExpression operationExpression)) {
            return null;
        }
        String operator = operationExpression.getOperation().getOperator();
        if ("+".equals(operator)
                && (isSameReference(assignedReference, operationExpression.getLeft())
                || isSameReference(assignedReference, operationExpression.getRight()))) {
            return "increase";
        }
        if ("-".equals(operator) && isSameReference(assignedReference, operationExpression.getLeft())) {
            return "decrease";
        }
        return null;
    }

    private boolean isSameReference(Reference reference, IExpression expression) {
        return expression instanceof Reference otherReference
                && reference.getValue() != null
                && reference.getValue().equals(otherReference.getValue());
    }

    private boolean isZeroLiteral(IExpression expression) {
        return expression instanceof Literal literal
                && literal.getValue() != null
                && literal.getValue().matches("0+(\\.0+)?");
    }

    private void collectQuantumServiceAttributes() {
        // The provider owns its conservation records; an APP-bound facade must not record the same movement twice.
        if (localServiceBinding != null || !quantumService && !declaredQuantumService
                && (scopeItem == null || !scopeItem.hasQuantumConservationAttributes())) return;
        // Enlisted persistence stores a prepared record; value movement belongs to its conserved service.
        if (hasPersistenceRules() && !scopeItem.hasQuantumConservationAttributes()) { return; }
        List<QuantumServiceAttribute> assignedAttributes = collectAssignedQuantumServiceAttributes();
        if (!assignedAttributes.isEmpty() && (scopeItem == null || !scopeItem.hasQuantumConservationAttributes())) {
            assignedAttributes.forEach(this::addQuantumServiceAttribute);
            return;
        }

        Set<String> written = assignedAttributes.stream().map(QuantumServiceAttribute::getAttributeId)
                .collect(Collectors.toSet());
        List<SppLocalVar> localVars = serviceBody.getSppLocalVarList();
        for (int i = 0; i < localVars.size(); i++) {
            SppLocalVar localVar = localVars.get(i);
            if (localVar.getType() instanceof SppClass sppClass) {
                for (SppField field : sppClass.getSppFieldList()) {
                    if (field.isQuantum()) {
                        String attributeId = sppClass.getName() + "." + field.getName();
                        boolean conserved = isExplicitQuantumConservationAttribute(attributeId);
                        if (!conserved && !written.contains(attributeId)) continue;
                        // APP conservation selection must never omit an actual quantum write.
                        addQuantumServiceAttribute(new QuantumServiceAttribute(i,
                                conserved ? getQuantumConservationGroupId() : "$state",
                                attributeId, field.getName()));
                    }
                }
            }
        }
    }

    private List<QuantumServiceAttribute> collectAssignedQuantumServiceAttributes() {
        if (expressions == null || expressions.isEmpty()) {
            return List.of();
        }
        Map<String, QuantumServiceAttribute> attributes = new LinkedHashMap<>();
        for (IOperationExpression expression : expressions) {
            if (!(expression.getOperation() instanceof IAssignOperation)) {
                continue;
            }
            IExpression left = expression.getLeft();
            if (left instanceof Reference reference) {
                QuantumServiceAttribute attribute = toQuantumServiceAttribute(reference);
                if (attribute != null) {
                    attributes.put(attribute.getAttributeId(), attribute);
                }
            }
        }
        String groupId = getQuantumConservationGroupId(attributes);
        return attributes.values().stream()
                .map(attribute -> new QuantumServiceAttribute(attribute.getArgumentIndex(), groupId,
                        attribute.getAttributeId(), attribute.getPropertyName()))
                .toList();
    }

    private List<String> collectAssignedResourceIds() {
        if (expressions == null || expressions.isEmpty()) {
            return List.of();
        }
        Map<String, String> resources = new LinkedHashMap<>();
        for (IOperationExpression expression : expressions) {
            if (!(expression.getOperation() instanceof IAssignOperation)) {
                continue;
            }
            IExpression left = expression.getLeft();
            if (left instanceof Reference reference) {
                String resourceId = toAssignedResourceId(reference.getValue());
                if (resourceId != null) {
                    resources.put(resourceId, resourceId);
                }
            }
        }
        return new ArrayList<>(resources.values());
    }

    private List<String> collectAssignedReferencePaths() {
        if (expressions == null || expressions.isEmpty()) {
            return List.of();
        }
        Map<String, String> references = new LinkedHashMap<>();
        for (IOperationExpression expression : expressions) {
            if (!(expression.getOperation() instanceof IAssignOperation)) {
                continue;
            }
            IExpression left = expression.getLeft();
            if (left instanceof Reference reference) {
                references.put(reference.getValue(), reference.getValue());
            } else if (left != null) {
                references.put(left.getExpr(), left.getExpr());
            }
        }
        return new ArrayList<>(references.values());
    }

    private String toAssignedResourceId(String reference) {
        String[] parts = reference.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        for (SppLocalVar localVar : serviceBody.getSppLocalVarList()) {
            if (localVar.getName().equals(parts[0])) {
                return localVar.getType().getName();
            }
        }
        return null;
    }

    private QuantumServiceAttribute toQuantumServiceAttribute(Reference reference) {
        if (reference instanceof com.servpp.lang.models.app.expr.CalculationReference resolved
                && resolved.field instanceof SppField field && field.isQuantum() && resolved.root != null) {
            var parameters = serviceBody.getSppLocalVarList();
            var matches = parameters.stream().filter(p -> p.getType().getName().equals(resolved.root.getType().getName())
                    && (p.getName().equals(resolved.root.getName()) || resolved.local && p.isParticipantCollection())).toList();
            if (matches.size() == 1) return new QuantumServiceAttribute(parameters.indexOf(matches.get(0)), getFuncName(),
                    resolved.root.getType().getName() + "." + field.getName(), field.getName());
        }
        return toQuantumServiceAttribute(reference.getValue());
    }

    private QuantumServiceAttribute toQuantumServiceAttribute(String reference) {
        String[] parts = reference.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        List<SppLocalVar> localVars = serviceBody.getSppLocalVarList();
        for (int i = 0; i < localVars.size(); i++) {
            SppLocalVar localVar = localVars.get(i);
            if (!localVar.getName().equals(parts[0]) || !(localVar.getType() instanceof SppClass sppClass)) {
                continue;
            }
            SppField field = sppClass.getField(parts[1]);
            if (field == null || !field.isQuantum()) {
                return null;
            }
            return new QuantumServiceAttribute(i, getFuncName(),
                    sppClass.getName() + "." + field.getName(), field.getName());
        }
        return null;
    }

    private String toConservationGroupId(Map<String, QuantumServiceAttribute> attributes) {
        if (attributes.size() < 2) {
            return getFuncName();
        }
        return attributes.values().stream()
                .map(QuantumServiceAttribute::getAttributeId)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("+"));
    }

    private String getQuantumConservationGroupId(Map<String, QuantumServiceAttribute> attributes) {
        if (scopeItem != null && scopeItem.hasQuantumConservationAttributes()
                && !scopeItem.isQuantumResourceInferred()
                && scopeItem.getResourceId() != null && !scopeItem.getResourceId().isBlank()) {
            return scopeItem.getResourceId();
        }
        return toConservationGroupId(attributes);
    }

    private String getQuantumConservationGroupId() {
        if (scopeItem != null && scopeItem.hasQuantumConservationAttributes()
                && !scopeItem.isQuantumResourceInferred()
                && scopeItem.getResourceId() != null && !scopeItem.getResourceId().isBlank()) {
            return scopeItem.getResourceId();
        }
        return getFuncName();
    }

    private boolean isExplicitQuantumConservationAttribute(String attributeId) {
        return scopeItem == null || !scopeItem.hasQuantumConservationAttributes()
                || scopeItem.getQuantumConservationAttributeIds().contains(attributeId);
    }

    private AppHeader getOutHeader() {
        if (scopeItem == null || scopeItem.getScopeDefine() == null) {
            return null;
        }
        return scopeItem.getScopeDefine().getOut();
    }

    private boolean hasResponseHeaderDefaults(AppHeader header) {
        for (SppExtendField field : header.getSppExtendFields()) {
            if (field.getDefaultValue() != null && !field.getDefaultValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String toJavaTypeName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public void setExpressions(List<IOperationExpression> expressions) {
        this.expressions = expressions;
    }

    public void setJavaStatements(List<IJavaStatement> javaStatements) {
        this.javaStatements = javaStatements;
    }

    public ScopeItem getScopeItem() {
        return scopeItem;
    }

    public boolean isLocalService() {
        return scopeItem == null || scopeItem.isLocal();
    }

    public boolean getLocalService() {
        return isLocalService();
    }

    public String getDirectArgumentCallJava() {
        var args = new java.util.ArrayList<String>();
        var parameters = serviceBody.getSppLocalVarList();
        for (int index=0;index<parameters.size();index++) {
            var parameter=parameters.get(index);
            args.add("(" + parameter.getServiceJavaType() + ") params[" + index + "]");
        }
        return String.join(",", args);
    }

    public String getAtomicReturnType() { return "void"; }
    public boolean isCheckService() { return getServiceType() == IConstance.ServiceType.check; }
    public String getCheckExecutionPrefix() { return isCheckService() ? "com.servpp.service.runtime.BusinessChecks.executeCheck(() -> " : ""; }
    public String getCheckExecutionSuffix() { return isCheckService() ? ")" : ""; }

    public String getAtomicResponseImport() {
        // Participant services have void signatures, including generated repository interfaces.
        return "";
    }

    public void setScopeItem(ScopeItem scopeItem) {
        this.scopeItem = scopeItem;
    }

    public ServiceBody getServiceBody() {
        return serviceBody;
    }
    public String addLocalVar(SppLocalVar sppLocalVar){
        return serviceBody.addLocalVar(sppLocalVar);
    }

    public SppLocalVar getLocalVar(String varName){
        return serviceBody.getLocalVar(varName);
    }
    public SppLocalVar getLocalVar(int index){
        return serviceBody.getLocalVar(index);
    }

    public String getFuncName() {
        return funcName;
    }

    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    public IConstance.ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(IConstance.ServiceType returnType) {
        this.serviceType = returnType;
    }

    @Override
    public void copyFrom(SppCompilationUnit cls) {
        super.copyFrom(cls);
        SppService sppService = (SppService) cls;
        technicalBindings = sppService.technicalBindings;
        serviceBody = sppService.serviceBody;
        returns = sppService.returns;
        serviceType = sppService.serviceType;
        funcName = sppService.funcName;
        quantumService = sppService.quantumService;
        declaredQuantumService = sppService.declaredQuantumService;
        businessImplementation = sppService.businessImplementation;
        numericGeneration = sppService.numericGeneration;
        javaStatements = sppService.javaStatements;
        expressions = sppService.expressions;
        quantumServiceAttributes = sppService.quantumServiceAttributes;
    }

    @Override
    public String toString() {
        return "SppService{" +
                "serviceType=" + serviceType +
                ", funcName='" + funcName + '\'' +
                ", serviceBody=" + serviceBody +
                ", name='" + getName() + '\'' +
                ", type=" + type +
                ", sppFieldMap=" + sppFieldMap +
                ", sppFieldList=" + sppFieldList +
                '}';
    }
}
