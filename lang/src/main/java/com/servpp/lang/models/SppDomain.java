package com.servpp.lang.models;

import com.servpp.ILogable;
import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.ISppErrorLogger;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.app.*;
import org.antlr.v4.runtime.ParserRuleContext;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author lidong@date 2023-11-01@version 1.0
 */
public class SppDomain {
    private final Map<String, SppConstant> constants = new java.util.LinkedHashMap<>();
    public Map<String, SppConstant> getConstants() { return constants; }
    private static final String DEFAULT_TRANSACTION_MANAGER =
            "com.servpp.service.transaction.DefaultTransactionManager";
    private static final String JDBC_BUSINESS_IDEMPOTENCY_STORE =
            "com.servpp.service.idempotency.JdbcBusinessIdempotencyStore";
    private static final String JDBC_SAGA_LOG_STORE =
            "com.servpp.service.transaction.JdbcSagaLogStore";
    private static final String JDBC_TCC_BRANCH_STORE =
            "com.servpp.service.transaction.JdbcTccBranchStore";
    private static final String JDBC_TCC_RESERVATION_STORE =
            "com.servpp.service.transaction.JdbcTccReservationStore";
    private static final String JDBC_QUANTUM_JOURNAL_STORE =
            "com.servpp.service.transaction.JdbcQuantumJournalStore";
    private static final String NATIVE_SEATA_TRANSACTION_BRIDGE =
            "com.servpp.service.runtime.seata.NativeSeataTransactionBridge";
    private static final String REMOTE_ROLE_ADAPTER_PROTOCOL_HTTP_JSON = "HTTP_JSON";

    Map<String, SppCompilationUnit> mapsOfClass = new HashMap<>();
    List<ErrorContent> unFoundClass = new ArrayList<>();
    List<Object[]> unknownServiceCall = new ArrayList<>();
    private final List<String> appParseErrors = new ArrayList<>();
    private SppCompilationUnit currentClass = null;
    private List<SppDefaultService> sppDefaultServices = new ArrayList<>();

    private Map<SppField, SppFieldReference> sppFieldReference = new HashMap<>();

    private String name;
    private RuleBlock ruleBlock = new RuleBlock();

    private SppProject project;
    public void setProject(SppProject project) { this.project = project; }
    public SppProject getProject() { return project; }
    /** Read-only lookup of the storage owned by an explicitly declared foreign participant. */
    public AppTable resolveAppTable(String businessType) {
        for (var group : ruleBlock.getAppTables()) for (var table : group.getAppTableList())
            if (table.getEntity() != null && table.getEntity().getName().equals(businessType)) return table;
        var unit = getSppClass(businessType);
        if (project == null || !(unit instanceof SppClass reference) || reference.getReferenceSource() == null) return null;
        String[] source = reference.getReferenceSource().split("\\.");
        var owner = source.length == 2 ? project.getDomain(source[0]) : null;
        if (owner == null || owner == this) return null;
        // A Reference is a view; it must never become a second physical table owner.
        for (var group : owner.getRuleBlock().getAppTables()) for (var table : group.getAppTableList())
            if (table.getEntity() != null && table.getEntity().getName().equals(source[1])) return table;
        return null;
    }
    private Map<String, AppTable> referencedAppTables() {
        Map<String, AppTable> tables = new LinkedHashMap<>();
        for (var unit : mapsOfClass.values()) if (unit instanceof SppClass reference && reference.getReferenceSource() != null) {
            var table = resolveAppTable(reference.getName());
            if (table != null) tables.put(reference.getName(), table);
        }
        return tables;
    }

    public SppDomain(String name) {
        for(String primary: IConstance.primaryTypes)
            addClass(new SppClass(primary));
        this.name = name;
    }

    public SppFieldReference getSppFieldReference(SppField sppField){
        return sppFieldReference.get(sppField);
    }

    public SppFieldReference addSppFieldReference(SppField sppField){
        SppFieldReference ret = getSppFieldReference(sppField);
        if (ret == null){
            ret = new SppFieldReference(sppField);
            sppFieldReference.put(sppField,ret);
        }
        return ret;
    }

    public List<SppDefaultService> getSppDefaultServices() {
        return sppDefaultServices;
    }

    public List<Object[]> getUnknownServiceCall() {
        return unknownServiceCall;
    }

    public void addAppParseError(String error) {
        if (error != null && !error.isBlank() && !appParseErrors.contains(error)) {
            appParseErrors.add(error);
        }
    }

    public List<String> getAppParseErrors() {
        return appParseErrors;
    }

    public RuleBlock getRuleBlock() {
        return ruleBlock;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, SppCompilationUnit> getMapsOfClass() {
        return mapsOfClass;
    }

    public SppCompilationUnit getCurrentClass() {
        return currentClass;
    }

    public void setCurrentClass(SppCompilationUnit currentClass) {
        this.currentClass = currentClass;
    }

    public String checkClass(String clsName) {
        SppCompilationUnit sppClass = mapsOfClass.get(clsName);
        if (sppClass == null || !sppClass.isReal())
            return "Class " + clsName + " not found!";
        return null;
    }

    public String checkDupClass(String clsName) {
        SppCompilationUnit sppClass = mapsOfClass.get(clsName);
        if ( sppClass != null && sppClass.isReal())
            return "Class " + clsName + " duplicated!";
        return null;
    }

    public SppCompilationUnit addClass(SppCompilationUnit cls) {
        String name = cls.getName();
        SppCompilationUnit sppClass = mapsOfClass.get(name);
        if ( sppClass != null) {
            sppClass.copyFrom(cls);
            if (cls instanceof SppScenario && !(sppClass instanceof SppScenario)) {
                // A forward service reference does not yet know its declaration kind.
                sppClass = cls;
                mapsOfClass.put(name, cls);
            }
        }else {
            sppClass = cls;
            mapsOfClass.put(name, sppClass);
        }
        currentClass = sppClass;
        return sppClass;
    }

    public SppCompilationUnit getSppClass(String clsName) {
        return  mapsOfClass.get(clsName);
    }
    public SppCompilationUnit getSppClass(IConstance.CompilationUnitType type,String clsName) {
        SppCompilationUnit sppClass = mapsOfClass.get(clsName);
        if(sppClass == null){
            sppClass = genObjectDecl(type,clsName);
            sppClass.setReal(false);
            mapsOfClass.put(clsName,sppClass);
        }
        return sppClass;
    }

    public void addUnFoundClass(ErrorContent errorContent){
        unFoundClass.add(errorContent);
    }

    public void checkSemanticFinally(ISppErrorLogger listener){
        prepareSystemResults();
        prepareImplicitParticipantServices();
        resolveServiceReferences();
        checkUnfoundClass(listener);
        checkUnknowServiceCall(listener);
    }

    public List<String> validateBusinessFieldMeanings() {
        List<String> errors = new ArrayList<>();
        for (var unit : mapsOfClass.values()) {
            if (!(unit instanceof SppClass type) || type instanceof SppService) continue;
            for (var field : type.getSppFieldList()) {
                if (field.isSyntheticRolePerformer()) continue;
                String name = field.getName();
                if (field.isDefaultParticipantName()
                        && (field.getType().getType() == null || !java.util.Set.of(IConstance.CompilationUnitType.role, IConstance.CompilationUnitType.contract,
                                IConstance.CompilationUnitType.entity, IConstance.CompilationUnitType.reference)
                                .contains(field.getType().getType())))
                    errors.add("SPP field " + type.getName() + "." + name
                            + " can omit its instance name only for a participant type; scalar and enum facts need explicit names.");
                boolean technical = name.equals("id") || name.endsWith("Id") || name.endsWith("ID")
                        || java.util.Set.of("version", "rowKey", "rowId", "requestId", "recordId").contains(name);
                if ((technical && field.getBusinessMeaning() == null)
                        || (field.getBusinessMeaning() != null && field.getBusinessMeaning().isBlank()))
                    errors.add("SPP field " + type.getName() + "." + name
                            + " requires an explicit business meaning; move technical identity/version/request metadata to APP/PHY.");
            }
        }
        return errors;
    }

    public List<String> validateAppDeclarations() {
        prepareSystemResults();
        prepareImplicitParticipantServices();
        resolveServiceReferences();
        List<String> errors = new ArrayList<>();
        errors.addAll(prepareRoleCompositions());
        errors.addAll(validateRoleStorageConfiguration());
        errors.addAll(prepareDefaultParticipantBindings());
        errors.addAll(appParseErrors);
        errors.addAll(ruleBlock.getTechnicalServices().validate(this));
        for (var unit : mapsOfClass.values()) if (unit instanceof SppService service) {
            service.setSystemRequestServices(ruleBlock.isSystemServiceEnabled("idempotency"),
                    ruleBlock.isSystemServiceEnabled("duplicateGuard"));
            service.setSystemPartialUpdateEnabled(ruleBlock.isSystemServiceEnabled("partialUpdate"));
            service.setUpdateEntry(service.getServiceType() == IConstance.ServiceType.update
                    || (service instanceof SppScenario && hasUpdateCall(service.getServiceBody().getSppServiceCallList())));
        }
        errors.addAll(validateBusinessFieldMeanings());
        errors.addAll(bindParticipantCalls());
        errors.addAll(validateServiceContextDeclarations());
        errors.addAll(validateBusinessCalculations());
        errors.addAll(validateScenarioParticipants());
        errors.addAll(validateScenarioConditions());
        errors.addAll(validateScenarioCalls());
        errors.addAll(validateQuantumCallBounds());
        errors.addAll(validateScenarioContracts());
        errors.addAll(validateScenarioResults());
        errors.addAll(validateScenarioFailures());
        errors.addAll(validatePhysicalRuntimeDeclarations());
        errors.addAll(validateAppTableDeclarations());
        errors.addAll(validateAppMapperDeclarations());
        errors.addAll(validateHeaderDeclarations());
        errors.addAll(validateUniqueScopeServiceDeclarations());
        errors.addAll(validateTransactionResourceBindings());
        errors.addAll(validateTransactionResourceModes());
        errors.addAll(validateDeclaredTransactionGroups());
        errors.addAll(validateQuantumStoreDeclarations());
        errors.addAll(validateQuantumConservationDeclarations());
        errors.addAll(validateRemoteScopeDeclarations());
        errors.addAll(validateAppServiceImplementations());
        errors.addAll(validateBusinessIdempotencyDeclarations());
        errors.addAll(validateExampleDeclarations());
        errors.addAll(validateWebDeclarations());
        errors.addAll(validateRepositoryHydrationDeclarations());
        errors.addAll(validatePersistenceDeclarations());
        errors.addAll(validateTccResourceDeclarations());
        errors.addAll(validateCompensationDeclarations());
        errors.addAll(validateRemoteCompensationDeclarations());
        errors.addAll(validateCompensationSnapshotDeclarations());
        errors.addAll(validateSnapshotDeclarations());
        errors.addAll(validateSagaScopeDeclarations());
        errors.addAll(validateSagaTransactionCalls());
        errors.addAll(validateTransactionalIdempotencyHeaders());
        errors.addAll(validateCurrentSnapshotContexts());
        errors.addAll(validateEnabledStrictAppDeclarations());
        return errors;
    }

    private List<String> validateRoleStorageConfiguration() {
        List<String> errors = new ArrayList<>();
        var tables = ruleBlock.getAppTables().stream().flatMap(group -> group.getAppTableList().stream()).toList();
        for (var table : tables) {
            if (table.isIndependentIdentity()) {
                var keys = table.getAppColumns().stream().filter(com.servpp.lang.models.app.AppColumn::isPrimaryKey).toList();
                if ((table.getCategory() != com.servpp.lang.models.app.AppTables.Category.roles
                            && table.getCategory() != com.servpp.lang.models.app.AppTables.Category.entities)
                        || !table.getPrimaryKeys().isEmpty() || keys.size() != 1
                        || keys.get(0).isObjectBacked() || !"String".equals(keys.get(0).getType().getName()))
                    errors.add("Independent identity requires one APP-only String primary key on an entity or role table: " + table.getName());
            }
            if (table.getPerformerTable() != null) {
                if (table.getCategory() != com.servpp.lang.models.app.AppTables.Category.roles)
                    errors.add("Performer storage requires a role table: " + table.getName());
                if (!table.getPerformerTable().isBlank() && tables.stream().anyMatch(other -> other.getName().equalsIgnoreCase(table.getPerformerTable())))
                    errors.add("Role relationship table conflicts with business table: " + table.getPerformerTable());
                if (table.getPrimaryKeys().size() + table.getAppColumns().stream().filter(com.servpp.lang.models.app.AppColumn::isPrimaryKey).count() != 1)
                    errors.add("Configured performer storage currently requires one role primary key: " + table.getName());
            }
        }
        return errors;
    }

    private List<String> bindParticipantCalls() {
        List<String> errors = new ArrayList<>();
        for (var unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || unit instanceof SppRoleMapper) continue;
            if (service.isDeclaredQuantumService() && service.getServiceType() != IConstance.ServiceType.update)
                errors.add("Quantum services declare atomic updates and their constraints: " + service.getFuncName());
            // Legacy generated repository implementation interfaces are not SPP entrances.
            // Validate source-declared services, including explicitly bound default services.
            boolean internalDefault = service.getScopeItem() != null && service.getScopeItem().isImplicitDefaultBinding();
            for (var parameter : service.getServiceBody().getSppLocalVarList()) {
                if (parameter.isQueryKey() && (service.getType() != IConstance.CompilationUnitType.atomicservice
                        || service.getServiceType() != IConstance.ServiceType.query || parameter.getArrayDimension() != 0))
                    errors.add("key marks a scalar condition participant on an atomic query: " + service.getFuncName());
                if (internalDefault) continue;
                var type=parameter.getType();
                if ("ResultInfo".equals(parameter.getName()))
                    errors.add("ResultInfo is an implicit scenario system participant; do not declare its instance name in the entrance.");
                if (type instanceof SppClass model && model.isSystemBuiltin())
                    errors.add("System ResultInfo/FailureInfo participants are implicit and may only be used inside scenarios, never declared as service entrances.");
                if (type == null || type.getType() == null || !Set.of(IConstance.CompilationUnitType.role,
                        IConstance.CompilationUnitType.entity,IConstance.CompilationUnitType.contract,
                        IConstance.CompilationUnitType.reference).contains(type.getType()))
                    errors.add("Service " + service.getFuncName() + " entrance requires a participant, not " + (type==null?"unknown":type.getName()));
            }
            if (service instanceof SppScenario) bindParticipantCalls(service, service.getServiceBody().getSppServiceCallList(), errors);
        }
        return errors;
    }
    private void bindParticipantCalls(SppService owner, List<IServiceBodyStatement> statements, List<String> errors) {
        if(statements==null) return;
        for(var statement:statements) {
            if(statement instanceof SppServiceCall call) {
                if(call.getIdentityTarget()!=null) {
                    var participant=owner.getServiceBody().getQualifieField(call.getIdentityTarget());
                    if(participant==null || participant.getArrayDimension()>1 || participant.getType().getType()==null)
                        errors.add("identify requires a declared participant or one-dimensional collection: " + call.getIdentityTarget());
                } else if(call.getCallee()!=null) {
                    if(call.isImplicitArguments() && call.getSppParameterList().isEmpty()) {
                        for(var parameter:call.getCallee().getServiceBody().getSppLocalVarList()) {
                            var matches=owner.getServiceBody().getSppLocalVarList().stream().filter(p ->
                                    p.getType().getName().equals(parameter.getType().getName())
                                    && p.getArrayDimension()==parameter.getArrayDimension()).toList();
                            if(matches.size()!=1) errors.add("Participant binding missing or ambiguous in " + owner.getFuncName()
                                    + " calling " + call.getCallee().getFuncName() + ": " + parameter.getType().getName());
                            else call.addParameter(new SppParameter(matches.get(0).getType(), matches.get(0).getName()).setArrayDimension(matches.get(0).getArrayDimension()));
                        }
                    }
                    if(call.getSppParameterList().size()!=call.getCallee().getServiceBody().getSppLocalVarList().size())
                        errors.add("Participant count mismatch in " + owner.getFuncName() + " calling " + call.getCallee().getFuncName());
                    else for (int index = 0; index < call.getSppParameterList().size(); index++) {
                        var actual = call.getSppParameterList().get(index);
                        if (actual.getType() instanceof SppClass model && model.isSystemBuiltin())
                            errors.add("System result participants cannot be passed to atomic services.");
                        var formal = call.getCallee().getServiceBody().getSppLocalVarList().get(index);
                        if (actual.getType() == null || !actual.getType().getName().equals(formal.getType().getName())
                                || actual.getArrayDimension() != formal.getArrayDimension())
                            errors.add("Participant type mismatch in " + owner.getFuncName() + " calling " + call.getCallee().getFuncName()
                                    + " at argument " + (index + 1));
                    }
                    if(call.getGuardStatements()!=null && call.getCallee().getServiceType()!=IConstance.ServiceType.check)
                        errors.add("Only check/identify can guard a block: " + call.getCallee().getFuncName());
                }
                if (call.getGuardStatements() == null && (call.getIdentityTarget() != null
                        || call.getCallee() != null && call.getCallee().getServiceType() == IConstance.ServiceType.check)
                        && call.getParent() instanceof ExecuteBlock group && group.getScenarioType() == IConstance.ScenarioType.parallel)
                    errors.add("A bare check cannot end an unordered parallel block; place it in a serial group or use a guarded check block.");
                if(call.isBreaksTransaction()) {
                    boolean inTransaction=false;
                    for(var parent=call.getParent();parent!=null;parent=parent.getParent()) if(parent instanceof TransactionBlock) inTransaction=true;
                    if(!inTransaction) errors.add("breakTransction requires an enclosing transaction in " + owner.getFuncName());
                    if(call.getGuardStatements()!=null) errors.add("A guarded check cannot combine with breakTransction");
                    if(call.getCallee()!=null && call.getCallee().getServiceType()!=IConstance.ServiceType.check)
                        errors.add("breakTransction applies only to check/identify");
                }
            }
            for(var children:statementChildren(statement)) bindParticipantCalls(owner,children,errors);
        }
    }

    public List<String> validateBusinessCalculations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppService service && !(service instanceof SppScenario)
                    && service.getServiceBody() != null && service.getServiceBody().getSppServiceCallList() != null
                    && !service.getServiceBody().getSppServiceCallList().isEmpty()) {
                errors.add("Atomic service " + service.getFuncName() + " cannot call other services.");
            }
            if (unit instanceof SppService service && service.hasBusinessImplementation()) {
                errors.addAll(new com.servpp.lang.models.app.expr.BusinessCalculationValidator(service).validate());
            }
        }
        validateCalculationCycles(errors);
        return errors;
    }

    private void validateCalculationCycles(List<String> errors) {
        var done = Collections.newSetFromMap(new IdentityHashMap<SppService, Boolean>());
        var visiting = Collections.newSetFromMap(new IdentityHashMap<SppService, Boolean>());
        for (var unit : mapsOfClass.values()) if (unit instanceof SppService service && service.hasBusinessImplementation())
            visitCalculation(service, visiting, done, new ArrayList<>(), errors);
    }
    private void visitCalculation(SppService service, Set<SppService> visiting, Set<SppService> done,
                                  List<String> path, List<String> errors) {
        if (done.contains(service)) return;
        if (!visiting.add(service)) { errors.add("Calculation call cycle: " + String.join(" -> ", path) + " -> " + service.getFuncName()); return; }
        path.add(service.getFuncName());
        var calls = new ArrayList<com.servpp.lang.models.app.expr.CalculationServiceCall>();
        collectCalculationCalls(service.getDeclaredJavaStatements(), calls);
        for (var call : calls) if (call.callee != null) visitCalculation(call.callee, visiting, done, path, errors);
        path.remove(path.size() - 1); visiting.remove(service); done.add(service);
    }
    private void collectCalculationCalls(List<com.servpp.lang.models.app.expr.IJavaStatement> statements,
                                        List<com.servpp.lang.models.app.expr.CalculationServiceCall> calls) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationServiceCall call) calls.add(call);
            else if (statement instanceof com.servpp.lang.models.app.expr.CalculationControl control) collectCalculationCalls(control.children(), calls);
            else if (statement instanceof com.servpp.lang.models.app.expr.CalculationRule rule) {
                collectCalculationCalls(rule.getWhenTrue(), calls); collectCalculationCalls(rule.getWhenFalse(), calls);
            }
        }
    }

    public List<String> validateDeclaredTransactionGroups() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppScenario scenario) {
                validateDeclaredTransactionGroups(scenario, scenario.getServiceBody().getSppServiceCallList(), null, errors);
            }
        }
        return errors;
    }

    private void prepareSystemResults() {
        for (var unit : new ArrayList<>(mapsOfClass.values())) if (unit instanceof SppScenario scene && scene.isUsesSystemResult()) {
            for (var participant : scene.getServiceBody().getSppLocalVarList())
                if (participant.getType() instanceof SppClass contract && contract.getType() == IConstance.CompilationUnitType.contract) {
                    SystemResultModels.attach(contract, this);
                    if (getSppClass(contract.getName()) instanceof SppClass declaration && declaration != contract)
                        SystemResultModels.attach(declaration, this);
                }
            resolveFailureContracts(scene, scene.getServiceBody().getSppServiceCallList());
        }
    }
    private void resolveFailureContracts(SppScenario scene, List<IServiceBodyStatement> statements) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof TransactionFailureBlock block && block.getContractTarget() == null) {
                var contracts = scene.getServiceBody().getSppLocalVarList().stream()
                        .filter(p -> p.getArrayDimension() == 0 && p.getType().getType() == IConstance.CompilationUnitType.contract).toList();
                if (contracts.size() == 1) block.setContractTarget(contracts.get(0).getName());
            }
            for (var children : statementChildren(statement)) resolveFailureContracts(scene, children);
        }
    }
    private List<String> validateScenarioFailures() {
        List<String> errors=new ArrayList<>();
        for (SppCompilationUnit unit:mapsOfClass.values()) {
            if(unit instanceof SppScenario scene) { validateScenarioFailures(scene,scene.getServiceBody().getSppServiceCallList(),Set.of(),errors); }
        }
        return errors;
    }
    private void validateScenarioFailures(SppScenario scene,List<IServiceBodyStatement> statements,Set<String> active,List<String> errors) {
        if(statements==null) { return; }
        for(var statement:statements) {
            if (statement instanceof TransactionFailureBlock block) {
                var target = block.getContractTarget() == null ? null : scene.getServiceBody().getQualifieField(block.getContractTarget());
                if (target == null || target.getArrayDimension() != 0 || target.getType().getType() != IConstance.CompilationUnitType.contract)
                    errors.add("Scenario transaction fail requires one scalar contract; with multiple contracts use fail(ContractParticipant).");
                else if (target.getType() instanceof SppClass contract) {
                    SystemResultModels.attach(contract, this);
                    if (getSppClass(contract.getName()) instanceof SppClass declaration) SystemResultModels.attach(declaration, this);
                }
                validateScenarioFailures(scene, block.getExecuteCallList(), active, errors);
            } else if(statement instanceof TryBlock block) {
                var declared=getSppClass(block.getFailureType());
                var target=scene.getServiceBody().getQualifieField(block.getFailureTarget());
                block.setFailureModel(declared);
                boolean model=declared instanceof SppClass && !(declared instanceof SppService)
                        && (declared.getType()==IConstance.CompilationUnitType.contract || declared.getType()==IConstance.CompilationUnitType.role);
                boolean valid=model && target!=null && target.getArrayDimension()==0
                        && target.getType().getName().equals(block.getFailureType());
                if(!valid) { errors.add("Scenario catch requires an explicitly declared role/contract participant or attribute of the stated failure type."); }
                if(model) {
                    for(String name:List.of("code","message","kind","transactionOutcome")) {
                        var field=((SppClass)declared).getField(name);
                        if(field==null && !name.equals("code")) { continue; }
                        if(field==null || field.getArrayDimension()!=0 || !"String".equals(field.getType().getName())) {
                            errors.add("Scenario failure facts require String code; optional message, kind and transactionOutcome must also be scalar String fields.");
                        }
                    }
                    var suppressedCodes = ((SppClass) declared).getField("suppressedCodes");
                    if (suppressedCodes != null && (suppressedCodes.getArrayDimension() != 1
                            || suppressedCodes.getType() == null || !"String".equals(suppressedCodes.getType().getName()))) {
                        errors.add("Scenario failure facts optional suppressedCodes must be a one-dimensional String array.");
                    }
                }
                if(active.contains(block.getFailureTarget())) { errors.add("Nested scenario catch cannot reuse an active failure target."); }
                validateScenarioFailures(scene,block.getTryStatements(),active,errors);
                var caught=new HashSet<>(active);caught.add(block.getFailureTarget());
                validateScenarioFailures(scene,block.getCatchStatements(),caught,errors);
            } else if(statement instanceof ScenarioThrow rethrow) {
                if(!active.contains(rethrow.getTarget())) { errors.add("Scenario throw must refer to an enclosing catch failure participant."); }
            } else if(statement instanceof ExecuteBlock group) { validateScenarioFailures(scene,group.getExecuteCallList(),active,errors); }
            else if(statement instanceof TransactionBlock group) { validateScenarioFailures(scene,group.getTransactionCallList(),active,errors); }
        }
    }

    private List<String> validateScenarioResults() {
        List<String> errors=new ArrayList<>();
        for (SppCompilationUnit unit:mapsOfClass.values()) {
            if (unit instanceof SppScenario scene) { validateScenarioResults(scene,scene.getServiceBody().getSppServiceCallList(),errors); }
        }
        return errors;
    }
    private void validateScenarioResults(SppScenario scene,List<IServiceBodyStatement> statements,List<String> errors) {
        if (statements==null) { return; }
        for (IServiceBodyStatement statement:statements) {
            if (statement instanceof SppServiceCall call && call.getResultTarget()!=null) {
                SppService callee=call.getCallee();
                var result=callee.getReturns();
                if (result==null || result.getSppLocalVarList().size()!=1 || callee.getResponseSuffix()==null || callee.getResponseSuffix().isBlank()) {
                    errors.add("Scenario result binding for " + callee.getFuncName() + " requires exactly one declared business return value.");continue;
                }
                var target=scene.getServiceBody().getQualifieField(call.getResultTarget());
                if (!call.getResultTarget().contains(".") || target==null || !isSameTypeWithParameter(result.getSppLocalVarList().get(0),target)) {
                    errors.add("Scenario result binding " + call.getResultTarget() + " requires a participant attribute with the same declared type and collection dimension.");
                }
            } else if (statement instanceof ExecuteBlock group) {
                validateScenarioResults(scene,group.getExecuteCallList(),errors);
            } else if (statement instanceof TransactionBlock group) {
                validateScenarioResults(scene,group.getTransactionCallList(),errors);
            }
        }
    }

    public List<String> validateScenarioConditions() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppScenario scenario) {
                validateScenarioConditions(scenario, scenario.getServiceBody().getSppServiceCallList(), errors);
            }
        }
        return errors;
    }

    private void validateScenarioConditions(SppScenario scenario,
                                            List<IServiceBodyStatement> statements, List<String> errors) {
        if (statements == null) { return; }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof ConditionalBlock conditional) {
                errors.addAll(new com.servpp.lang.models.app.expr.BusinessCalculationValidator(scenario)
                        .validateCondition(conditional.getCondition()));
            }
            for (var children : statementChildren(statement)) validateScenarioConditions(scenario, children, errors);
        }
    }

    private void resolveServiceReferences() {
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppService service)
                resolveServiceReferences(service.getServiceBody().getSppServiceCallList());
        }
    }

    private void resolveServiceReferences(List<IServiceBodyStatement> statements) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall call && call.getCallee() != null
                    && mapsOfClass.get(call.getCallee().getName()) instanceof SppService canonical) {
                call.setCallee(canonical);
            }
            for (List<IServiceBodyStatement> children : statementChildren(statement)) resolveServiceReferences(children);
        }
    }

    private List<String> validateScenarioParticipants() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppScenario scene) {
                validateScenarioParticipants(scene, scene.getServiceBody().getSppServiceCallList(), errors);
            }
        }
        return errors;
    }

    private void validateScenarioParticipants(SppScenario scene, List<IServiceBodyStatement> statements, List<String> errors) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            List<String> paths = new ArrayList<>();
            if (statement instanceof SppServiceCall call && call.getCallee()!=null) {
                call.getSppParameterList().forEach(parameter -> paths.add(parameter.getName()));
                if (call.getResultTarget() != null) paths.add(call.getResultTarget());
            } else if (statement instanceof TryBlock block) {
                paths.add(block.getFailureTarget());
            } else if (statement instanceof ScenarioThrow rethrow) {
                paths.add(rethrow.getTarget());
            }
            for (String path : paths) {
                if (path == null || scene.getServiceBody().getQualifieField(path) == null) {
                    errors.add("Scenario " + scene.getFuncName() + ": '" + path
                            + "' must resolve to an explicitly declared participant or its declared attribute.");
                }
            }
            for (List<IServiceBodyStatement> children : statementChildren(statement))
                validateScenarioParticipants(scene, children, errors);
        }
    }

    public List<String> unusedParticipantWarnings() {
        List<String> warnings = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppService service) {
                warnings.addAll(UnusedParticipantWarnings.collect(service));
                if (ruleBlock.hasTransactionPolicy() && service instanceof SppScenario scene)
                    transactionPlanWarnings(scene,scene.getServiceBody().getSppServiceCallList(),warnings);
            }
        }
        for (String locale : ruleBlock.getSupportedLocales()) for (var constant : constants.values())
            if ("String".equals(constant.getType().getName())
                    && !ruleBlock.getConstantTranslations().getOrDefault(locale, Map.of()).containsKey(constant.getName()))
                warnings.add("Missing translation " + getName() + "." + locale + "." + constant.getName() + "; using SPP default.");
        return warnings.stream().sorted().toList();
    }

    private void transactionPlanWarnings(SppScenario scene,List<IServiceBodyStatement> statements,List<String> warnings) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof TransactionBlock block) {
                var modes = new java.util.LinkedHashSet<String>(); collectAppTransactionModes(block.getTransactionCallList(),modes);
                if (modes.size() > 1) {
                    var plan = new java.util.ArrayList<String>();
                    if (modes.remove("QUANTUM")) plan.add("QUANTUM"); plan.addAll(modes);
                    warnings.add("Scenario " + scene.getFuncName() + " mixed transaction: Saga stages " + String.join(" -> ",plan)
                            + "; quantum commits first; prepared updates may be reordered; REQUIRES_NEW is an independent barrier.");
                }
            }
            for (var children : statementChildren(statement)) transactionPlanWarnings(scene,children,warnings);
        }
    }

    public List<String> validateScenarioContracts() {
        resolveServiceReferences();
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppScenario scene
                    && hasAtomicUpdates(scene.getServiceBody().getSppServiceCallList())
                    && scene.getServiceBody().getSppLocalVarList().stream().noneMatch(parameter ->
                        parameter.getType() instanceof SppClass type && (type.getType() == IConstance.CompilationUnitType.contract
                            || type.getReferenceSourceKind() == IConstance.CompilationUnitType.contract))) {
                errors.add("Scenario " + scene.getFuncName() + " updates business state and requires an explicit Contract participant.");
            }
        }
        return errors;
    }

    private boolean hasAtomicUpdates(List<IServiceBodyStatement> statements) {
        if (statements == null) return false;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall call && call.getCallee() != null
                    && call.getCallee().getServiceType() == IConstance.ServiceType.update) return true;
            for (List<IServiceBodyStatement> children : statementChildren(statement)) if (hasAtomicUpdates(children)) return true;
        }
        return false;
    }

    public List<String> validateScenarioCalls() {
        resolveServiceReferences();
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppScenario scene) {
                validateScenarioCalls(scene, scene.getServiceBody().getSppServiceCallList(), errors);
            }
        }
        return errors;
    }

    private List<String> validateQuantumCallBounds() {
        List<String> errors = new ArrayList<>();
        for (var unit : mapsOfClass.values()) if (unit instanceof SppScenario scene)
            validateQuantumCallBounds(scene, scene.getServiceBody().getSppServiceCallList(), errors);
        return errors;
    }
    private void validateQuantumCallBounds(SppScenario scene, List<IServiceBodyStatement> statements, List<String> errors) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call && !call.getQuantumBounds().isEmpty()) {
                SppService target = call.getCallee();
                while (target != null && target.getLocalServiceProvider() != null) target = target.getLocalServiceProvider();
                String label = "Scenario " + scene.getFuncName() + " bounds: ";
                if (target == null || !target.isQuantumService() || target.getServiceType() != IConstance.ServiceType.update)
                    errors.add(label + "bounds may decorate only a quantum update call.");
                boolean transaction = false;
                for (var parent = statement.getParent(); parent != null; parent = parent.getParent())
                    if (parent instanceof TransactionBlock) transaction = true;
                if (!transaction) errors.add(label + "quantum bounds require an explicit transaction.");
                Set<String> seen = new HashSet<>();
                for (var bound : call.getQuantumBounds()) {
                    if (!seen.add(bound.path)) errors.add(label + "duplicate interval " + bound.path);
                    for (var argument : call.getSppParameterList()) {
                        String prefix = argument.getName() + ".";
                        if (!bound.path.startsWith(prefix)) continue;
                        var participant = scene.getServiceBody().getQualifieField(argument.getName());
                        if (participant == null || participant.getArrayDimension() > 1 || !(participant.getType() instanceof SppClass type)) continue;
                        String property = bound.path.substring(prefix.length());
                        var field = type.getField(property);
                        if (field == null || !field.isQuantum() || field.getArrayDimension() != 0) continue;
                        int argumentIndex = call.getSppParameterList().indexOf(argument);
                        if (target == null || target.getQuantumServiceAttributes().stream().noneMatch(attribute ->
                                attribute.getArgumentIndex() == argumentIndex && attribute.getPropertyName().equals(property))) {
                            errors.add(label + "interval must target a conserved attribute of this quantum call: " + bound.path);
                            continue;
                        }
                        bound.participant = participant; bound.participantPath = argument.getName(); bound.property = property;
                        break;
                    }
                    if (bound.participant == null) errors.add(label + "interval must target a quantum attribute of a passed participant: " + bound.path);
                    errors.addAll(new com.servpp.lang.models.app.expr.BusinessCalculationValidator(scene).validateBoundEndpoint(bound.minimum));
                    errors.addAll(new com.servpp.lang.models.app.expr.BusinessCalculationValidator(scene).validateBoundEndpoint(bound.maximum));
                }
            }
            for (var children : statementChildren(statement)) validateQuantumCallBounds(scene, children, errors);
        }
    }

    private void validateScenarioCalls(SppScenario owner, List<IServiceBodyStatement> statements, List<String> errors) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall call && call.getCallee() instanceof SppScenario callee) {
                errors.add("Scenario " + owner.getFuncName() + " cannot call "
                        + "Scenario " + callee.getFuncName()
                        + "; compose shared atomic services instead.");
            }
            for (List<IServiceBodyStatement> children : statementChildren(statement))
                validateScenarioCalls(owner, children, errors);
        }
    }

    private List<List<IServiceBodyStatement>> statementChildren(IServiceBodyStatement statement) {
        if (statement instanceof SppServiceCall call && call.getGuardStatements()!=null) return List.of(call.getGuardStatements());
        if (statement instanceof TryBlock block) return List.of(block.getTryStatements(), block.getCatchStatements());
        if (statement instanceof ExecuteBlock block) return List.of(block.getExecuteCallList() == null ? List.of() : block.getExecuteCallList());
        if (statement instanceof TransactionBlock block) return List.of(block.getTransactionCallList() == null ? List.of() : block.getTransactionCallList());
        return List.of();
    }

    private void validateDeclaredTransactionGroups(SppScenario scenario,
                                                    List<IServiceBodyStatement> statements, String inheritedMode, List<String> errors) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionFailureBlock failureBlock) {
                if (scenario.getScenarioType() == IConstance.ScenarioType.parallel)
                    errors.add("Scenario " + scenario.getFuncName() + " transaction fail v1 requires serial failure disposition.");
                for (ServiceBaseBody parent = failureBlock.getParent(); parent != null; parent = parent.getParent())
                    if (parent instanceof ExecuteBlock group && group.getScenarioType() == IConstance.ScenarioType.parallel) {
                        errors.add("Scenario " + scenario.getFuncName() + " transaction fail v1 cannot share system results across parallel branches.");
                        break;
                    } else if (parent instanceof TransactionBlock) {
                        errors.add("Scenario " + scenario.getFuncName() + " transaction fail v1 requires an outermost transaction boundary.");
                        break;
                    }
            }
            if (statement instanceof TransactionBlock group) {
                if (ruleBlock.hasTransactionPolicy() && "NESTED".equals(group.getPropagation()))
                    errors.add("Scenario " + scenario.getFuncName() + " NESTED propagation is unsupported by transaction storage v1; use REQUIRED or REQUIRES_NEW.");
                Set<String> modes = new java.util.LinkedHashSet<>();
                collectAppTransactionModes(group.getTransactionCallList(), modes);
                group.setSourcePlan(ruleBlock.hasTransactionPolicy());
                group.setParallelOrdinaryUpdates(ruleBlock.isParallelOrdinaryUpdates());
                if (group.isSourcePlan() && inheritedMode != null && "REQUIRES_NEW".equals(group.getPropagation())
                        && (!modes.isEmpty() && !(modes.size() == 1 && (modes.contains("AT") || modes.contains("LOCAL") || modes.contains("XA") || modes.contains("QUANTUM")))))
                    errors.add("Scenario " + scenario.getFuncName() + " nested REQUIRES_NEW v1 supports homogeneous AT or QUANTUM child only; mixed/SAGA child requires an unsupported propagation protocol.");
                if (!group.isSourcePlan() && modes.size() > 1) errors.add("Scenario " + scenario.getFuncName()
                        + " transaction has incompatible APP modes " + modes + ".");
                if (group.isSourcePlan() && modes.size() > 1 && !staticUpdatePlan(group.getTransactionCallList()))
                    errors.add("Scenario " + scenario.getFuncName() + " mixed transaction v1 supports prepared updates and post-settlement calculations; move query/check before the boundary.");
                if (group.isSourcePlan() && containsIndependentBoundary(group.getTransactionCallList())
                        && (!modes.isEmpty() && !modes.equals(java.util.Set.of("AT")) && !modes.equals(java.util.Set.of("LOCAL"))))
                    errors.add("Scenario " + scenario.getFuncName() + " transaction v1 supports nested REQUIRES_NEW only in a homogeneous AT parent; mixed/quantum parents are unsupported.");
                if (group.isSourcePlan() && modes.size() > 1) {
                    var flattened = new ArrayList<SppServiceCall>();
                    flattenPreparedCalls(group.getTransactionCallList(), flattened);
                    boolean calculationSeen = false;
                    for (var call : flattened) {
                        var callee = call.getCallee();
                        if (callee == null) continue;
                        if (callee.getServiceType() == IConstance.ServiceType.calculate) calculationSeen = true;
                        if (calculationSeen && "QUANTUM".equals(callee.getTransactionModeName())
                                && callee.getServiceType() == IConstance.ServiceType.update)
                            errors.add("Scenario " + scenario.getFuncName() + " quantum input preparation must be outside the transaction; mixed boundary calculations must follow all quantum calls.");
                    }
                    var plannedModes = new java.util.ArrayList<>(modes);
                    plannedModes.remove("QUANTUM");
                    for (var call : flattened) {
                        var callee = call.getCallee();
                        if (callee == null || callee.getServiceType() != IConstance.ServiceType.update || "QUANTUM".equals(callee.getTransactionModeName())) continue;
                        var implementation = callee;
                        while (implementation.getLocalServiceProvider() != null) implementation = implementation.getLocalServiceProvider();
                        if (implementation.hasBusinessImplementation())
                            errors.add("Scenario " + scenario.getFuncName() + " mixed transaction requires prepared ordinary writes; move calculation from update " + implementation.getFuncName() + " before the boundary.");
                        if (plannedModes.indexOf(callee.getTransactionModeName()) < plannedModes.size() - 1
                                && (callee.getScopeItem() == null || !callee.getScopeItem().hasCompensationServices()))
                            errors.add("Scenario " + scenario.getFuncName() + " committed ordinary stage " + callee.getFuncName() + " requires explicit compensation before a later transaction mode; compensation business semantics remain an open design item.");
                    }
                }
                group.setDeclaredMode(modes.isEmpty()
                        ? (inheritedMode != null && !"REQUIRES_NEW".equals(group.getPropagation()) ? inheritedMode : "AT")
                        : group.isSourcePlan() && modes.size() > 1 ? "SAGA" : modes.iterator().next());
                if (!group.getHasTransactionCall()) errors.add("Scenario " + scenario.getFuncName()
                        + " declares an empty transaction " + group.getDeclaredMode() + " group.");
                validateTransactionMembers(scenario, group.getTransactionCallList(), group.isSourcePlan() ? null : group.getDeclaredMode(), errors);
            }
            for (List<IServiceBodyStatement> children : statementChildren(statement))
                validateDeclaredTransactionGroups(scenario, children, statement instanceof TransactionBlock group ? group.getDeclaredMode() : inheritedMode, errors);
        }
    }

    private boolean containsIndependentBoundary(List<IServiceBodyStatement> statements) {
        for (var statement : statements) {
            if (statement instanceof TransactionBlock block && "REQUIRES_NEW".equals(block.getPropagation())) return true;
            for (var children : statementChildren(statement)) if (containsIndependentBoundary(children)) return true;
        }
        return false;
    }

    private void flattenPreparedCalls(List<IServiceBodyStatement> statements, List<SppServiceCall> calls) {
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call) calls.add(call);
            else if (statement instanceof TransactionBlock block && !"REQUIRES_NEW".equals(block.getPropagation()))
                flattenPreparedCalls(block.getTransactionCallList(), calls);
        }
    }

    private boolean staticUpdatePlan(List<IServiceBodyStatement> statements) {
        for (var statement : statements) {
            if (statement instanceof TransactionBlock nested) {
                if (!staticUpdatePlan(nested.getTransactionCallList())) return false;
            } else if (!(statement instanceof SppServiceCall call) || call.getCallee() == null
                    || !List.of(IConstance.ServiceType.update,IConstance.ServiceType.calculate).contains(call.getCallee().getServiceType()) || call.getGuardStatements() != null && !call.getGuardStatements().isEmpty()) return false;
        }
        return true;
    }

    private void collectAppTransactionModes(List<IServiceBodyStatement> statements, Set<String> modes) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionBlock block && "REQUIRES_NEW".equals(block.getPropagation())) continue;
            if (statement instanceof SppServiceCall call && call.getCallee() != null
                    && call.getCallee().getServiceType() == IConstance.ServiceType.update)
                modes.add(call.getCallee().getTransactionModeName());
            for (List<IServiceBodyStatement> children : statementChildren(statement)) collectAppTransactionModes(children, modes);
        }
    }

    private void validateTransactionMembers(SppScenario owner, List<IServiceBodyStatement> statements,
                                            String mode, List<String> errors) {
        if (statements == null) return;
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionBlock block && "REQUIRES_NEW".equals(block.getPropagation())) continue;
            if ((statement instanceof ConditionalBlock || statement instanceof TryBlock
                    || statement instanceof SppServiceCall checkCall && (checkCall.getIdentityTarget() != null
                        || checkCall.getCallee() != null && checkCall.getCallee().getServiceType() == IConstance.ServiceType.check))
                    && mode != null && !List.of("AT", "LOCAL", "XA", "QUANTUM").contains(mode)) {
                errors.add("Scenario " + owner.getFuncName() + " conditional transaction supports AT or QUANTUM, not " + mode + ".");
            }
            if (statement instanceof SppServiceCall call && call.getCallee() != null) {
                SppService callee = call.getCallee();
                if (callee instanceof SppScenario) errors.add("Scenario " + owner.getFuncName()
                        + " transaction " + mode + " may contain only atomic services, not " + callee.getFuncName() + ".");
                boolean modeBound = callee.getServiceType() == IConstance.ServiceType.update
                        || callee.isDeclaredQuantumService() || (callee.getScopeItem() != null
                        && !callee.getScopeItem().getTransactionModifierNames().isEmpty());
                if (modeBound && mode != null && !mode.equals(callee.getTransactionModeName())) errors.add("Scenario "
                        + owner.getFuncName() + " transaction " + mode + " conflicts with service "
                        + callee.getFuncName() + " mode " + callee.getTransactionModeName() + ".");
            }
            if (statement instanceof ExecuteBlock block && block.getScenarioType() == IConstance.ScenarioType.parallel)
                errors.add("Scenario " + owner.getFuncName() + " transaction does not support parallel subgroups.");
            for (List<IServiceBodyStatement> children : statementChildren(statement))
                validateTransactionMembers(owner, children, mode, errors);
        }
    }

    public List<String> validatePhysicalRuntimeDeclarations() {
        List<String> errors = new ArrayList<>();
        for (String duplicateBackendName : ruleBlock.getDuplicatePhysicalBackendNames()) {
            errors.add("PHY backend " + duplicateBackendName + " is declared multiple times.");
        }
        for (String duplicateRemoteAdapterName : ruleBlock.getDuplicateRemoteAdapterNames()) {
            errors.add("PHY remote adapter for " + duplicateRemoteAdapterName + " is declared multiple times.");
        }
        for (String duplicateRepositoryAdapterName : ruleBlock.getDuplicateRepositoryAdapterNames()) {
            errors.add("PHY repository adapter " + duplicateRepositoryAdapterName + " is declared multiple times.");
        }
        for (String duplicateQuantumStoreAdapterName : ruleBlock.getDuplicateQuantumStoreAdapterNames()) {
            errors.add("PHY quantum store adapter " + duplicateQuantumStoreAdapterName
                    + " is declared multiple times.");
        }
        for (String duplicateConfigurationKey : ruleBlock.getDuplicatePhysicalConfigurationKeys()) {
            errors.add("PHY config " + duplicateConfigurationKey + " is declared multiple times.");
        }
        validateNumericBindings(errors);
        validatePhysicalConfiguration(errors);
        validateUniquePhysicalBindingAssignments(errors);
        validatePhysicalBackendBindingCoverage(errors);
        validateRemoteAdapterCoverage(errors);
        if (hasPhysicalRuntimeDeclarations()) {
            Set<String> backendReferencedQuantumStores = backendReferencedQuantumStores();
            for (AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
                if (!hasQuantumStoreAdapter(store.getName())
                        && !backendReferencedQuantumStores.contains(store.getName())) {
                    errors.add("PHY quantum store adapter " + store.getName()
                            + " must define the physical implementation for APP quantum store "
                            + store.getName() + ".");
                }
            }
        }
        for (AppPhysicalBackend backend : ruleBlock.getAppPhysicalBackends()) {
            String backendName = backend.getName();
            String adapterName = backend.getAdapterName() == null ? backendName : backend.getAdapterName();
            if (!isSupportedPhysicalBackendAdapter(adapterName)) {
                if (backend.getAdapterName() == null) {
                    errors.add("Unsupported PHY backend " + backendName
                            + "; supported backend adapters are localJdbc, local-jdbc, and seata. "
                            + "Use adapter <name> when the deployment backend name is custom.");
                } else {
                    errors.add("Unsupported PHY backend adapter " + adapterName + " on backend " + backendName
                            + "; supported backend adapters are localJdbc, local-jdbc, and seata.");
                }
            } else if (isLocalJdbcBackendAdapter(adapterName)) {
                validateLocalJdbcBackendConfig(backend, errors);
            } else if ("seata".equals(adapterName)) {
                validateSeataBackendConfig(backend, errors);
            }
            validateSupportedTransactionsBackendConfig(backend, errors);
            for (String duplicateBindingKey : backend.getDuplicateBindingKeys()) {
                errors.add("PHY backend " + backendName
                        + " declares duplicate binding " + duplicateBindingKey + ".");
            }
            for (String duplicateConfigurationKey : backend.getDuplicateConfigurationKeys()) {
                errors.add("PHY backend " + backendName
                        + " declares duplicate config " + duplicateConfigurationKey + ".");
            }
            for (String duplicateQuantumStoreReference : backend.getDuplicateQuantumStoreReferences()) {
                errors.add("PHY backend " + backendName
                        + " declares duplicate " + duplicateQuantumStoreReference + " reference.");
            }
            String quantumStoreName = backendQuantumStoreName(backend);
            if (quantumStoreName != null && !hasQuantumStore(quantumStoreName)) {
                errors.add("PHY backend " + backendName
                        + " quantumStore references undefined quantum store " + quantumStoreName + ".");
            } else if (quantumStoreName != null && !hasQuantumStoreAdapter(quantumStoreName)) {
                errors.add("PHY backend " + backendName
                        + " quantumStore " + quantumStoreName
                        + " has no PHY quantumStore adapter definition.");
            }
            for (AppPhysicalBinding binding : backend.getBindings()) {
                validatePhysicalBinding(backendName, binding, errors);
            }
        }
        for (AppRemoteAdapter adapter : ruleBlock.getAppRemoteAdapters()) {
            if (!hasRemoteService(adapter.getName())) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " must reference a remote service declared in APP scope.");
            }
            if ("httpJson".equals(adapter.getAdapterName())) {
                validateHttpJsonRemoteAdapterConfig(adapter, errors);
            } else if (isObjectRepositoryRemoteAdapter(adapter.getAdapterName())) {
                validateObjectRepositoryRemoteAdapterConfig(adapter, errors);
            }
            validateRemoteRoleAdapterConfig(adapter, errors);
            validateRemoteCompensationPolicyConfig(adapter, errors);
            for (String duplicateConfigurationKey : adapter.getDuplicateConfigurationKeys()) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " declares duplicate config " + duplicateConfigurationKey + ".");
            }
        }
        for (AppRepositoryAdapter adapter : ruleBlock.getAppRepositoryAdapters()) {
            if (!hasAppRepositoryAccessDeclarations() && !isReferencedByObjectRepositoryRemote(adapter.getName())) {
                errors.add("PHY repository adapter " + adapter.getName()
                        + " declares adapter " + adapter.getAdapterName()
                        + " but APP scope declares no repository hydration or object snapshot rules.");
            }
            if (!isSupportedRepositoryAdapter(adapter.getAdapterName())) {
                errors.add("Unsupported PHY repository adapter " + adapter.getAdapterName()
                        + " on repository " + adapter.getName()
                        + "; supported repository adapters are objectSnapshot, objectRepository,"
                        + " jdbcObjectRepository and jdbc-object-repository.");
            } else if ("objectSnapshot".equals(adapter.getAdapterName())) {
                validateOptionalRepositoryConfig(adapter, errors, "datasourceUrl");
                validateOptionalRepositoryConfig(adapter, errors, "driverClassName");
                validateOptionalRepositoryConfig(adapter, errors, "table");
                validateRepositoryDatasourceDriver(adapter, errors);
                validateTransactionRetentionRepositoryConfig(adapter, errors);
            } else if ("objectRepository".equals(adapter.getAdapterName())) {
                validateOptionalRepositoryConfig(adapter, errors, "implementationClass");
            } else if (isJdbcObjectRepositoryAdapter(adapter.getAdapterName())) {
                validateOptionalRepositoryConfig(adapter, errors, "datasourceUrl");
                validateOptionalRepositoryConfig(adapter, errors, "driverClassName");
                validateOptionalRepositoryConfig(adapter, errors, "table");
                validateBooleanRepositoryConfig(adapter, errors, "appDdlTables");
                validateRepositoryDatasourceDriver(adapter, errors);
                validateTransactionRetentionRepositoryConfig(adapter, errors);
            }
            validateBooleanRepositoryConfig(adapter, errors, "default");
            for (String duplicateConfigurationKey : adapter.getDuplicateConfigurationKeys()) {
                errors.add("PHY repository adapter " + adapter.getName()
                        + " declares duplicate config " + duplicateConfigurationKey + ".");
            }
        }
        validateDefaultRepositoryAdapter(errors);
        validateRepositoryHydrationPhysicalBindings(errors);
        validateObjectSnapshotPhysicalBindings(errors);
        for (AppQuantumStoreAdapter adapter : ruleBlock.getAppQuantumStoreAdapters()) {
            if (!hasQuantumStore(adapter.getName())) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " must reference an APP quantum store.");
            }
            if (!isSupportedQuantumStoreAdapter(adapter.getAdapterName())) {
                errors.add("Unsupported PHY quantum store adapter " + adapter.getAdapterName()
                        + " on quantum store " + adapter.getName()
                        + "; supported quantum store adapters are jdbcQuantum, jdbc-quantum,"
                        + " tccObjectStore and quantumService.");
            } else if (isJdbcQuantumStoreAdapter(adapter.getAdapterName())) {
                validateRequiredQuantumStoreConfig(adapter, errors, "datasourceUrl");
                validateRequiredQuantumStoreConfig(adapter, errors, "driverClassName");
                validateRequiredQuantumStoreConfig(adapter, errors, "journalStore");
                validateRequiredQuantumStoreConfig(adapter, errors, "journalTable");
                validateRequiredQuantumStoreConfig(adapter, errors, "valueTable");
                Optional<String> decimalStorage = configValueForStaticValidation(adapter.getConfiguration().get("decimalStorage"));
                if (decimalStorage.isPresent() && !java.util.Set.of("decimal", "text").contains(decimalStorage.get())) {
                    errors.add("PHY quantum store adapter " + adapter.getName() + " has unsupported decimalStorage " + decimalStorage.get());
                }
                validateSupportedQuantumStoreConfig(adapter, errors, "journalStore",
                        JDBC_QUANTUM_JOURNAL_STORE);
            } else if ("quantumService".equals(adapter.getAdapterName())) {
                validateQuantumServiceAdapterConfig(adapter, errors);
                validateQuantumServiceProtocol(adapter, errors);
            }
            validateQuantumDefinitions(adapter, errors);
            validateQuantumImplementationStrategy(adapter, errors);
            validateQuantumAccessMode(adapter, errors);
            validateQuantumIdempotencyMode(adapter, errors);
            validateQuantumReversalMode(adapter, errors);
            if (isSupportedQuantumStoreAdapter(adapter.getAdapterName())) {
                validateQuantumAccessContractConsistency(adapter, errors);
                validateTccObjectStoreBackendBinding(adapter, errors);
            }
            for (String duplicateConfigurationKey : adapter.getDuplicateConfigurationKeys()) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " declares duplicate config " + duplicateConfigurationKey + ".");
            }
        }
        validateQuantumImplementationStrategyExclusivity(errors);
        if (ruleBlock.hasTransactionPolicy()) validateSourcePlanPhysicalCapabilities(errors);
        return errors;
    }

    private void validateSourcePlanPhysicalCapabilities(List<String> errors) {
        var backends = ruleBlock.getAppPhysicalBackends();
        boolean localJdbcBackends = !backends.isEmpty() && backends.stream().allMatch(backend ->
                List.of("localJdbc", "local-jdbc").contains(backend.getAdapterName()));
        var quantumBackends = backends.stream().filter(backend -> backend.getQuantumStoreName() != null
                || backend.getBindings().stream().anyMatch(binding -> binding.getKind()
                    == com.servpp.lang.models.app.AppPhysicalBinding.Kind.quantum)).toList();
        boolean jdbcQuantumStores = !ruleBlock.getAppQuantumStoreAdapters().isEmpty()
                && ruleBlock.getAppQuantumStoreAdapters().stream().allMatch(adapter -> isJdbcQuantumStoreAdapter(adapter.getAdapterName()));
        // Ordinary distributed backends do not determine the independent quantum store's capability.
        boolean centralizedQuantumJdbc = jdbcQuantumStores && !quantumBackends.isEmpty()
                && quantumBackends.stream().allMatch(backend ->
                    List.of("localJdbc", "local-jdbc").contains(backend.getAdapterName()));
        for (var unit : mapsOfClass.values()) {
            if (!(unit instanceof SppScenario scene)) continue;
            validateSourcePlanPhysicalCapabilities(scene,scene.getServiceBody().getSppServiceCallList(),
                    centralizedQuantumJdbc,localJdbcBackends,errors);
        }
    }
    private void validateSourcePlanPhysicalCapabilities(SppScenario scene, List<IServiceBodyStatement> statements,
                                                        boolean centralizedQuantumJdbc, boolean localJdbcBackends,
                                                        List<String> errors) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof TransactionBlock group) {
                var modes = new java.util.LinkedHashSet<String>();
                collectAppTransactionModes(group.getTransactionCallList(),modes);
                var calls = new ArrayList<SppServiceCall>(); flattenPreparedCalls(group.getTransactionCallList(),calls);
                for (var call : calls) if (call.getCallee() != null && call.getCallee().getServiceType() == IConstance.ServiceType.update
                        && call.getCallee().getScopeItem() != null && !call.getCallee().getScopeItem().isLocal())
                    errors.add("Scenario " + scene.getFuncName() + " transaction storage v1 cannot enlist remote update "
                            + call.getCallee().getFuncName() + " without a supported transaction/recovery protocol; local deployment is the default implementation.");
                if (!centralizedQuantumJdbc && modes.contains("QUANTUM"))
                    errors.add("Scenario " + scene.getFuncName() + " transaction storage v1 quantum batching/reversal requires a centralized JDBC quantum store with localJdbc backend; external quantum/TCC propagation requires a supported protocol.");
                if (!localJdbcBackends && containsIndependentBoundary(group.getTransactionCallList()))
                    errors.add("Scenario " + scene.getFuncName() + " transaction storage v1 REQUIRES_NEW requires localJdbc backends; native coordinator suspension requires a supported propagation protocol.");
            }
            for (var children : statementChildren(statement))
                validateSourcePlanPhysicalCapabilities(scene,children,centralizedQuantumJdbc,localJdbcBackends,errors);
        }
    }

    private void validateNumericBindings(List<String> errors) {
        var bindings = ruleBlock.getNumericBindings();
        Set<String> targets = new LinkedHashSet<>();
        for (var binding : bindings) {
            if (!targets.add(com.servpp.lang.models.app.NumericBinding.canonical(binding.target()))) {
                errors.add("PHY numeric target " + binding.target() + " is declared multiple times.");
            }
            try { binding.javaType(); com.servpp.lang.models.app.NumericBinding.dimensions(binding.target()); } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
            if (!com.servpp.lang.models.app.NumericBinding.isNumeric(binding.target())) {
                String[] path = binding.target().split("\\.");
                SppCompilationUnit current = mapsOfClass.get(path[0]);
                SppField field = null;
                for (int i = 1; i < path.length && current instanceof SppClass; i++) {
                    field = ((SppClass) current).getField(path[i]);
                    current = field == null ? null : field.getType();
                }
                if (path.length != 2 || field == null || field.getArrayDimension() != 0 || current == null
                        || !com.servpp.lang.models.app.NumericBinding.isNumeric(current.getName())) {
                    errors.add("PHY numeric target " + binding.target() + " must reference an existing numeric field.");
                }
            }
        }
        for (int i = 0; i < bindings.size(); i++) for (int j = i + 1; j < bindings.size(); j++) {
            var left = bindings.get(i); var right = bindings.get(j);
            if (left.isGlobal() && right.isGlobal() && (left.matches(right.target()) || right.matches(left.target())))
                errors.add("Overlapping PHY numeric replacements: " + left.target() + " and " + right.target());
        }
        if (!errors.isEmpty()) { return; }
        for (SppConstant constant : constants.values()) applyNumericBinding(constant, null);
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppClass cls) {
                for (SppField field : cls.getSppFieldList()) {
                    applyNumericBinding(field, cls.getName() + "." + field.getName());
                }
            }
            if (unit instanceof SppService service) {
                if (service.hasBusinessImplementation()) {
                    applyCalculationBindings(service.getDeclaredJavaStatements());
                    List<String> businessErrors = new com.servpp.lang.models.app.expr.BusinessCalculationValidator(service).validate();
                    if (!businessErrors.isEmpty()) { errors.addAll(businessErrors); continue; }
                }
                for (SppLocalVar parameter : service.getServiceBody().getSppLocalVarList()) {
                    applyNumericBinding(parameter, null);
                    if (parameter.getType() instanceof SppClass parameterClass) {
                        applyNumericClassBindings(parameterClass, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
                    }
                }
                for (SppLocalVar parameter : service.getReturns().getSppLocalVarList()) {
                    applyNumericBinding(parameter, null);
                    if (parameter.getType() instanceof SppClass parameterClass) {
                        applyNumericClassBindings(parameterClass, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
                    }
                }
                service.setNumericGeneration(true);
                try { service.getJavaStatements(); }
                catch (IllegalArgumentException e) {
                    errors.add("PHY numeric implementation for " + service.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void applyNumericClassBindings(SppClass cls, Set<SppClass> visiting) {
        if (!visiting.add(cls)) { return; }
        for (SppField field : cls.getSppFieldList()) {
            applyNumericBinding(field, cls.getName() + "." + field.getName());
            if (field.getType() instanceof SppClass nested) { applyNumericClassBindings(nested, visiting); }
        }
    }

    private void applyCalculationBindings(List<com.servpp.lang.models.app.expr.IJavaStatement> statements) {
        for (var statement : statements) {
            if (statement instanceof com.servpp.lang.models.app.expr.CalculationControl control) {
                if (control.local != null) { applyNumericBinding(control.local, null); }
                applyCalculationBindings(control.children());
            } else if (statement instanceof com.servpp.lang.models.app.expr.CalculationRule rule) {
                applyCalculationBindings(rule.getWhenTrue()); applyCalculationBindings(rule.getWhenFalse());
            }
        }
    }

    public void applyCalculationNumericBinding(SppParameter field) { applyNumericBinding(field, null); }

    private void applyNumericBinding(SppParameter field, String path) {
        field.setPhysicalJavaType(null);
        field.setNumericAdapter(null);
        if (field.getType() == null || !com.servpp.lang.models.app.NumericBinding.isNumeric(field.getType().getName())) {
            return;
        }
        String original = field.getDeclaredNumericSpecification();
        String specification = original;
        String implementation = new com.servpp.lang.models.app.NumericBinding("", original).javaType();
        String numericAdapter = null;
        for (var binding : ruleBlock.getNumericBindings()) {
            if (binding.matches(original)) { implementation = binding.javaType(); numericAdapter = binding.adapter(); specification = binding.representation(); }
        }
        for (var binding : ruleBlock.getNumericBindings()) {
            if (binding.target().equals(path)) { implementation = binding.javaType(); numericAdapter = binding.adapter(); specification = binding.representation(); }
        }
        field.setPhysicalJavaType(implementation);
        field.setNumericAdapter(numericAdapter);
        field.setPhysicalNumericSpecification(specification);
    }

    private void validatePhysicalConfiguration(List<String> errors) {
        for (Map.Entry<String, String> entry : ruleBlock.getPhysicalConfiguration().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("requestIdempotencyAdapter".equals(key) || "domainRpcToken".equals(key)) {
                Optional<String> adapter = configValueForStaticValidation(value);
                if (adapter.isPresent() && adapter.get().isBlank()) errors.add("PHY requestIdempotencyAdapter must not be blank.");
                continue;
            }
            if (!strictAppDeclarationConfigKeys().contains(key)) {
                errors.add("Unsupported PHY config " + key + ".");
                continue;
            }
            Optional<String> validationValue = configValueForStaticValidation(value);
            if (validationValue.isPresent()
                    && !Set.of("true", "false").contains(validationValue.get().trim())) {
                errors.add("PHY config " + key + " must be true or false.");
            }
        }
    }

    private List<String> validateEnabledStrictAppDeclarations() {
        List<String> errors = new ArrayList<>();
        if (strictAppDeclarationEnabled("strictRoleOnlyBusinessModel")) {
            errors.addAll(validateRoleOnlyBusinessModelDeclarations());
        }
        if (strictAppDeclarationEnabled("strictRoleOnlyServiceBoundary")) {
            errors.addAll(validateBlockedRoleOnlyServiceBoundaryDeclarations());
        }
        if (strictAppDeclarationEnabled("strictAppDdlReplacement")) {
            errors.addAll(validateAppDdlReplacementDeclarations());
        }
        if (strictAppDeclarationEnabled("strictQuantumServiceInternalProtocol")) {
            errors.addAll(validateQuantumServiceInternalProtocolDeclarations());
        }
        if (strictAppDeclarationEnabled("strictQuantumServiceExternalImplementation")) {
            errors.addAll(validateQuantumServiceExternalImplementationDeclarations());
        }
        if (strictAppDeclarationEnabled("strictExternalRemoteAdapter")) {
            errors.addAll(validateExternalRemoteAdapterDeclarations());
        }
        if (strictAppDeclarationEnabled("strictRemoteExternalEndpoint")) {
            errors.addAll(validateRemoteExternalEndpointDeclarations());
        }
        if (strictAppDeclarationEnabled("strictPlatformColumnBusinessShadow")) {
            errors.addAll(validatePlatformColumnBusinessShadowDeclarations());
        }
        if (strictAppDeclarationEnabled("strictAppDdlPlatformPrimaryKey")) {
            errors.addAll(validateAppDdlPlatformPrimaryKeyDeclarations());
        }
        if (strictAppDeclarationEnabled("strictCurrentSnapshotAppDdlKey")) {
            errors.addAll(validateCurrentSnapshotAppDdlKeyDeclarations());
        }
        return List.copyOf(errors);
    }

    private boolean strictAppDeclarationEnabled(String key) {
        if (ruleBlock.getPhysicalConfiguration().containsKey(key)) {
            return physicalConfigEnabled(key);
        }
        return physicalConfigEnabled("strictAppMigration");
    }

    private boolean physicalConfigEnabled(String key) {
        return configValueForStaticValidation(ruleBlock.getPhysicalConfiguration().get(key))
                .map(String::trim)
                .filter("true"::equals)
                .isPresent();
    }

    private Set<String> strictAppDeclarationConfigKeys() {
        return Set.of(
                "strictAppMigration",
                "strictRoleOnlyBusinessModel",
                "strictRoleOnlyServiceBoundary",
                "strictAppDdlReplacement",
                "strictQuantumServiceInternalProtocol",
                "strictQuantumServiceExternalImplementation",
                "strictExternalRemoteAdapter",
                "strictRemoteExternalEndpoint",
                "strictPlatformColumnBusinessShadow",
                "strictAppDdlPlatformPrimaryKey",
                "strictCurrentSnapshotAppDdlKey");
    }

    public List<String> validatePhysicalRuntimeCatalogGeneration() {
        List<String> errors = new ArrayList<>();
        for (AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
            if (!hasQuantumStoreAdapter(store.getName())) {
                errors.add("PHY quantum store adapter " + store.getName()
                        + " must define the physical implementation for APP quantum store "
                        + store.getName()
                        + " before generating physical runtime catalog.");
            }
        }
        validateQuantumImplementationStrategyExclusivity(errors);
        if (ruleBlock.hasTransactionPolicy()) validateSourcePlanPhysicalCapabilities(errors);
        return errors;
    }

    private boolean hasAppRepositoryAccessDeclarations() {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                if (scopeItem.hasRepositoryHydrationRules() || scopeItem.hasObjectSnapshotRules()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReferencedByObjectRepositoryRemote(String repositoryName) {
        for (AppRemoteAdapter adapter : ruleBlock.getAppRemoteAdapters()) {
            if (!isObjectRepositoryRemoteAdapter(adapter.getAdapterName())) {
                continue;
            }
            Optional<String> remoteRepositoryName = configValueForStaticValidation(
                    adapter.getConfiguration().get("repository"));
            if (remoteRepositoryName.isPresent() && repositoryName.equals(remoteRepositoryName.get())) {
                return true;
            }
        }
        return false;
    }

    private void validateRepositoryHydrationPhysicalBindings(List<String> errors) {
        int repositoryAdapterCount = ruleBlock.getAppRepositoryAdapters().size();
        Set<String> repositoryAdapterNames = new LinkedHashSet<>();
        for (AppRepositoryAdapter adapter : ruleBlock.getAppRepositoryAdapters()) {
            repositoryAdapterNames.add(adapter.getName());
        }
        boolean hasUniqueDefaultRepository = hasUniqueDefaultRepositoryAdapter();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                for (RepositoryHydrationRule rule : scopeItem.getRepositoryHydrationRules()) {
                    if (rule.repositoryName() == null || rule.repositoryName().isBlank()) {
                        if (repositoryAdapterCount > 1 && !hasUniqueDefaultRepository) {
                            errors.add("Repository hydration rule for response target "
                                    + rule.responseMethodName()
                                    + " must declare using <repository> when no unique default PHY repository adapter exists.");
                        }
                        continue;
                    }
                    if (!repositoryAdapterNames.contains(rule.repositoryName())) {
                        errors.add("Repository hydration rule for response target "
                                + rule.responseMethodName()
                                + " references undefined PHY repository adapter " + rule.repositoryName() + ".");
                    }
                }
            }
        }
    }

    private void validateObjectSnapshotPhysicalBindings(List<String> errors) {
        Set<String> repositoryAdapterNames = new LinkedHashSet<>();
        for (AppRepositoryAdapter adapter : ruleBlock.getAppRepositoryAdapters()) {
            repositoryAdapterNames.add(adapter.getName());
        }
        boolean hasUniqueDefaultRepository = hasUniqueDefaultRepositoryAdapter();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
                    if (rule.repositoryName() == null || rule.repositoryName().isBlank()) {
                        if (repositoryAdapterNames.size() > 1 && !hasUniqueDefaultRepository) {
                            errors.add("Object snapshot rule for object type " + rule.getObjectType()
                                    + " must declare using <repository> when no unique default PHY repository adapter exists.");
                        }
                        continue;
                    }
                    if (!repositoryAdapterNames.contains(rule.repositoryName())) {
                        errors.add("Object snapshot rule for object type " + rule.getObjectType()
                                + " references undefined PHY repository adapter " + rule.repositoryName() + ".");
                    }
                }
            }
        }
    }

    private void validateDefaultRepositoryAdapter(List<String> errors) {
        List<String> defaultRepositoryNames = new ArrayList<>();
        for (AppRepositoryAdapter adapter : ruleBlock.getAppRepositoryAdapters()) {
            if (isDefaultRepositoryAdapter(adapter)) {
                defaultRepositoryNames.add(adapter.getName());
            }
        }
        if (defaultRepositoryNames.size() > 1) {
            errors.add("PHY repository adapters declare multiple defaults: "
                    + String.join(", ", defaultRepositoryNames) + ".");
        }
    }

    private boolean hasUniqueDefaultRepositoryAdapter() {
        int defaultCount = 0;
        for (AppRepositoryAdapter adapter : ruleBlock.getAppRepositoryAdapters()) {
            if (isDefaultRepositoryAdapter(adapter)) {
                defaultCount++;
            }
        }
        return defaultCount == 1;
    }

    private boolean isDefaultRepositoryAdapter(AppRepositoryAdapter adapter) {
        return configValueForStaticValidation(adapter.getConfiguration().get("default"))
                .map(value -> "true".equals(value.trim()))
                .orElse(false);
    }

    private void validateUniquePhysicalBindingAssignments(List<String> errors) {
        Map<String, String> backendByBinding = new LinkedHashMap<>();
        Map<AppPhysicalBinding.Kind, String> backendByAllBinding = new LinkedHashMap<>();
        Set<String> duplicatedBindings = new LinkedHashSet<>();
        for (AppPhysicalBackend backend : ruleBlock.getAppPhysicalBackends()) {
            String backendName = backend.getName();
            for (AppPhysicalBinding binding : backend.getBindings()) {
                if (binding.isAllTargets()) {
                    String previousBackend = backendByAllBinding.putIfAbsent(binding.getKind(), backendName);
                    if (previousBackend != null
                            && !previousBackend.equals(backendName)
                            && duplicatedBindings.add(binding.key())) {
                        errors.add("PHY binding " + binding.key()
                                + " is assigned to multiple backends: "
                                + previousBackend + " and " + backendName + ".");
                    }
                    for (Map.Entry<String, String> entry : backendByBinding.entrySet()) {
                        if (entry.getKey().startsWith(binding.getKind().name() + ":")
                                && !entry.getValue().equals(backendName)
                                && duplicatedBindings.add(binding.key() + " overlaps " + entry.getKey())) {
                            errors.add("PHY binding " + binding.key()
                                    + " overlaps " + entry.getKey()
                                    + " assigned to backend " + entry.getValue()
                                    + "; " + binding.getKind().name()
                                    + " targets must be assigned to one backend.");
                        }
                    }
                    continue;
                }
                String allBindingBackend = backendByAllBinding.get(binding.getKind());
                if (allBindingBackend != null
                        && !allBindingBackend.equals(backendName)
                        && duplicatedBindings.add(binding.key() + " overlaps "
                                + binding.getKind().name() + ":" + AppPhysicalBinding.ALL_TARGETS)) {
                    errors.add("PHY binding " + binding.key()
                            + " overlaps " + binding.getKind().name() + ":" + AppPhysicalBinding.ALL_TARGETS
                            + " assigned to backend " + allBindingBackend
                            + "; " + binding.getKind().name()
                            + " targets must be assigned to one backend.");
                }
                String previousBackend = backendByBinding.putIfAbsent(binding.key(), backendName);
                if (previousBackend != null
                        && !previousBackend.equals(backendName)
                        && duplicatedBindings.add(binding.key())) {
                    errors.add("PHY binding " + binding.key()
                            + " is assigned to multiple backends: "
                            + previousBackend + " and " + backendName + ".");
                }
            }
        }
    }

    private void validatePhysicalBackendBindingCoverage(List<String> errors) {
        List<AppPhysicalBackend> backends = ruleBlock.getAppPhysicalBackends();
        if (backends.isEmpty()) {
            return;
        }
        if (backends.size() == 1 && backends.get(0).getBindings().isEmpty()) {
            return;
        }
        Set<String> coveredBindings = new LinkedHashSet<>();
        Set<AppPhysicalBinding.Kind> coveredKinds = new LinkedHashSet<>();
        for (AppPhysicalBackend backend : backends) {
            for (AppPhysicalBinding binding : backend.getBindings()) {
                if (binding.isAllTargets()) {
                    coveredKinds.add(binding.getKind());
                } else {
                    coveredBindings.add(binding.key());
                }
            }
        }
        for (AppPhysicalBinding target : localPhysicalBindingTargets()) {
            if (coveredKinds.contains(target.getKind()) || coveredBindings.contains(target.key())) {
                continue;
            }
            if (target.getKind() == AppPhysicalBinding.Kind.saga) {
                errors.add("PHY local saga service " + target.getTargetName()
                        + " is not assigned to any backend; add saga " + target.getTargetName()
                        + " or saga * to a PHY backend.");
            } else {
                String kind = target.getKind().name();
                errors.add("PHY local " + kind.toUpperCase(Locale.ROOT) + " resource " + target.getTargetName()
                        + " is not assigned to any backend; add " + kind + " " + target.getTargetName()
                        + " or " + kind + " * to a PHY backend.");
            }
        }
    }

    private List<AppPhysicalBinding> localPhysicalBindingTargets() {
        Map<String, AppPhysicalBinding> targets = new LinkedHashMap<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (!scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                IConstance.TransactionType transactionType = scopeItem.getTransactionType();
                if (transactionType == null) {
                    continue;
                }
                if (transactionType == IConstance.TransactionType.saga && scopeItem.getResourceId() == null) {
                    SppService service = scopeItem.getService();
                    if (service != null) {
                        AppPhysicalBinding binding = new AppPhysicalBinding(AppPhysicalBinding.Kind.saga,
                                service.getFuncName());
                        targets.putIfAbsent(binding.key(), binding);
                    }
                    continue;
                }
                String resourceId = scopeItem.getResourceId();
                if (resourceId == null || resourceId.isBlank()) {
                    continue;
                }
                AppPhysicalBinding.Kind kind = "LOCAL".equals(scopeItem.getTransactionModeName()) ? AppPhysicalBinding.Kind.at
                        : "XA".equals(scopeItem.getTransactionModeName()) ? AppPhysicalBinding.Kind.xa : AppPhysicalBinding.Kind.valueOf(transactionType.name());
                AppPhysicalBinding binding = new AppPhysicalBinding(kind, resourceId);
                targets.putIfAbsent(binding.key(), binding);
            }
        }
        return List.copyOf(targets.values());
    }

    private boolean hasPhysicalRuntimeDeclarations() {
        return !ruleBlock.getAppPhysicalBackends().isEmpty()
                || !ruleBlock.getAppRemoteAdapters().isEmpty()
                || !ruleBlock.getAppRepositoryAdapters().isEmpty()
                || !ruleBlock.getAppQuantumStoreAdapters().isEmpty();
    }

    private void validateRemoteAdapterCoverage(List<String> errors) {
        if (!hasPhysicalRuntimeDeclarations()) {
            return;
        }
        Set<String> declaredRemoteAdapters = new LinkedHashSet<>();
        for (AppRemoteAdapter adapter : ruleBlock.getAppRemoteAdapters()) {
            declaredRemoteAdapters.add(adapter.getName());
        }
        for (String serviceName : remoteServiceNames()) {
            if (!declaredRemoteAdapters.contains(serviceName)) {
                errors.add("PHY remote service " + serviceName
                        + " is not assigned to any remote adapter; add remote "
                        + serviceName + " to the PHY deployment.");
            }
        }
    }

    private List<String> remoteServiceNames() {
        Set<String> serviceNames = new LinkedHashSet<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                String serviceName = scopeItemServiceName(scopeItem);
                if (serviceName != null && !serviceName.isBlank()) {
                    serviceNames.add(serviceName);
                }
            }
        }
        return List.copyOf(serviceNames);
    }

    private Set<String> backendReferencedQuantumStores() {
        Set<String> storeNames = new LinkedHashSet<>();
        for (AppPhysicalBackend backend : ruleBlock.getAppPhysicalBackends()) {
            String storeName = backendQuantumStoreName(backend);
            if (storeName != null) {
                storeNames.add(storeName);
            }
        }
        return storeNames;
    }

    private boolean isSupportedPhysicalBackendAdapter(String adapterName) {
        return isLocalJdbcBackendAdapter(adapterName)
                || "seata".equals(adapterName);
    }

    private boolean isLocalJdbcBackendAdapter(String adapterName) {
        return "localJdbc".equals(adapterName)
                || "local-jdbc".equals(adapterName);
    }

    private void validateLocalJdbcBackendConfig(AppPhysicalBackend backend, List<String> errors) {
        validateOptionalBackendConfig(backend, errors, "localJdbc", "datasourceUrl");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "driverClassName");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "schema");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "transactionCoordinator");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "idempotencyStore");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "sagaLogStore");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "tccBranchStore");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "tccReservationStore");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "quantumStore");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "idempotencyTable");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "sagaLogTable");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "tccBranchTable");
        validateOptionalBackendConfig(backend, errors, "localJdbc", "tccReservationTable");
        validateNonNegativeBackendLongConfig(backend, errors, "localJdbc", "tccReservationTtlMillis");
        validateSupportedBackendConfig(backend, errors, "localJdbc", "transactionCoordinator",
                DEFAULT_TRANSACTION_MANAGER);
        validateSupportedBackendConfig(backend, errors, "localJdbc", "idempotencyStore",
                JDBC_BUSINESS_IDEMPOTENCY_STORE);
        validateSupportedBackendConfig(backend, errors, "localJdbc", "sagaLogStore",
                JDBC_SAGA_LOG_STORE);
        validateSupportedBackendConfig(backend, errors, "localJdbc", "tccBranchStore",
                JDBC_TCC_BRANCH_STORE);
        validateSupportedBackendConfig(backend, errors, "localJdbc", "tccReservationStore",
                JDBC_TCC_RESERVATION_STORE);
        if (ruleBlock.getAppQuantumStores().size() > 1
                && backendHandlesQuantumSemantics(backend)
                && !backendDeclaresQuantumStoreReference(backend)) {
            errors.add("PHY backend " + backend.getName()
                    + " localJdbc adapter must declare quantumStore <name>;"
                    + " when multiple APP quantum stores are declared.");
        }
    }

    private String backendQuantumStoreName(AppPhysicalBackend backend) {
        if (backend.getQuantumStoreName() != null) {
            return backend.getQuantumStoreName();
        }
        return configValueForStaticValidation(backend.getConfiguration().get("quantumStore"))
                .orElse(null);
    }

    private boolean backendDeclaresQuantumStoreReference(AppPhysicalBackend backend) {
        String explicitQuantumStoreName = backend.getQuantumStoreName();
        if (explicitQuantumStoreName != null) {
            return !explicitQuantumStoreName.isBlank();
        }
        if (!backend.getConfiguration().containsKey("quantumStore")) {
            return false;
        }
        Optional<String> validationValue = configValueForStaticValidation(
                backend.getConfiguration().get("quantumStore"));
        return validationValue.isEmpty() || !validationValue.get().isBlank();
    }

    private boolean backendHandlesQuantumSemantics(AppPhysicalBackend backend) {
        if ("LOCAL".equals(ruleBlock.getDeploymentTransactionMode()) && !ruleBlock.getAppQuantumStores().isEmpty()) return true;
        if (hasPhysicalBinding(backend, AppPhysicalBinding.Kind.quantum)) {
            return true;
        }
        return ruleBlock.getAppPhysicalBackends().size() == 1
                && backend.getBindings().isEmpty()
                && hasLocalQuantumResource();
    }

    private boolean hasLocalQuantumResource() {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (!scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                if (scopeItem.getTransactionType() == IConstance.TransactionType.quantum
                        && scopeItem.getResourceId() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateOptionalBackendConfig(AppPhysicalBackend backend, List<String> errors,
                                               String adapterName, String key) {
        if (!backend.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(backend.getConfiguration().get(key));
        if (validationValue.isPresent() && validationValue.get().isBlank()) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName + " adapter"
                    + " config " + key + " must not be blank when declared.");
        }
    }

    private void validateSupportedBackendConfig(AppPhysicalBackend backend, List<String> errors,
                                                String adapterName, String key, String supportedClass) {
        Optional<String> validationValue = configValueForStaticValidation(backend.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank() || supportedClass.equals(value)) {
            return;
        }
        errors.add("PHY backend " + backend.getName() + " " + adapterName
                + " adapter config " + key + " declares unsupported implementation " + value
                + "; supported implementation is " + supportedClass + ".");
    }

    private void validateTccObjectStoreBackendBinding(AppQuantumStoreAdapter adapter, List<String> errors) {
        if (!"tccObjectStore".equals(adapter.getAdapterName())) {
            return;
        }
        List<AppPhysicalBackend> referencingBackends = new ArrayList<>();
        for (AppPhysicalBackend backend : ruleBlock.getAppPhysicalBackends()) {
            if (adapter.getName().equals(backendQuantumStoreName(backend))) {
                referencingBackends.add(backend);
            }
        }
        if (referencingBackends.isEmpty()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " tccObjectStore must be referenced by a PHY backend quantumStore binding.");
            return;
        }
        boolean hasTccQuantumBackend = referencingBackends.stream()
                .anyMatch(backend -> backendSupportsTransaction(backend, "tcc")
                        && backendSupportsTransaction(backend, "quantum"));
        if (!hasTccQuantumBackend) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " tccObjectStore must be referenced by a PHY backend that supports both tcc and quantum;"
                    + " referencing backend transactions: "
                    + referencingBackends.stream()
                    .map(backend -> backend.getName() + "="
                            + String.join("+", backendSupportedTransactions(backend)))
                    .collect(Collectors.joining(",")) + ".");
        }
    }

    private boolean backendSupportsTransaction(AppPhysicalBackend backend, String transactionKind) {
        return backendSupportedTransactions(backend).contains(transactionKind);
    }

    private List<String> backendSupportedTransactions(AppPhysicalBackend backend) {
        if (backend == null) {
            return List.of();
        }
        String configured = backend.getConfiguration().get("supportedTransactions");
        Optional<String> validationValue = configValueForStaticValidation(configured);
        if (validationValue.isPresent() && !validationValue.get().isBlank()) {
            List<String> values = new ArrayList<>();
            for (String item : validationValue.get().split(",")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return List.copyOf(values);
        }
        String adapterName = effectiveBackendAdapterName(backend);
        if (isLocalJdbcBackendAdapter(adapterName)) {
            return List.of("at", "tcc", "quantum", "saga");
        }
        if ("seata".equals(adapterName)) {
            Optional<String> quantumSupported = configValueForStaticValidation(
                    backend.getConfiguration().get("quantumSupported"));
            if (quantumSupported.isPresent() && "true".equals(quantumSupported.get().trim())) {
                return List.of("at", "xa", "tcc", "quantum", "saga");
            }
            return List.of("at", "xa", "tcc", "saga");
        }
        return List.of();
    }

    private String effectiveBackendAdapterName(AppPhysicalBackend backend) {
        if (backend == null) {
            return null;
        }
        return backend.getAdapterName() == null ? backend.getName() : backend.getAdapterName();
    }

    private void validateSupportedTransactionsBackendConfig(AppPhysicalBackend backend, List<String> errors) {
        if (!backend.getConfiguration().containsKey("supportedTransactions")) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(
                backend.getConfiguration().get("supportedTransactions"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get();
        if (value.isBlank()) {
            errors.add("PHY backend " + backend.getName()
                    + " config supportedTransactions must not be blank when declared.");
            return;
        }
        Set<String> allowed = Set.of("at", "xa", "tcc", "quantum", "saga");
        List<String> unsupported = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            String trimmed = item.trim();
            if (trimmed.isEmpty() || !allowed.contains(trimmed)) {
                unsupported.add(trimmed.isEmpty() ? "<blank>" : trimmed);
            }
        }
        if (!unsupported.isEmpty()) {
            errors.add("PHY backend " + backend.getName()
                    + " config supportedTransactions must be a comma-separated list of"
                    + " at, tcc, quantum, and saga; unsupported values: "
                    + String.join(",", unsupported) + ".");
        }
    }

    private void validateSeataBackendConfig(AppPhysicalBackend backend, List<String> errors) {
        if (!backend.getConfiguration().containsKey("transactionServiceGroup")) {
            errors.add("PHY backend " + backend.getName()
                    + " Seata adapter config transactionServiceGroup must not be blank.");
        } else {
            Optional<String> validationTransactionServiceGroup = configValueForStaticValidation(
                    backend.getConfiguration().get("transactionServiceGroup"));
            if (validationTransactionServiceGroup.isPresent()
                    && validationTransactionServiceGroup.get().isBlank()) {
                errors.add("PHY backend " + backend.getName()
                        + " Seata adapter config transactionServiceGroup must not be blank.");
            }
        }
        String quantumSupported = backend.getConfiguration().get("quantumSupported");
        boolean quantumSupportedDeclared = backend.getConfiguration().containsKey("quantumSupported");
        Optional<String> validationQuantumSupported = configValueForStaticValidation(quantumSupported);
        if (validationQuantumSupported.isPresent()) {
            String value = validationQuantumSupported.get();
            if (value == null || value.isBlank() || !Set.of("true", "false").contains(value.trim())) {
                errors.add("PHY backend " + backend.getName()
                        + " Seata adapter config quantumSupported must be true or false.");
            }
            if (hasPhysicalBinding(backend, AppPhysicalBinding.Kind.quantum)
                    && !"true".equals(value == null ? null : value.trim())) {
                errors.add("PHY backend " + backend.getName()
                        + " Seata adapter must set quantumSupported = true when declaring quantum bindings.");
            }
        } else if (hasPhysicalBinding(backend, AppPhysicalBinding.Kind.quantum) && !quantumSupportedDeclared) {
            errors.add("PHY backend " + backend.getName()
                    + " Seata adapter must set quantumSupported = true when declaring quantum bindings.");
        }
        validateOptionalBackendConfig(backend, errors, "Seata", "transactionBridgeClass");
        validateOptionalBackendConfig(backend, errors, "Seata", "seataGlobalTransactionContextClass");
        validateOptionalBackendConfig(backend, errors, "Seata", "seataGlobalTransactionClass");
        validatePositiveBackendIntegerConfig(backend, errors, "Seata", "seataTransactionTimeoutMillis");
        validateBooleanBackendConfig(backend, errors, "Seata", "atDataSourceProxy");
        validateBooleanBackendConfig(backend, errors, "Seata", "frameworkStores");

        String bridgeClass = configValueForStaticValidation(
                backend.getConfiguration().get("transactionBridgeClass")).orElse(null);
        if (NATIVE_SEATA_TRANSACTION_BRIDGE.equals(bridgeClass)) {
            validateRequiredBackendConfig(backend, errors, "Seata", "applicationId");
            if (!"XA".equals(configValueForStaticValidation(backend.getConfiguration().get("datasourceProxyMode")).orElse(null))) {
                validateRequiredTrueBackendConfig(backend, errors, "Seata", "atDataSourceProxy");
                validateRequiredBackendConfig(backend, errors, "Seata", "undoLogTable");
            }
            if ("true".equals(configValueForStaticValidation(
                    backend.getConfiguration().get("frameworkStores")).orElse(null))) {
                for (String key : List.of("datasourceUrl", "driverClassName", "idempotencyStore",
                        "sagaLogStore", "tccBranchStore", "tccReservationStore", "idempotencyTable",
                        "sagaLogTable", "tccBranchTable", "tccReservationTable")) {
                    validateRequiredBackendConfig(backend, errors, "Seata", key);
                }
                validateNonNegativeBackendLongConfig(backend, errors, "Seata", "tccReservationTtlMillis");
            }
        }
    }

    private void validateRequiredBackendConfig(AppPhysicalBackend backend, List<String> errors,
                                               String adapterName, String key) {
        if (!backend.getConfiguration().containsKey(key)) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must not be blank.");
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(backend.getConfiguration().get(key));
        if (validationValue.isPresent() && validationValue.get().isBlank()) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must not be blank.");
        }
    }

    private void validateBooleanBackendConfig(AppPhysicalBackend backend, List<String> errors,
                                              String adapterName, String key) {
        if (!backend.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(backend.getConfiguration().get(key));
        if (validationValue.isPresent() && !Set.of("true", "false").contains(validationValue.get().trim())) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be true or false.");
        }
    }

    private void validateRequiredTrueBackendConfig(AppPhysicalBackend backend, List<String> errors,
                                                   String adapterName, String key) {
        Optional<String> validationValue = configValueForStaticValidation(backend.getConfiguration().get(key));
        if (validationValue.isPresent() && !"true".equals(validationValue.get().trim())) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be true for the native transaction bridge.");
        } else if (!backend.getConfiguration().containsKey(key)) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be true for the native transaction bridge.");
        }
    }

    private void validatePositiveBackendIntegerConfig(AppPhysicalBackend backend, List<String> errors,
                                                      String adapterName, String key) {
        if (!backend.getConfiguration().containsKey(key)) {
            return;
        }
        String value = backend.getConfiguration().get(key);
        Optional<String> validationValue = configValueForStaticValidation(value);
        if (validationValue.isEmpty()) {
            return;
        }
        String resolvedValue = validationValue.get().trim();
        if (resolvedValue.isBlank()) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be positive.");
            return;
        }
        try {
            if (Long.parseLong(resolvedValue) <= 0) {
                errors.add("PHY backend " + backend.getName() + " " + adapterName
                        + " adapter config " + key + " must be positive.");
            }
        } catch (NumberFormatException e) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be a number.");
        }
    }

    private void validateNonNegativeBackendLongConfig(AppPhysicalBackend backend, List<String> errors,
                                                      String adapterName, String key) {
        if (!backend.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(backend.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String resolvedValue = validationValue.get().trim();
        if (resolvedValue.isBlank()) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be non-negative.");
            return;
        }
        try {
            if (Long.parseLong(resolvedValue) < 0) {
                errors.add("PHY backend " + backend.getName() + " " + adapterName
                        + " adapter config " + key + " must be non-negative.");
            }
        } catch (NumberFormatException e) {
            errors.add("PHY backend " + backend.getName() + " " + adapterName
                    + " adapter config " + key + " must be a number.");
        }
    }

    private boolean hasPhysicalBinding(AppPhysicalBackend backend, AppPhysicalBinding.Kind kind) {
        for (AppPhysicalBinding binding : backend.getBindings()) {
            if (binding.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    private void validateHttpJsonRemoteAdapterConfig(AppRemoteAdapter adapter, List<String> errors) {
        if (!adapter.getConfiguration().containsKey("endpoint")) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " httpJson config endpoint must not be blank.");
        } else {
            Optional<String> validationEndpoint = configValueForStaticValidation(
                    adapter.getConfiguration().get("endpoint"));
            if (validationEndpoint.isPresent()) {
                String endpoint = validationEndpoint.get();
                if (endpoint.isBlank()) {
                    errors.add("PHY remote adapter " + adapter.getName()
                            + " httpJson config endpoint must not be blank.");
                } else {
                    validateHttpJsonEndpoint(adapter, endpoint, errors);
                }
            }
        }
        if (adapter.getConfiguration().containsKey("timeoutMillis")) {
            String timeoutMillis = adapter.getConfiguration().get("timeoutMillis");
            Optional<String> validationTimeoutMillis = configValueForStaticValidation(timeoutMillis);
            if (validationTimeoutMillis.isEmpty()) {
                return;
            }
            String resolvedTimeoutMillis = validationTimeoutMillis.get().trim();
            if (resolvedTimeoutMillis.isBlank()) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " httpJson config timeoutMillis must not be blank when declared.");
                return;
            }
            try {
                if (Long.parseLong(resolvedTimeoutMillis) <= 0) {
                    errors.add("PHY remote adapter " + adapter.getName()
                            + " httpJson config timeoutMillis must be positive.");
                }
            } catch (NumberFormatException e) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " httpJson config timeoutMillis must be a number.");
            }
        }
    }

    private Optional<String> configValueForStaticValidation(String value) {
        if (value == null || !value.contains("${")) {
            return Optional.ofNullable(value);
        }
        StringBuilder resolved = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int start = value.indexOf("${", cursor);
            if (start < 0) {
                resolved.append(value.substring(cursor));
                break;
            }
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                resolved.append(value.substring(cursor));
                break;
            }
            resolved.append(value, cursor, start);
            String expression = value.substring(start + 2, end);
            int defaultSeparator = expression.indexOf(':');
            String propertyName = defaultSeparator < 0 ? expression : expression.substring(0, defaultSeparator);
            String propertyValue = System.getProperty(propertyName);
            if (propertyValue != null) {
                resolved.append(propertyValue);
                cursor = end + 1;
                continue;
            }
            if (defaultSeparator < 0) {
                return Optional.empty();
            }
            resolved.append(expression.substring(defaultSeparator + 1));
            cursor = end + 1;
        }
        return Optional.of(resolved.toString());
    }

    private void validateHttpJsonEndpoint(AppRemoteAdapter adapter, String endpoint, List<String> errors) {
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " httpJson config endpoint must be an absolute http or https URI.");
            }
        } catch (IllegalArgumentException e) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " httpJson config endpoint is invalid: " + endpoint + ".");
        }
    }

    private boolean isObjectRepositoryRemoteAdapter(String adapterName) {
        return "objectRepositoryRemote".equals(adapterName)
                || "object-repository-remote".equals(adapterName);
    }

    private void validateQuantumServiceAdapterConfig(AppQuantumStoreAdapter adapter, List<String> errors) {
        if (usesLogicalQuantumServiceProtocol(adapter)) {
            String namespace = adapter.getConfiguration().get("namespace");
            if (namespace == null || namespace.isBlank()) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config namespace must not be blank.");
            } else if (namespace.contains("${")) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config namespace must be a stable literal.");
            }
        }
        if (!adapter.getConfiguration().containsKey("endpoint")) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " quantumService config endpoint must not be blank.");
            return;
        }
        Optional<String> validationEndpoint = configValueForStaticValidation(
                adapter.getConfiguration().get("endpoint"));
        if (validationEndpoint.isPresent()) {
            String endpoint = validationEndpoint.get();
            if (endpoint.isBlank()) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config endpoint must not be blank.");
                return;
            }
            validateQuantumServiceEndpoint(adapter, endpoint, errors);
        }
        if (adapter.getConfiguration().containsKey("timeoutMillis")) {
            String timeoutMillis = adapter.getConfiguration().get("timeoutMillis");
            Optional<String> validationTimeoutMillis = configValueForStaticValidation(timeoutMillis);
            if (validationTimeoutMillis.isEmpty()) {
                return;
            }
            String resolvedTimeoutMillis = validationTimeoutMillis.get().trim();
            if (resolvedTimeoutMillis.isBlank()) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config timeoutMillis must not be blank when declared.");
                return;
            }
            try {
                if (Long.parseLong(resolvedTimeoutMillis) <= 0) {
                    errors.add("PHY quantum store adapter " + adapter.getName()
                            + " quantumService config timeoutMillis must be positive.");
                }
            } catch (NumberFormatException e) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config timeoutMillis must be a number.");
            }
        }
    }

    private void validateQuantumDefinitions(AppQuantumStoreAdapter adapter, List<String> errors) {
        for (String attributeId : adapter.getDuplicateQuantumDefinitionAttributeIds()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " declares duplicate quantum definition " + attributeId + ".");
        }
        if (!"quantumService".equals(adapter.getAdapterName())) {
            if (!adapter.getQuantumDefinitions().isEmpty()) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " declares quantum definitions, but they are only supported by quantumService.");
            }
            return;
        }
        Optional<String> protocol = quantumServiceProtocolForStaticValidation(adapter);
        if (protocol.isEmpty()) {
            return;
        }
        if ("HTTP_JSON".equals(protocol.get())) {
            if (adapter.getConfiguration().containsKey("namespace")) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config namespace is only supported when serviceProtocol"
                        + " is LOGICAL_QSERVICE.");
            }
            if (!adapter.getQuantumDefinitions().isEmpty()) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantum definitions are only supported when serviceProtocol"
                        + " is LOGICAL_QSERVICE.");
            }
            return;
        }
        if (!"LOGICAL_QSERVICE".equals(protocol.get())) {
            return;
        }

        AppQuantumStore store = quantumStore(adapter.getName());
        Set<String> expectedAttributeIds = store == null
                ? Set.of()
                : new LinkedHashSet<>(store.getAttributeIds());
        Set<String> definedAttributeIds = new LinkedHashSet<>();
        for (AppQuantumDefinition definition : adapter.getQuantumDefinitions()) {
            String attributeId = definition.getAttributeId();
            definedAttributeIds.add(attributeId);
            if (!expectedAttributeIds.contains(attributeId)) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " defines quantum " + attributeId
                        + " which is not an attribute of APP quantum store " + adapter.getName() + ".");
            }
            validateQuantumDefinition(adapter, definition, errors);
        }
        for (String attributeId : expectedAttributeIds) {
            if (!definedAttributeIds.contains(attributeId)) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService must define number bounds for APP quantum attribute "
                        + attributeId + ".");
            }
        }
    }

    private boolean usesLogicalQuantumServiceProtocol(AppQuantumStoreAdapter adapter) {
        return "LOGICAL_QSERVICE".equals(quantumServiceProtocolForStaticValidation(adapter).orElse(""));
    }

    private void validateQuantumServiceProtocol(AppQuantumStoreAdapter adapter, List<String> errors) {
        if (!adapter.getConfiguration().containsKey("serviceProtocol")) {
            return;
        }
        Optional<String> validationValue =
                configValueForStaticValidation(adapter.getConfiguration().get("serviceProtocol"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " quantumService config serviceProtocol must not be blank when declared.");
            return;
        }
        if (!Set.of("HTTP_JSON", "LOGICAL_QSERVICE").contains(value)) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " quantumService config serviceProtocol declares unsupported protocol " + value
                    + "; supported protocols are HTTP_JSON and LOGICAL_QSERVICE.");
        }
    }

    private Optional<String> quantumServiceProtocolForStaticValidation(AppQuantumStoreAdapter adapter) {
        if (adapter == null || !"quantumService".equals(adapter.getAdapterName())) {
            return Optional.empty();
        }
        if (!adapter.getConfiguration().containsKey("serviceProtocol")) {
            return Optional.of("HTTP_JSON");
        }
        return configValueForStaticValidation(adapter.getConfiguration().get("serviceProtocol"))
                .map(String::trim);
    }

    private void validateQuantumDefinition(AppQuantumStoreAdapter adapter, AppQuantumDefinition definition,
                                           List<String> errors) {
        String prefix = "PHY quantum store adapter " + adapter.getName()
                + " quantum " + definition.getAttributeId();
        for (String property : definition.getDuplicateProperties()) {
            errors.add(prefix + " declares duplicate " + property + ".");
        }
        for (String property : List.of("numberType", "minValue", "maxValue", "scale")) {
            if (!definition.getDeclaredProperties().contains(property)) {
                errors.add(prefix + " must declare " + property + ".");
            }
        }
        String numberType = definition.getNumberType();
        if (numberType == null || !Set.of("BYTE", "SHORT", "INT", "LONG", "FLOAT", "DOUBLE")
                .contains(numberType)) {
            if (numberType != null) {
                errors.add(prefix + " numberType " + numberType
                        + " is unsupported; expected BYTE, SHORT, INT, LONG, FLOAT, or DOUBLE.");
            }
            return;
        }

        Number min = parseQuantumBound(prefix, numberType, "minValue", definition.getMinValue(), errors);
        Number max = parseQuantumBound(prefix, numberType, "maxValue", definition.getMaxValue(), errors);
        if (min != null && max != null && compareQuantumBounds(numberType, min, max) > 0) {
            errors.add(prefix + " minValue must not be greater than maxValue.");
        }

        if (definition.getScale() == null) {
            return;
        }
        try {
            int scale = Integer.parseInt(definition.getScale());
            if (scale < 0) {
                errors.add(prefix + " scale must not be negative.");
            } else if (scale > 0 && ("FLOAT".equals(numberType) || "DOUBLE".equals(numberType))) {
                errors.add(prefix + " fixed scale requires an integer numberType.");
            }
        } catch (NumberFormatException e) {
            errors.add(prefix + " scale must be a non-negative 32-bit integer.");
        }
    }

    private Number parseQuantumBound(String prefix, String numberType, String property, String value,
                                     List<String> errors) {
        if (value == null) {
            return null;
        }
        try {
            return switch (numberType) {
                case "BYTE" -> Byte.valueOf(value);
                case "SHORT" -> Short.valueOf(value);
                case "INT" -> Integer.valueOf(value);
                case "LONG" -> Long.valueOf(value);
                case "FLOAT" -> {
                    float parsed = Float.parseFloat(value);
                    if (!Float.isFinite(parsed)) {
                        throw new NumberFormatException("non-finite float");
                    }
                    yield parsed;
                }
                case "DOUBLE" -> {
                    double parsed = Double.parseDouble(value);
                    if (!Double.isFinite(parsed)) {
                        throw new NumberFormatException("non-finite double");
                    }
                    yield parsed;
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            errors.add(prefix + " " + property + " is not a valid " + numberType + " value: " + value + ".");
            return null;
        }
    }

    private int compareQuantumBounds(String numberType, Number first, Number second) {
        return switch (numberType) {
            case "BYTE" -> Byte.compare(first.byteValue(), second.byteValue());
            case "SHORT" -> Short.compare(first.shortValue(), second.shortValue());
            case "INT" -> Integer.compare(first.intValue(), second.intValue());
            case "LONG" -> Long.compare(first.longValue(), second.longValue());
            case "FLOAT" -> Float.compare(first.floatValue(), second.floatValue());
            case "DOUBLE" -> Double.compare(first.doubleValue(), second.doubleValue());
            default -> 0;
        };
    }

    private AppQuantumStore quantumStore(String storeName) {
        for (AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
            if (store.getName().equals(storeName)) {
                return store;
            }
        }
        return null;
    }

    private void validateQuantumServiceEndpoint(AppQuantumStoreAdapter adapter, String endpoint, List<String> errors) {
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                errors.add("PHY quantum store adapter " + adapter.getName()
                        + " quantumService config endpoint must be an absolute http or https URI.");
            }
        } catch (IllegalArgumentException e) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " quantumService config endpoint is invalid: " + endpoint + ".");
        }
    }

    private boolean isHttpJsonRemoteAdapter(String adapterName) {
        return "httpJson".equals(adapterName);
    }

    private void validateRemoteRoleAdapterConfig(AppRemoteAdapter adapter, List<String> errors) {
        Optional<String> roleAdapterValue = configValueForStaticValidation(
                adapter.getConfiguration().get("roleAdapter"));
        if (roleAdapterValue.isPresent()
                && !Set.of("true", "false").contains(roleAdapterValue.get().trim())) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleAdapter must be true or false.");
        }
        boolean roleAdapter = roleAdapterValue
                .map(String::trim)
                .filter("true"::equals)
                .isPresent();
        if (roleAdapter && isObjectRepositoryRemoteAdapter(adapter.getAdapterName())) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote reference adapter cannot declare roleAdapter = true;"
                    + " use httpJson or a custom external adapter for remote Role adapter binding.");
        }
        boolean roleTypeDeclared = adapter.getConfiguration().containsKey("roleType");
        boolean protocolDeclared = adapter.getConfiguration().containsKey("roleAdapterProtocol");
        if (!roleAdapter && !roleTypeDeclared && !protocolDeclared) {
            return;
        }
        validateRemoteRoleAdapterRoleTypeConfig(adapter, roleAdapter, roleTypeDeclared, errors);
        if (roleAdapter && !protocolDeclared) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleAdapterProtocol must declare " + REMOTE_ROLE_ADAPTER_PROTOCOL_HTTP_JSON
                    + " when roleAdapter is true.");
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(
                adapter.getConfiguration().get("roleAdapterProtocol"));
        if (validationValue.isEmpty()) {
            return;
        }
        String protocol = validationValue.get().trim();
        if (protocol.isBlank()) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleAdapterProtocol must not be blank when declared.");
            return;
        }
        if (!REMOTE_ROLE_ADAPTER_PROTOCOL_HTTP_JSON.equals(protocol)) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleAdapterProtocol declares unsupported protocol " + protocol
                    + "; supported protocol is " + REMOTE_ROLE_ADAPTER_PROTOCOL_HTTP_JSON + ".");
        }
    }

    private void validateRemoteRoleAdapterRoleTypeConfig(AppRemoteAdapter adapter, boolean roleAdapter,
                                                         boolean roleTypeDeclared, List<String> errors) {
        if (roleAdapter && !roleTypeDeclared) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleType must not be blank when roleAdapter is true.");
            return;
        }
        if (!roleTypeDeclared) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get("roleType"));
        if (validationValue.isEmpty()) {
            return;
        }
        String roleType = validationValue.get().trim();
        if (roleType.isBlank()) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleType must not be blank when declared.");
            return;
        }
        SppCompilationUnit roleTypeUnit = mapsOfClass.get(roleType);
        if (roleTypeUnit == null || !roleTypeUnit.isReal()) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config roleType references undefined SPP type " + roleType + ".");
        }
    }

    private String remoteAdapterRole(String adapterName) {
        if (isObjectRepositoryRemoteAdapter(adapterName)) {
            return "OBJECT_REPOSITORY_REFERENCE";
        }
        if (isHttpJsonRemoteAdapter(adapterName)) {
            return "HTTP_JSON_EXTERNAL";
        }
        return "CUSTOM_REMOTE";
    }

    private void validateObjectRepositoryRemoteAdapterConfig(AppRemoteAdapter adapter, List<String> errors) {
        validateRequiredRemoteConfig(adapter, errors, "objectRepositoryRemote", "objectType");
        validateObjectRepositoryRemoteObjectIdPaths(adapter, errors);
        validateObjectRepositoryRemoteRepositoryReference(adapter, errors);
        validateObjectRepositoryRemoteServiceShape(adapter, errors);
    }

    private void validateObjectRepositoryRemoteObjectIdPaths(AppRemoteAdapter adapter, List<String> errors) {
        boolean hasSinglePath = hasResolvedTextConfig(adapter, "objectIdPath");
        boolean singlePathUnresolved = isUnresolvedConfigPlaceholder(adapter, "objectIdPath");
        List<String> objectIdPaths = configuredPathList(adapter, "objectIdPaths");
        boolean objectIdPathsUnresolved = isUnresolvedConfigPlaceholder(adapter, "objectIdPaths");
        if (!hasSinglePath && objectIdPaths.isEmpty()
                && !singlePathUnresolved && !objectIdPathsUnresolved) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config objectIdPath or objectIdPaths must not be blank.");
        }
        if (adapter.getConfiguration().containsKey("objectIdPath") && !hasSinglePath
                && !singlePathUnresolved && !adapter.getConfiguration().containsKey("objectIdPaths")) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config objectIdPath must not be blank.");
        }
        if (adapter.getConfiguration().containsKey("objectIdPaths")
                && objectIdPaths.isEmpty() && !objectIdPathsUnresolved) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config objectIdPaths must contain at least one path.");
        }
        List<String> repositoryObjectIdPaths = configuredPathList(adapter, "repositoryObjectIdPaths");
        boolean requestPathsUnresolved = singlePathUnresolved || objectIdPathsUnresolved;
        boolean repositoryObjectIdPathsUnresolved = isUnresolvedConfigPlaceholder(adapter, "repositoryObjectIdPaths");
        if (adapter.getConfiguration().containsKey("repositoryObjectIdPaths")) {
            if (repositoryObjectIdPaths.isEmpty() && !repositoryObjectIdPathsUnresolved) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " objectRepositoryRemote config repositoryObjectIdPaths"
                        + " must contain at least one path.");
            } else if (!repositoryObjectIdPaths.isEmpty() && !requestPathsUnresolved) {
                int requestPathCount = objectIdPaths.isEmpty() ? 1 : objectIdPaths.size();
                if (repositoryObjectIdPaths.size() != requestPathCount) {
                    errors.add("PHY remote adapter " + adapter.getName()
                            + " objectRepositoryRemote config repositoryObjectIdPaths"
                            + " must have the same number of paths as objectIdPath/objectIdPaths.");
                }
            }
        }
    }

    private List<String> configuredPathList(AppRemoteAdapter adapter, String key) {
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty() || !hasText(validationValue.get())) {
            return List.of();
        }
        String value = validationValue.get();
        List<String> paths = new ArrayList<>();
        for (String path : value.split(",")) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return List.copyOf(paths);
    }

    private void validateObjectRepositoryRemoteServiceShape(AppRemoteAdapter adapter, List<String> errors) {
        if (!hasRemoteService(adapter.getName())) {
            return;
        }
        SppService service = findServiceByFuncName(adapter.getName());
        if (service == null) {
            return;
        }
        List<SppLocalVar> parameters = service.getServiceBody().getSppLocalVarList();
        if (parameters.isEmpty() || parameters.size() > 2) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote requires one key participant and optionally one result participant.");
            return;
        }
        SppLocalVar request = parameters.get(0);
        if (request.getType() == null || request.getArrayDimension() != 0
                || !(request.getType() instanceof SppClass)) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote requires exactly one single request object parameter.");
            return;
        }
        for (String objectIdPath : objectRepositoryRemoteRequestObjectIdPaths(adapter)) {
            validateObjectRepositoryRemoteRequestObjectIdPath(adapter, request, objectIdPath, errors);
        }
        if (parameters.size() == 2) {
            SppLocalVar result = parameters.get(1);
            var storedType = configValueForStaticValidation(adapter.getConfiguration().get("objectType"));
            if (result.getType() != null && storedType.isPresent()
                    && !storedType.get().trim().equals(result.getType().getName()))
                errors.add("PHY remote adapter " + adapter.getName()
                        + " objectRepositoryRemote result participant type must match configured objectType " + storedType.get().trim() + ".");
            if (!(result.getType() instanceof SppClass) || result.getArrayDimension() > 1)
                errors.add("PHY remote adapter " + adapter.getName()
                        + " objectRepositoryRemote result must be a participant or a participant collection.");
        }
        validateObjectRepositoryRemoteReturnShape(adapter, service, errors);
    }

    private List<String> objectRepositoryRemoteRequestObjectIdPaths(AppRemoteAdapter adapter) {
        List<String> objectIdPaths = configuredPathList(adapter, "objectIdPaths");
        if (!objectIdPaths.isEmpty()) {
            return objectIdPaths;
        }
        Optional<String> objectIdPath = configValueForStaticValidation(adapter.getConfiguration().get("objectIdPath"));
        if (objectIdPath.isEmpty() || !hasText(objectIdPath.get())) {
            return List.of();
        }
        return List.of(objectIdPath.get().trim());
    }

    private void validateObjectRepositoryRemoteRequestObjectIdPath(AppRemoteAdapter adapter, SppLocalVar request,
                                                                   String objectIdPath, List<String> errors) {
        if (!hasText(objectIdPath)) {
            return;
        }
        SppField field = resolveFieldPath(request.getType(), objectIdPath);
        String requestObjectPath = request.getType().getName() + "." + objectIdPath;
        if (field == null) {
            if (isRolePerformerObjectIdPath(adapter, request, objectIdPath, errors)) {
                return;
            }
            if (isRoleTakeIdentityObjectPath(requestObjectPath)) {
                return;
            }
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config object id path " + requestObjectPath + " not found.");
            return;
        }
        String arrayFieldPath = firstArrayFieldPath(request.getType(), objectIdPath);
        if (arrayFieldPath != null) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config object id path " + requestObjectPath
                    + " must not traverse array field " + arrayFieldPath + ".");
        }
    }

    private boolean isRolePerformerObjectIdPath(AppRemoteAdapter adapter, SppLocalVar request,
                                                String objectIdPath, List<String> errors) {
        if (adapter == null || request == null || request.getType() == null
                || objectIdPath == null || objectIdPath.isBlank() || !objectIdPath.contains(".")) {
            return false;
        }
        Optional<String> configuredObjectType =
                configValueForStaticValidation(adapter.getConfiguration().get("objectType"));
        if (configuredObjectType.isEmpty() || configuredObjectType.get().isBlank()) {
            return false;
        }
        String objectType = configuredObjectType.get().trim();
        if (!rolePerformerNames(request.getType().getName()).contains(objectType)) {
            return false;
        }
        SppCompilationUnit performerUnit = mapsOfClass.get(objectType);
        if (!(performerUnit instanceof SppClass performerClass)) {
            return false;
        }
        String performerPath = roleTakePerformerIdPath(objectIdPath, performerClass);
        if (performerPath == null || performerPath.isBlank()) {
            return false;
        }
        SppField performerField = resolveFieldPath(performerClass, performerPath);
        if (performerField == null) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config object id path " + objectType + "." + performerPath
                    + " not found on Role performer identity " + request.getType().getName()
                    + " -> " + objectType + ".");
            return true;
        }
        String arrayFieldPath = firstArrayFieldPath(performerClass, performerPath);
        if (arrayFieldPath != null) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config object id path " + objectType + "." + performerPath
                    + " must not traverse array field " + arrayFieldPath + ".");
        }
        return true;
    }

    private void validateObjectRepositoryRemoteReturnShape(AppRemoteAdapter adapter, SppService service,
                                                           List<String> errors) {
        if (objectRepositoryRemoteRequestObjectIdPaths(adapter).size() <= 1) {
            return;
        }
        var parameters = service.getServiceBody().getSppLocalVarList();
        boolean collectionResult = parameters.size() == 2 && parameters.get(1).getArrayDimension() == 1;
        if (!collectionResult) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config objectIdPaths requires a result participant array or collection.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasResolvedTextConfig(AppRemoteAdapter adapter, String key) {
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        return validationValue.isPresent() && hasText(validationValue.get());
    }

    private boolean isUnresolvedConfigPlaceholder(AppRemoteAdapter adapter, String key) {
        return adapter.getConfiguration().containsKey(key)
                && configValueForStaticValidation(adapter.getConfiguration().get(key)).isEmpty();
    }

    private void validateObjectRepositoryRemoteRepositoryReference(AppRemoteAdapter adapter, List<String> errors) {
        if (!adapter.getConfiguration().containsKey("repository")) {
            return;
        }
        Optional<String> validationRepositoryName = configValueForStaticValidation(
                adapter.getConfiguration().get("repository"));
        if (validationRepositoryName.isEmpty()) {
            return;
        }
        String repositoryName = validationRepositoryName.get();
        if (repositoryName.isBlank()) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " objectRepositoryRemote config repository must not be blank.");
            return;
        }
        for (AppRepositoryAdapter repositoryAdapter : ruleBlock.getAppRepositoryAdapters()) {
            if (repositoryAdapter.getName().equals(repositoryName)) {
                return;
            }
        }
        errors.add("PHY remote adapter " + adapter.getName()
                + " objectRepositoryRemote config repository references undefined PHY repository adapter "
                + repositoryName + ".");
    }

    private void validateRequiredRemoteConfig(AppRemoteAdapter adapter, List<String> errors,
                                              String adapterName, String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            errors.add("PHY remote adapter " + adapter.getName() + " " + adapterName
                    + " config " + key + " must not be blank.");
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isPresent() && validationValue.get().isBlank()) {
            errors.add("PHY remote adapter " + adapter.getName() + " " + adapterName
                    + " config " + key + " must not be blank.");
        }
    }

    private void validateRemoteCompensationPolicyConfig(AppRemoteAdapter adapter, List<String> errors) {
        validatePositiveIntegerConfig(adapter, errors, "compensationMaxAttempts");
        validateNonNegativeIntegerConfig(adapter, errors, "compensationBackoffMillis");
        String manual = adapter.getConfiguration().get("compensationManualInterventionRequired");
        if (!adapter.getConfiguration().containsKey("compensationManualInterventionRequired")) {
            return;
        }
        Optional<String> validationManual = configValueForStaticValidation(manual);
        if (validationManual.isEmpty()) {
            return;
        }
        String value = validationManual.get();
        if (value == null || value.isBlank() || !Set.of("true", "false").contains(value.trim())) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config compensationManualInterventionRequired must be true or false.");
        }
    }

    private void validatePositiveIntegerConfig(AppRemoteAdapter adapter, List<String> errors, String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get();
        if (value == null || value.isBlank()) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config " + key + " must be a positive integer.");
            return;
        }
        try {
            if (Integer.parseInt(value.trim()) <= 0) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " config " + key + " must be a positive integer.");
            }
        } catch (NullPointerException | NumberFormatException e) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config " + key + " must be a positive integer.");
        }
    }

    private void validateNonNegativeIntegerConfig(AppRemoteAdapter adapter, List<String> errors, String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get();
        if (value == null || value.isBlank()) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config " + key + " must be a non-negative integer.");
            return;
        }
        try {
            if (Long.parseLong(value.trim()) < 0) {
                errors.add("PHY remote adapter " + adapter.getName()
                        + " config " + key + " must be a non-negative integer.");
            }
        } catch (NullPointerException | NumberFormatException e) {
            errors.add("PHY remote adapter " + adapter.getName()
                    + " config " + key + " must be a non-negative integer.");
        }
    }

    private boolean isSupportedRepositoryAdapter(String adapterName) {
        return "objectSnapshot".equals(adapterName)
                || "objectRepository".equals(adapterName)
                || isJdbcObjectRepositoryAdapter(adapterName);
    }

    private boolean isJdbcObjectRepositoryAdapter(String adapterName) {
        return "jdbcObjectRepository".equals(adapterName)
                || "jdbc-object-repository".equals(adapterName);
    }

    private void validateOptionalRepositoryConfig(AppRepositoryAdapter adapter, List<String> errors, String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isPresent() && validationValue.get().isBlank()) {
            errors.add("PHY repository adapter " + adapter.getName()
                    + " " + adapter.getAdapterName() + " config " + key
                    + " must not be blank when declared.");
        }
    }

    private void validateRepositoryDatasourceDriver(AppRepositoryAdapter adapter, List<String> errors) {
        Optional<String> datasourceUrl = configValueForStaticValidation(
                adapter.getConfiguration().get("datasourceUrl"));
        if (datasourceUrl.isEmpty() || datasourceUrl.get().isBlank()
                || adapter.getConfiguration().containsKey("driverClassName")) {
            return;
        }
        errors.add("PHY repository adapter " + adapter.getName()
                + " " + adapter.getAdapterName()
                + " config driverClassName must be declared when datasourceUrl is declared.");
    }

    private void validateBooleanRepositoryConfig(AppRepositoryAdapter adapter, List<String> errors, String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isPresent()
                && !Set.of("true", "false").contains(validationValue.get().trim())) {
            errors.add("PHY repository adapter " + adapter.getName()
                    + " " + adapter.getAdapterName()
                    + " config " + key + " must be true or false.");
        }
    }

    private void validateTransactionRetentionRepositoryConfig(AppRepositoryAdapter adapter, List<String> errors) {
        validateOptionalRepositoryConfig(adapter, errors, "transactionRetentionMode");
        validateOptionalRepositoryConfig(adapter, errors, "transactionRetentionPropertyPrefix");
        validateBooleanRepositoryConfig(adapter, errors, "transactionRetentionEnabledByDefault");
        validateTransactionRetentionModeRepositoryConfig(adapter, errors);
        validatePositiveRepositoryLongConfig(adapter, errors, "transactionRetentionFixedDelayMillis");
        validatePositiveRepositoryLongConfig(adapter, errors, "transactionRetentionMillis");
        validatePositiveRepositoryLongConfig(adapter, errors, "transactionArchiveRetentionMillis");
        validatePositiveRepositoryLongMapConfig(adapter, errors, "transactionRetentionMillisByService");
        validatePositiveRepositoryLongMapConfig(adapter, errors, "transactionRetentionMillisByResource");
        validatePositiveRepositoryLongMapConfig(adapter, errors, "transactionArchiveRetentionMillisByResource");
    }

    private void validateTransactionRetentionModeRepositoryConfig(AppRepositoryAdapter adapter, List<String> errors) {
        Optional<String> validationValue = configValueForStaticValidation(
                adapter.getConfiguration().get("transactionRetentionMode"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank()
                || "MANUAL_API".equals(value)
                || "PHYSICAL_SCHEDULED_ARCHIVE".equals(value)) {
            return;
        }
        errors.add("PHY repository adapter " + adapter.getName()
                + " " + adapter.getAdapterName()
                + " config transactionRetentionMode declares unsupported mode " + value
                + "; supported modes are MANUAL_API and PHYSICAL_SCHEDULED_ARCHIVE.");
    }

    private void validatePositiveRepositoryLongConfig(AppRepositoryAdapter adapter, List<String> errors, String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String resolvedValue = validationValue.get().trim();
        if (resolvedValue.isBlank()) {
            errors.add("PHY repository adapter " + adapter.getName()
                    + " " + adapter.getAdapterName()
                    + " config " + key + " must be positive.");
            return;
        }
        try {
            if (Long.parseLong(resolvedValue) <= 0) {
                errors.add("PHY repository adapter " + adapter.getName()
                        + " " + adapter.getAdapterName()
                        + " config " + key + " must be positive.");
            }
        } catch (NumberFormatException e) {
            errors.add("PHY repository adapter " + adapter.getName()
                    + " " + adapter.getAdapterName()
                    + " config " + key + " must be a number.");
        }
    }

    private void validatePositiveRepositoryLongMapConfig(AppRepositoryAdapter adapter, List<String> errors,
                                                         String key) {
        if (!adapter.getConfiguration().containsKey(key)) {
            return;
        }
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get();
        if (value == null || value.isBlank()) {
            errors.add("PHY repository adapter " + adapter.getName()
                    + " " + adapter.getAdapterName()
                    + " config " + key
                    + " must be a comma-separated name:positiveMillis list.");
            return;
        }
        for (String entry : value.split(",")) {
            String trimmedEntry = entry.trim();
            int separator = trimmedEntry.indexOf(':');
            if (trimmedEntry.isEmpty()
                    || separator <= 0
                    || separator == trimmedEntry.length() - 1
                    || trimmedEntry.indexOf(':', separator + 1) >= 0) {
                errors.add("PHY repository adapter " + adapter.getName()
                        + " " + adapter.getAdapterName()
                        + " config " + key
                        + " must be a comma-separated name:positiveMillis list.");
                return;
            }
            String name = trimmedEntry.substring(0, separator).trim();
            String millis = trimmedEntry.substring(separator + 1).trim();
            if (name.isEmpty() || millis.isEmpty()) {
                errors.add("PHY repository adapter " + adapter.getName()
                        + " " + adapter.getAdapterName()
                        + " config " + key
                        + " must be a comma-separated name:positiveMillis list.");
                return;
            }
            try {
                if (Long.parseLong(millis) <= 0) {
                    errors.add("PHY repository adapter " + adapter.getName()
                            + " " + adapter.getAdapterName()
                            + " config " + key
                            + " entry " + name + " must declare a positive millis value.");
                }
            } catch (NumberFormatException e) {
                errors.add("PHY repository adapter " + adapter.getName()
                        + " " + adapter.getAdapterName()
                        + " config " + key
                        + " entry " + name + " must declare a numeric millis value.");
            }
        }
    }

    private boolean isSupportedQuantumStoreAdapter(String adapterName) {
        return isJdbcQuantumStoreAdapter(adapterName)
                || "tccObjectStore".equals(adapterName)
                || "quantumService".equals(adapterName);
    }

    private boolean isJdbcQuantumStoreAdapter(String adapterName) {
        return "jdbcQuantum".equals(adapterName)
                || "jdbc-quantum".equals(adapterName);
    }

    private void validateRequiredQuantumStoreConfig(AppQuantumStoreAdapter adapter, List<String> errors, String key) {
        String configured = adapter.getConfiguration().get(key);
        Optional<String> validationValue = configValueForStaticValidation(configured);
        if (configured == null || configured.isBlank()
                || (validationValue.isPresent() && validationValue.get().isBlank())) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " jdbcQuantum config " + key + " must not be blank.");
        }
    }

    private void validateSupportedQuantumStoreConfig(AppQuantumStoreAdapter adapter, List<String> errors,
                                                     String key, String supportedClass) {
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank() || supportedClass.equals(value)) {
            return;
        }
        errors.add("PHY quantum store adapter " + adapter.getName()
                + " jdbcQuantum config " + key
                + " declares unsupported implementation " + value
                + "; supported implementation is " + supportedClass + ".");
    }

    private void validateQuantumImplementationStrategy(AppQuantumStoreAdapter adapter, List<String> errors) {
        Optional<String> validationValue = configValueForStaticValidation(
                adapter.getConfiguration().get("implementationStrategy"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " config implementationStrategy must not be blank when declared.");
            return;
        }
        if ("GLOBAL_QUANTUM_STORE".equals(value)
                || "TCC_OBJECT_STORE".equals(value)
                || "QUANTUM_SERVICE".equals(value)) {
            return;
        }
        errors.add("PHY quantum store adapter " + adapter.getName()
                + " config implementationStrategy declares unsupported strategy " + value
                + "; supported strategies are GLOBAL_QUANTUM_STORE, TCC_OBJECT_STORE and QUANTUM_SERVICE.");
    }

    private void validateQuantumAccessMode(AppQuantumStoreAdapter adapter, List<String> errors) {
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get("accessMode"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " config accessMode must not be blank when declared.");
            return;
        }
        if ("GLOBAL_STORE_ADAPTER".equals(value)
                || "TCC_OBJECT_STORE_ADAPTER".equals(value)
                || "QUANTUM_SERVICE_ADAPTER".equals(value)) {
            return;
        }
        errors.add("PHY quantum store adapter " + adapter.getName()
                + " config accessMode declares unsupported mode " + value
                + "; supported modes are GLOBAL_STORE_ADAPTER, TCC_OBJECT_STORE_ADAPTER and QUANTUM_SERVICE_ADAPTER.");
    }

    private void validateQuantumIdempotencyMode(AppQuantumStoreAdapter adapter, List<String> errors) {
        Optional<String> validationValue = configValueForStaticValidation(
                adapter.getConfiguration().get("idempotencyMode"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " config idempotencyMode must not be blank when declared.");
            return;
        }
        if ("FRAMEWORK_IDEMPOTENCY".equals(value)
                || "OBJECT_STORE_IDEMPOTENCY".equals(value)
                || "SERVICE_IDEMPOTENCY".equals(value)) {
            return;
        }
        errors.add("PHY quantum store adapter " + adapter.getName()
                + " config idempotencyMode declares unsupported mode " + value
                + "; supported modes are FRAMEWORK_IDEMPOTENCY, OBJECT_STORE_IDEMPOTENCY and SERVICE_IDEMPOTENCY.");
    }

    private void validateQuantumReversalMode(AppQuantumStoreAdapter adapter, List<String> errors) {
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get("reversalMode"));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank()) {
            errors.add("PHY quantum store adapter " + adapter.getName()
                    + " config reversalMode must not be blank when declared.");
            return;
        }
        if ("NEGATIVE_DELTA".equals(value)
                || "SERVICE_REVERSAL".equals(value)) {
            return;
        }
        errors.add("PHY quantum store adapter " + adapter.getName()
                + " config reversalMode declares unsupported mode " + value
                + "; supported modes are NEGATIVE_DELTA and SERVICE_REVERSAL.");
    }

    private void validateQuantumAccessContractConsistency(AppQuantumStoreAdapter adapter, List<String> errors) {
        validateQuantumContractValue(adapter, errors, "implementationStrategy",
                defaultQuantumImplementationStrategy(adapter.getAdapterName()));
        validateQuantumContractValue(adapter, errors, "accessMode",
                defaultQuantumAccessMode(adapter.getAdapterName()));
        validateQuantumContractValue(adapter, errors, "idempotencyMode",
                defaultQuantumIdempotencyMode(adapter.getAdapterName()));
        validateQuantumContractValue(adapter, errors, "reversalMode",
                expectedQuantumReversalMode(adapter));
    }

    private void validateQuantumContractValue(AppQuantumStoreAdapter adapter, List<String> errors,
                                              String key, String expectedValue) {
        Optional<String> validationValue = configValueForStaticValidation(adapter.getConfiguration().get(key));
        if (validationValue.isEmpty()) {
            return;
        }
        String value = validationValue.get().trim();
        if (value.isBlank() || expectedValue == null || expectedValue.equals(value)) {
            return;
        }
        errors.add("PHY quantum store adapter " + adapter.getName()
                + " config " + key + " must be " + expectedValue
                + " when adapter is " + adapter.getAdapterName()
                + ", but was " + value + ".");
    }

    private String defaultQuantumImplementationStrategy(String adapterName) {
        if (isJdbcQuantumStoreAdapter(adapterName)) {
            return "GLOBAL_QUANTUM_STORE";
        }
        if ("tccObjectStore".equals(adapterName)) {
            return "TCC_OBJECT_STORE";
        }
        if ("quantumService".equals(adapterName)) {
            return "QUANTUM_SERVICE";
        }
        return null;
    }

    private void validateQuantumImplementationStrategyExclusivity(List<String> errors) {
        Map<String, List<String>> storesByStrategy = new LinkedHashMap<>();
        for (AppQuantumStoreAdapter adapter : ruleBlock.getAppQuantumStoreAdapters()) {
            if (!isSupportedQuantumStoreAdapter(adapter.getAdapterName())) {
                continue;
            }
            String strategy = configValueForStaticValidation(adapter.getConfiguration().get("implementationStrategy"))
                    .map(String::trim)
                    .orElse("");
            if (strategy.isBlank()) {
                strategy = defaultQuantumImplementationStrategy(adapter.getAdapterName());
            }
            if (strategy == null || strategy.isBlank()) {
                continue;
            }
            storesByStrategy.computeIfAbsent(strategy, ignored -> new ArrayList<>())
                    .add(adapter.getName() + "=" + adapter.getAdapterName());
        }
        if (storesByStrategy.size() <= 1) {
            return;
        }
        errors.add("PHY quantum implementation strategies are mutually exclusive within one physical profile; found "
                + storesByStrategy.entrySet().stream()
                .map(entry -> entry.getKey() + "[" + String.join(",", entry.getValue()) + "]")
                .collect(Collectors.joining(";"))
                + ".");
    }

    private String defaultQuantumAccessMode(String adapterName) {
        if (isJdbcQuantumStoreAdapter(adapterName)) {
            return "GLOBAL_STORE_ADAPTER";
        }
        if ("tccObjectStore".equals(adapterName)) {
            return "TCC_OBJECT_STORE_ADAPTER";
        }
        if ("quantumService".equals(adapterName)) {
            return "QUANTUM_SERVICE_ADAPTER";
        }
        return null;
    }

    private String defaultQuantumIdempotencyMode(String adapterName) {
        if (isJdbcQuantumStoreAdapter(adapterName)) {
            return "FRAMEWORK_IDEMPOTENCY";
        }
        if ("tccObjectStore".equals(adapterName)) {
            return "OBJECT_STORE_IDEMPOTENCY";
        }
        if ("quantumService".equals(adapterName)) {
            return "SERVICE_IDEMPOTENCY";
        }
        return null;
    }

    private String expectedQuantumReversalMode(AppQuantumStoreAdapter adapter) {
        String adapterName = adapter.getAdapterName();
        if (isJdbcQuantumStoreAdapter(adapterName) || "tccObjectStore".equals(adapterName)) {
            return "NEGATIVE_DELTA";
        }
        if ("quantumService".equals(adapterName)) {
            return quantumServiceProtocolForStaticValidation(adapter)
                    .filter(protocol -> Set.of("HTTP_JSON", "LOGICAL_QSERVICE").contains(protocol))
                    .map(protocol -> "LOGICAL_QSERVICE".equals(protocol)
                            ? "SERVICE_REVERSAL"
                            : "NEGATIVE_DELTA")
                    .orElse(null);
        }
        return null;
    }

    private void validatePhysicalBinding(String backendName, AppPhysicalBinding binding, List<String> errors) {
        if (binding.getKind() == AppPhysicalBinding.Kind.repository) {
            if (binding.isAllTargets()) {
                if (ruleBlock.getAppRepositoryAdapters().isEmpty()) {
                    errors.add("PHY backend " + backendName
                            + " repository binding * must reference at least one PHY repository adapter.");
                }
                return;
            }
            if (ruleBlock.getAppRepositoryAdapters().stream()
                    .noneMatch(adapter -> binding.getTargetName().equals(adapter.getName()))) {
                errors.add("PHY backend " + backendName + " repository binding " + binding.getTargetName()
                        + " must reference a declared PHY repository adapter.");
            }
            return;
        }
        if (binding.getKind() == AppPhysicalBinding.Kind.saga
                && !(binding.isAllTargets() ? hasLocalTransactionResource(IConstance.TransactionType.saga)
                    : hasLocalTransactionResource(binding.getTargetName(),IConstance.TransactionType.saga))) {
            if (binding.isAllTargets()) {
                if (!hasLocalSagaService()) {
                    errors.add("PHY backend " + backendName
                            + " saga binding * must reference at least one local saga service"
                            + " declared in APP scope.");
                }
                return;
            }
            if (!hasLocalSagaService(binding.getTargetName())) {
                errors.add("PHY backend " + backendName + " saga binding " + binding.getTargetName()
                        + " must reference a local saga service declared in APP scope.");
            }
            return;
        }
        IConstance.TransactionType transactionType = IConstance.TransactionType.valueOf(binding.getKind().name());
        if (binding.isAllTargets()) {
            if (!hasLocalTransactionResource(transactionType)) {
                errors.add("PHY backend " + backendName + " " + binding.getKind().name()
                        + " binding * must reference at least one local "
                        + binding.getKind().name().toUpperCase(Locale.ROOT)
                        + " resource declared in APP scope.");
            }
            return;
        }
        if (!hasLocalTransactionResource(binding.getTargetName(), transactionType)) {
            errors.add("PHY backend " + backendName + " " + binding.getKind().name()
                    + " binding " + binding.getTargetName()
                    + " must reference a local " + binding.getKind().name().toUpperCase(Locale.ROOT)
                    + " resource declared in APP scope.");
        }
    }

    private boolean hasLocalSagaService() {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (!scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                if (scopeItem.getService() != null
                        && scopeItem.getTransactionType() == IConstance.TransactionType.saga) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLocalSagaService(String serviceName) {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (!scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                SppService service = scopeItem.getService();
                if (service != null
                        && service.getFuncName().equals(serviceName)
                        && scopeItem.getTransactionType() == IConstance.TransactionType.saga) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRemoteService(String serviceName) {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                SppService service = scopeItem.getService();
                if (service != null && service.getFuncName().equals(serviceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLocalTransactionResource(IConstance.TransactionType transactionType) {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (!scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                if (scopeItem.getResourceId() != null
                        && scopeItem.getTransactionType() == transactionType) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLocalTransactionResource(String resourceId, IConstance.TransactionType transactionType) {
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (!scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                if (resourceId.equals(scopeItem.getResourceId())
                        && scopeItem.getTransactionType() == transactionType) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<String> validateAppTableDeclarations() {
        List<String> errors = new ArrayList<>();
        for (String duplicateTableName : ruleBlock.getDuplicateTableNames()) {
            errors.add("APP table " + duplicateTableName
                    + " is declared multiple times.");
        }
        Map<String, AppTable> tableByBusinessType = referencedAppTables();
        Map<String, Integer> tableCountByBusinessType = new LinkedHashMap<>();
        for (var table : new HashSet<>(tableByBusinessType.values())) tableCountByBusinessType.put(table.getEntity().getName(), 1);
        for (AppTables appTables : ruleBlock.getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (appTable.getEntity() != null && appTable.getEntity().isReal()) {
                    tableByBusinessType.putIfAbsent(appTable.getEntity().getName(), appTable);
                    tableCountByBusinessType.merge(appTable.getEntity().getName(), 1, Integer::sum);
                }
            }
        }
        Map<String, String> mappedBusinessTypes = new LinkedHashMap<>();
        Set<String> duplicateBusinessTypes = new LinkedHashSet<>();
        for (AppTables appTables : ruleBlock.getAppTables()) {
            if (!appTables.isKnownCategory()) {
                errors.add("Unknown APP table category " + appTables.getName()
                        + ". Expected entities, roles or contracts.");
            }
            for (AppTable appTable : appTables.getAppTableList()) {
                validateTableTargetResolution(appTable, errors);
                SppClass target = appTable.getEntity();
                if (target == null || !target.isReal()) {
                    if (appTable.getUndefinedBusinessTypeNames().isEmpty()
                            && appTable.getNonBusinessTypeNames().isEmpty()) {
                        errors.add("Table " + appTable.getName()
                                + " must map to a defined business type.");
                    }
                    continue;
                }
                validateSingleTableBusinessType(appTable, errors);
                IConstance.CompilationUnitType expectedType = expectedTableTargetType(appTable.getCategory());
                if (expectedType != null && target.getType() != expectedType) {
                    errors.add("Table " + appTable.getName() + " in tables "
                            + appTable.getCategory().name() + " must map to a "
                            + expectedType.name() + ", but " + target.getName()
                            + " is " + target.getType().name() + ".");
                }
                String previousTable = mappedBusinessTypes.putIfAbsent(target.getName(), appTable.getName());
                if (previousTable != null && duplicateBusinessTypes.add(target.getName())) {
                    errors.add("Business type " + target.getName()
                            + " is mapped by multiple APP tables: "
                            + previousTable + " and " + appTable.getName() + ".");
                }
                validateTablePrimaryKeys(appTable, tableByBusinessType, tableCountByBusinessType, errors);
                validateTableForeignKeys(appTable, tableByBusinessType, tableCountByBusinessType, errors);
                validateTableOutputColumns(appTable, tableByBusinessType, errors);
            }
        }
        return errors;
    }

    public List<String> appSemanticMigrationWarnings() {
        List<String> warnings = new ArrayList<>();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            String warning = appSemanticMigrationWarning(plan);
            if (!warning.isBlank()) {
                warnings.add(warning);
            }
        }
        return warnings;
    }

    public List<String> validateRoleOnlyBusinessModelDeclarations() {
        List<String> errors = new ArrayList<>();
        errors.addAll(validateRoleTakeBusinessModelDeclarations());
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if ("ROLE_PERFORMER_FIELD".equals(plan.migrationType())) {
                String action = "removeRolePerformerField+useTakePerformerIdentity";
                errors.add("Role " + plan.objectType()
                        + " declares performer fields " + String.join(", ", plan.paths())
                        + "; participantCategories=" + valueOrDash(String.join("+", plan.participantCategories()))
                        + "; use take(...) and APP DDL/runtime role identity instead of technical identity fields in SPP"
                        + "; actions=" + action
                        + "; action=" + action + ".");
            } else if ("ROLE_OBJECT_FIELD".equals(plan.migrationType())) {
                String action = "removeRoleObjectField+useRuntimeRoleParticipantIdentity";
                errors.add("Role " + plan.objectType()
                        + " declares object participant fields " + String.join(", ", plan.paths())
                        + "; participantCategories=" + valueOrDash(String.join("+", plan.participantCategories()))
                        + "; role fields must describe role facts only"
                        + "; actions=" + action
                        + "; action=" + action + ".");
            } else if ("ROLE_WITHOUT_PERFORMER".equals(plan.migrationType())) {
                String action = "declareRoleTakePerformer";
                errors.add("Role " + plan.objectType()
                        + " has no take performer declaration; service-oriented Roles must be played by"
                        + " Entity, Reference, or Role participants at runtime"
                        + "; actions=" + action
                        + "; action=" + action + ".");
            }
        }
        return List.copyOf(errors);
    }

    private List<String> validateRoleTakeBusinessModelDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper)) {
                continue;
            }
            if (mapper.getRole() == null || mapper.getRole().getType() != IConstance.CompilationUnitType.role) {
                errors.add("Role take declaration " + mapper.getName()
                        + " must target a Role; take expresses runtime Role playing, not inheritance or"
                        + " Contract composition.");
            }
            List<SppClass> performers = roleMapperPerformers(mapper);
            if (performers.isEmpty()) {
                continue;
            }
            for (SppClass performer : performers) {
                if (performer == null) {
                    errors.add("Role take declaration " + mapper.getName()
                            + " references an unknown performer type.");
                    continue;
                }
                IConstance.CompilationUnitType performerType = performer.getType();
                if (performerType == IConstance.CompilationUnitType.contract) {
                    errors.add("Role take declaration " + mapper.getName()
                            + " uses Contract " + performer.getName() + " as a performer; contracts are signed by"
                            + " Role participants and must not play their internal Roles.");
                } else if (performerType != IConstance.CompilationUnitType.entity
                        && performerType != IConstance.CompilationUnitType.reference
                        && performerType != IConstance.CompilationUnitType.role) {
                    errors.add("Role take declaration " + mapper.getName()
                            + " uses " + performer.getName() + " (" + roleTakeTypeLabel(performerType)
                            + ") as a performer; performers must be Entity, Reference, or Role participants.");
                }
                validateRoleOverloadedPerformerFields(mapper, performer, errors);
            }
        }
        return errors;
    }

    private Set<String> roleNamesWithTakePerformer() {
        Set<String> roleNames = new TreeSet<>();
        for (SppCompilationUnit unit : getMapsOfClass().values()) {
            if (!(unit instanceof SppRoleMapper mapper)
                    || mapper.getRole() == null
                    || mapper.getRole().getType() != IConstance.CompilationUnitType.role
                    || roleMapperPerformers(mapper).isEmpty()) {
                continue;
            }
            roleNames.add(mapper.getRole().getName());
        }
        for (var unit : mapsOfClass.values()) if (unit instanceof SppClass role
                && role.getType() == IConstance.CompilationUnitType.role
                && role.getSppFieldList().stream().anyMatch(field -> field.getType().getType() != null
                    && Set.of(IConstance.CompilationUnitType.role,IConstance.CompilationUnitType.entity,IConstance.CompilationUnitType.reference).contains(field.getType().getType())))
            roleNames.add(role.getName());
        return roleNames;
    }

    private void validateRoleOverloadedPerformerFields(SppRoleMapper mapper, SppClass performer,
                                                       List<String> errors) {
        if (mapper == null || mapper.getRole() == null || performer == null) {
            return;
        }
        SppClass role = mapper.getRole();
        for (SppField performerField : performer.getSppFieldList()) {
            if (performerField == null || performerField.getName() == null) {
                continue;
            }
            SppField roleField = role.getField(performerField.getName());
            if (roleField == null || sameFieldType(roleField, performerField)) {
                continue;
            }
            errors.add("Role " + role.getName()
                    + " field " + roleField.getName()
                    + " overloads performer " + performer.getName() + "." + performerField.getName()
                    + " but changes type from " + fieldTypeName(performerField)
                    + " to " + fieldTypeName(roleField)
                    + "; role field overloads must keep the same type as the performer field"
                    + "; actions=alignRoleOverloadFieldType.");
        }
    }

    private static boolean sameFieldType(SppField left, SppField right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.getArrayDimension() == right.getArrayDimension()
                && Objects.equals(fieldBaseTypeName(left), fieldBaseTypeName(right));
    }

    private static String fieldTypeName(SppField field) {
        if (field == null) {
            return "unknown";
        }
        return fieldBaseTypeName(field) + field.getArrayString();
    }

    private static String fieldBaseTypeName(SppField field) {
        return field == null || field.getType() == null || field.getType().getName() == null
                ? "unknown"
                : field.getType().getName();
    }

    private static String roleTakeTypeLabel(IConstance.CompilationUnitType type) {
        return type == null ? "unknown" : type.name();
    }

    public List<String> validateRoleOnlyServiceBoundaryDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if (!"SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())) {
                continue;
            }
            errors.add("Service/scenario " + plan.serviceName()
                    + " declares non-Role participants " + String.join(", ", plan.paths())
                    + "; services and scenarios must accept Role or Contract participants at the boundary. "
                    + serviceNonRoleParticipantStrictActionSummary(plan));
        }
        return List.copyOf(errors);
    }

    public List<String> validateBlockedRoleOnlyServiceBoundaryDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if (!"SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                    || !hasBlockedRoleOnlyServiceBoundaryPath(plan)) {
                continue;
            }
            errors.add("Service/scenario " + plan.serviceName()
                    + " declares non-Role participants " + String.join(", ", plan.paths())
                    + "; services and scenarios must accept Role or Contract participants at the boundary. "
                    + serviceNonRoleParticipantStrictActionSummary(plan));
        }
        return List.copyOf(errors);
    }

    public List<String> validateServiceContextDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service)) {
                continue;
            }
            for (SppLocalVar parameter : service.getServiceBody().getSppLocalVarList()) {
                if (!parameter.isContextParameter()) {
                    continue;
                }
                if (!(parameter.getType() instanceof SppClass parameterType)
                        || parameterType.getType() != IConstance.CompilationUnitType.contract) {
                    errors.add("Service/scenario " + service.getFuncName()
                            + " declares context parameter " + parameter.getName()
                            + ":" + typedName(parameter)
                            + "; only Contract values can be service or scenario context.");
                }
                if (parameter.getArrayDimension() != 0) {
                    errors.add("Service/scenario " + service.getFuncName()
                            + " declares array context parameter " + parameter.getName()
                            + ":" + typedName(parameter)
                            + "; Contract context must be singular.");
                }
            }
        }
        return List.copyOf(errors);
    }

    private boolean hasBlockedRoleOnlyServiceBoundaryPath(AppSemanticMigrationPlan plan) {
        if (plan == null || plan.paths() == null || plan.paths().isEmpty()) {
            return true;
        }
        for (int index = 0; index < plan.paths().size(); index++) {
            String path = plan.paths().get(index);
            String participantType = serviceParticipantType(path);
            String category = serviceNonRoleParticipantCategory(plan, index, participantType);
            if ("contracts".equals(category)) {
                return true;
            }
            if (!serviceNonRoleParticipantStrictActionPlan(plan.serviceName(), participantType, category)
                    .roleOnlyReady()) {
                return true;
            }
        }
        return false;
    }

    private String serviceNonRoleParticipantStrictActionSummary(AppSemanticMigrationPlan plan) {
        if (plan == null || plan.paths() == null || plan.paths().isEmpty()) {
            return "Role-only migration action: declare Role participants and move concrete performers to runtime identity.";
        }
        List<String> actions = new ArrayList<>();
        for (int index = 0; index < plan.paths().size(); index++) {
            String path = plan.paths().get(index);
            String participantType = serviceParticipantType(path);
            String category = serviceNonRoleParticipantCategory(plan, index, participantType);
            actions.add(path + " -> " + serviceNonRoleParticipantStrictAction(
                    plan.serviceName(), participantType, category));
        }
        return "Role-only migration actions: " + String.join("; ", actions) + ".";
    }

    private String serviceNonRoleParticipantStrictAction(String serviceName,
                                                        String participantType,
                                                        String category) {
        return serviceNonRoleParticipantStrictActionPlan(serviceName, participantType, category).actionSummary();
    }

    private ServiceParticipantStrictActionPlan serviceNonRoleParticipantStrictActionPlan(String serviceName,
                                                                                       String participantType,
                                                                                       String category) {
        List<String> serviceRoleTypes = serviceRoleParameterTypes(serviceName);
        Map<String, List<String>> serviceRoleParameterNamesByType = serviceRoleParameterNamesByType(serviceName);
        List<String> targetRoles = List.of();
        List<String> targetRoleParams = List.of();
        List<String> candidateRoles = List.of();
        String action;
        if ("contracts".equals(category)) {
            action = "CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO";
            if (serviceUsesQuantumResourceBoundary(serviceName)) {
                return ServiceParticipantStrictActionPlan.of("CONTRACT_TO_QUANTUM_RESOURCE_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), List.of(), true,
                        List.of("APP_QUANTUM_RESOURCE_BOUND", "QUANTUM_RESOURCE_BOUNDARY",
                                "IDEMPOTENCY_REVERSAL_INTERNAL"));
            }
            if (serviceUsesAppDdlObjectTransactionAdapter(serviceName, participantType)) {
                return ServiceParticipantStrictActionPlan.of("CONTRACT_TO_OBJECT_TRANSACTION_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), List.of(), true,
                        List.of("APP_DDL_SNAPSHOT_BOUND", "OBJECT_TRANSACTION_BOUNDARY"));
            }
            if (serviceUsesAppDdlRepositoryAdapter(serviceName, participantType)) {
                return ServiceParticipantStrictActionPlan.of("CONTRACT_TO_OBJECT_REPOSITORY_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), List.of(), true,
                        List.of("APP_DDL_REPOSITORY_BOUND", "OBJECT_REPOSITORY_BOUNDARY"));
            }
            if (serviceRoleTypes.isEmpty()) {
                return ServiceParticipantStrictActionPlan.of(action, false, serviceRoleTypes, List.of(),
                        List.of(), false, List.of("MISSING_ROLE_PARTICIPANT", "MISSING_TARGET_ROLE"));
            }
            targetRoles = serviceRoleTypes;
            targetRoleParams = targetRoleParameterNames(targetRoles, serviceRoleParameterNamesByType);
            boolean ready = !targetRoleParams.isEmpty();
            return ServiceParticipantStrictActionPlan.of(action, true, targetRoles, targetRoleParams,
                    List.of(), ready, serviceParticipantStrictReadinessReasons(ready, true, ready, action));
        }
        if ("entities".equals(category)) {
            action = "ENTITY_TO_ROLE_PERFORMER_OR_REPOSITORY_QUERY";
            if (serviceUsesQuantumResourceBoundary(serviceName)) {
                return ServiceParticipantStrictActionPlan.of("ENTITY_TO_QUANTUM_RESOURCE_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), List.of(), true,
                        List.of("APP_QUANTUM_RESOURCE_BOUND", "QUANTUM_RESOURCE_BOUNDARY",
                                "IDEMPOTENCY_REVERSAL_INTERNAL"));
            }
            if (serviceUsesAppDdlObjectTransactionAdapter(serviceName, participantType)) {
                return ServiceParticipantStrictActionPlan.of("ENTITY_TO_OBJECT_TRANSACTION_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), List.of(), true,
                        List.of("APP_DDL_SNAPSHOT_BOUND", "OBJECT_TRANSACTION_BOUNDARY"));
            }
            if (serviceUsesAppDdlRepositoryAdapter(serviceName, participantType)) {
                return ServiceParticipantStrictActionPlan.of("ENTITY_TO_OBJECT_REPOSITORY_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), List.of(), true,
                        List.of("APP_DDL_REPOSITORY_BOUND", "OBJECT_REPOSITORY_BOUNDARY"));
            }
            targetRoles = serviceRoleTypes.stream()
                    .filter(roleType -> rolePerformerNames(roleType).contains(participantType))
                    .distinct()
                    .toList();
            targetRoleParams = targetRoleParameterNames(targetRoles, serviceRoleParameterNamesByType);
            if (!targetRoles.isEmpty()) {
                boolean ready = !targetRoleParams.isEmpty();
                return ServiceParticipantStrictActionPlan.of(action, !serviceRoleTypes.isEmpty(), targetRoles,
                        targetRoleParams, List.of(), ready,
                        serviceParticipantStrictReadinessReasons(ready, !serviceRoleTypes.isEmpty(), ready, action));
            }
            candidateRoles = roleTypesPerformedBy(participantType);
            return ServiceParticipantStrictActionPlan.of(action, !serviceRoleTypes.isEmpty(), List.of(), List.of(),
                    candidateRoles, false, serviceParticipantStrictReadinessReasons(false,
                            !serviceRoleTypes.isEmpty(), false, action));
        }
        if ("references".equals(category)) {
            action = "REFERENCE_TO_REMOTE_ROLE_ADAPTER";
            candidateRoles = roleTypesPerformedBy(participantType);
            if (remoteServiceUsesExternalAdapter(serviceName)) {
                return ServiceParticipantStrictActionPlan.of("REFERENCE_TO_EXTERNAL_DOMAIN_ADAPTER",
                        !serviceRoleTypes.isEmpty(), List.of(), List.of(), candidateRoles, true,
                        List.of("EXTERNAL_REMOTE_ADAPTER_BOUND", "REFERENCE_BOUNDARY_EXTERNALIZED"));
            }
            return ServiceParticipantStrictActionPlan.of(action, !serviceRoleTypes.isEmpty(), List.of(), List.of(),
                    candidateRoles, false, serviceParticipantStrictReadinessReasons(false,
                            !serviceRoleTypes.isEmpty(), false, action));
        }
        action = "KEEP_ROLE_PARTICIPANT";
        return ServiceParticipantStrictActionPlan.of(action, !serviceRoleTypes.isEmpty(), serviceRoleTypes,
                targetRoleParameterNames(serviceRoleTypes, serviceRoleParameterNamesByType), List.of(), true,
                serviceParticipantStrictReadinessReasons(true, !serviceRoleTypes.isEmpty(), true, action));
    }

    private List<String> targetRoleParameterNames(List<String> targetRoleTypes,
                                                  Map<String, List<String>> serviceRoleParameterNamesByType) {
        if (targetRoleTypes == null || targetRoleTypes.isEmpty()
                || serviceRoleParameterNamesByType == null || serviceRoleParameterNamesByType.isEmpty()) {
            return List.of();
        }
        return targetRoleTypes.stream()
                .flatMap(roleType -> serviceRoleParameterNamesByType.getOrDefault(roleType, List.of()).stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> serviceParticipantStrictReadinessReasons(boolean roleOnlyReady,
                                                                  boolean roleParticipantPresent,
                                                                  boolean targetRolePresent,
                                                                  String action) {
        List<String> reasons = new ArrayList<>();
        if (roleOnlyReady) {
            reasons.add("ROLE_PARTICIPANT_PRESENT");
            reasons.add("TARGET_ROLE_PRESENT");
            reasons.add("NON_ROLE_PARTICIPANT_REMOVABLE");
            return List.copyOf(reasons);
        }
        if (!roleParticipantPresent) {
            reasons.add("MISSING_ROLE_PARTICIPANT");
        }
        if (!targetRolePresent) {
            reasons.add("MISSING_TARGET_ROLE");
        }
        if ("REFERENCE_TO_REMOTE_ROLE_ADAPTER".equals(action)) {
            reasons.add("REMOTE_ROLE_ADAPTER_REQUIRED");
        }
        if (reasons.isEmpty()) {
            reasons.add("MIGRATION_ACTION_NOT_READY");
        }
        return List.copyOf(reasons);
    }

    private record ServiceParticipantStrictActionPlan(String action,
                                                      boolean roleParticipantPresent,
                                                      List<String> targetRoles,
                                                      List<String> targetRoleParams,
                                                      List<String> candidateRoles,
                                                      boolean roleOnlyReady,
                                                      List<String> readinessReasons) {
        static ServiceParticipantStrictActionPlan of(String action,
                                                     boolean roleParticipantPresent,
                                                     List<String> targetRoles,
                                                     List<String> targetRoleParams,
                                                     List<String> candidateRoles,
                                                     boolean roleOnlyReady,
                                                     List<String> readinessReasons) {
            return new ServiceParticipantStrictActionPlan(
                    action,
                    roleParticipantPresent,
                    List.copyOf(targetRoles == null ? List.of() : targetRoles),
                    List.copyOf(targetRoleParams == null ? List.of() : targetRoleParams),
                    List.copyOf(candidateRoles == null ? List.of() : candidateRoles),
                    roleOnlyReady,
                    List.copyOf(readinessReasons == null ? List.of() : readinessReasons));
        }

        String actionSummary() {
            return "action=" + valueOrDash(action)
                    + "; roleParticipantPresent=" + roleParticipantPresent
                    + "; roleOnlyReady=" + roleOnlyReady
                    + "; targetRoles=" + valueOrDash(String.join("+", targetRoles))
                    + "; targetRoleParams=" + valueOrDash(String.join("+", targetRoleParams))
                    + "; candidateRoles=" + valueOrDash(String.join("+", candidateRoles))
                    + "; reasons=" + valueOrDash(String.join("+", readinessReasons));
        }
    }

    private Map<String, List<String>> serviceRoleParameterNamesByType(String serviceName) {
        SppService service = findServiceByName(serviceName);
        if (service == null || service.getServiceBody() == null) {
            return Map.of();
        }
        Map<String, List<String>> parameterNamesByType = new LinkedHashMap<>();
        for (SppLocalVar parameter : service.getServiceBody().getSppLocalVarList()) {
            if (!(parameter.getType() instanceof SppClass parameterType)) {
                continue;
            }
            SppCompilationUnit declaredType = mapsOfClass.get(parameterType.getName());
            if (declaredType == null || declaredType.getType() != IConstance.CompilationUnitType.role) {
                continue;
            }
            parameterNamesByType
                    .computeIfAbsent(parameterType.getName(), ignored -> new ArrayList<>())
                    .add(parameter.getName());
        }
        return parameterNamesByType.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private String serviceNonRoleParticipantCategory(AppSemanticMigrationPlan plan, int pathIndex,
                                                    String participantType) {
        if (plan != null && plan.participantCategories() != null
                && pathIndex >= 0 && pathIndex < plan.participantCategories().size()) {
            String category = plan.participantCategories().get(pathIndex);
            if (category != null && !category.isBlank()) {
                return category;
            }
        }
        SppCompilationUnit participantUnit = mapsOfClass.get(participantType);
        if (participantUnit == null) {
            return "";
        }
        return switch (participantUnit.getType()) {
            case contract -> "contracts";
            case entity -> "entities";
            case reference -> "references";
            case role -> "roles";
            default -> "";
        };
    }

    private List<String> serviceRoleParameterTypes(String serviceName) {
        SppService service = findServiceByName(serviceName);
        if (service == null || service.getServiceBody() == null) {
            return List.of();
        }
        List<String> roleTypes = new ArrayList<>();
        for (SppLocalVar parameter : service.getServiceBody().getSppLocalVarList()) {
            if (!(parameter.getType() instanceof SppClass parameterType)) {
                continue;
            }
            SppCompilationUnit declaredType = mapsOfClass.get(parameterType.getName());
            if (declaredType != null && declaredType.getType() == IConstance.CompilationUnitType.role) {
                roleTypes.add(parameterType.getName());
            }
        }
        return roleTypes.stream().distinct().toList();
    }

    private boolean serviceUsesQuantumResourceBoundary(String serviceName) {
        SppService service = findServiceByName(serviceName);
        return service != null
                && service.getScopeItem() != null
                && service.getScopeItem().getTransactionType() == IConstance.TransactionType.quantum
                && service.getScopeItem().hasQuantumConservationAttributes();
    }

    private boolean serviceUsesAppDdlObjectTransactionAdapter(String serviceName, String participantType) {
        SppService service = findServiceByName(serviceName);
        if (service == null || service.getScopeItem() == null || participantType == null || participantType.isBlank()) {
            return false;
        }
        ScopeItem scopeItem = service.getScopeItem();
        if (!scopeItem.isLocal() || !requiresLocalAtomicUpdateBinding(scopeItem)) {
            return false;
        }
        for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
            if (rule.appDdlInferred()
                    && rule.source() == ObjectSnapshotRule.Source.argument
                    && participantType.equals(rule.getObjectType())) {
                return true;
            }
        }
        return false;
    }

    private boolean serviceUsesAppDdlRepositoryAdapter(String serviceName, String participantType) {
        SppService service = findServiceByName(serviceName);
        if (service == null || service.getScopeItem() == null || participantType == null || participantType.isBlank()) {
            return false;
        }
        Map<String, AppTable> tableByBusinessType = appTableByBusinessType();
        for (RepositoryHydrationRule rule : service.getScopeItem().getRepositoryHydrationRules()) {
            if (participantType.equals(rule.getObjectType())
                    && (rule.appDdlInferred()
                    || repositoryAppDdlKeyEvidence(rule, tableByBusinessType).closed())) {
                return true;
            }
        }
        return false;
    }

    private boolean remoteServiceUsesExternalAdapter(String serviceName) {
        AppRemoteAdapter adapter = remoteAdapterForService(serviceName);
        return adapter != null && "HTTP_JSON_EXTERNAL".equals(remoteAdapterRole(adapter.getAdapterName()));
    }

    private AppRemoteAdapter remoteAdapterForService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return null;
        }
        for (AppRemoteAdapter adapter : ruleBlock.getAppRemoteAdapters()) {
            if (serviceName.equals(adapter.getName())) {
                return adapter;
            }
        }
        return null;
    }

    private SppService findServiceByName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return null;
        }
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppService service && serviceName.equals(service.getFuncName())) {
                return service;
            }
        }
        return null;
    }

    public List<String> validateAppDdlReplacementDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if ("REPOSITORY_ESCAPE_HATCH".equals(plan.migrationType())) {
                if (plan.objectKeyAppDdlDerived() && plan.appDdlObjectKeyPushdown()) {
                    String action = "replaceRepositoryArgumentEscapeHatch+useRepositoryTypeOrRepositoryByIndex";
                    errors.add("Repository query " + plan.serviceName()
                            + " uses explicit argument key paths " + String.join(", ", plan.paths())
                            + " even though APP DDL " + plan.objectKeySource()
                            + " already covers " + String.join(", ", plan.appDdlObjectKeyPaths())
                            + "; replace the escape hatch with repository(Type...) or repository(Type by index ...) syntax"
                            + "; actions=" + action
                            + "; action=" + action + ".");
                } else {
                    String action = "declareAppDdlKeyOrIndex+replaceRepositoryArgumentEscapeHatch"
                            + "+useRepositoryTypeOrRepositoryByIndex";
                    errors.add("Repository query " + plan.serviceName()
                            + " uses explicit argument key paths " + String.join(", ", plan.paths())
                            + "; declare APP DDL primary keys, foreign keys or named indexes and use repository(Type...) syntax"
                            + "; actions=" + action
                            + "; action=" + action + ".");
                }
            } else if ("SNAPSHOT_ESCAPE_HATCH".equals(plan.migrationType())) {
                if (isTransactionSnapshotMigrationPlan(plan)) {
                    continue;
                }
                String action = "declareAppDdlKeyOrIndex+replaceSnapshotArgumentEscapeHatch"
                        + "+useSnapshotTypeOrSnapshotByIndex";
                errors.add("Snapshot rule for service " + plan.serviceName()
                        + " object " + plan.objectType()
                        + " uses explicit object key paths " + String.join(", ", plan.paths())
                        + "; declare APP DDL primary keys or named indexes and use snapshot(Type...) syntax"
                        + "; actions=" + action
                        + "; action=" + action + ".");
            }
        }
        return List.copyOf(errors);
    }

    private boolean isTransactionSnapshotMigrationPlan(AppSemanticMigrationPlan plan) {
        return "TransactionSnapshot".equals(plan.objectType())
                && plan.paths().contains("globalSerialNumber");
    }

    public List<String> validateQuantumServiceInternalProtocolDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if (!"QUANTUM_SERVICE_INTERNAL_PROTOCOL".equals(plan.migrationType())) {
                continue;
            }
            String actions = quantumServiceInternalProtocolActions(plan);
            errors.add("Quantum service " + plan.serviceName()
                    + " still declares APP object recovery protocol " + String.join(", ", plan.participantCategories())
                    + " for attributes " + String.join(", ", plan.paths())
                    + "; pure quantum service profiles must keep idempotency, entanglement audit and reversal inside the quantum service protocol"
                    + "; reason=objectRecoveryProtocol"
                    + "; actions=" + actions
                    + "; action=" + actions + ".");
        }
        return List.copyOf(errors);
    }

    public List<String> validateQuantumServiceExternalImplementationDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppQuantumStoreAdapter adapter : ruleBlock.getAppQuantumStoreAdapters()) {
            if (!"QUANTUM_SERVICE".equals(quantumImplementationStrategy(adapter))) {
                continue;
            }
            if (quantumServiceExternalImplementationReady(adapter)) {
                continue;
            }
            String reason = quantumServiceExternalImplementationReadinessReason(adapter);
            String action = quantumServiceExternalImplementationActions(reason);
            errors.add("Quantum service store " + adapter.getName()
                    + " is only a contract boundary for adapter " + adapter.getAdapterName()
                    + "; strict production profiles must bind a real absolute http(s) quantum service endpoint"
                    + "; reason=" + reason
                    + "; endpoint=" + adapter.getConfiguration().getOrDefault("endpoint", "")
                    + "; actions=" + action
                    + "; action=" + action
                    + ".");
        }
        return List.copyOf(errors);
    }

    private String quantumServiceExternalImplementationActions(String reason) {
        return switch (reason == null ? "" : reason) {
            case "endpoint" -> "configureQuantumServiceEndpoint+bindProductionQuantumService";
            case "endpointInvalid" -> "configureAbsoluteHttpQuantumServiceEndpoint+bindProductionQuantumService";
            case "developmentEndpoint" -> "replaceDevelopmentQuantumServiceEndpoint+bindProductionQuantumService";
            case "placeholderEndpoint" -> "replaceQuantumServiceEndpointPlaceholder+bindProductionQuantumService";
            default -> "bindProductionQuantumService";
        };
    }

    private boolean quantumServiceExternalImplementationReady(AppQuantumStoreAdapter adapter) {
        return adapter != null
                && "QUANTUM_SERVICE".equals(quantumImplementationStrategy(adapter))
                && absoluteHttpEndpoint(quantumServiceEndpoint(adapter))
                && !quantumServiceDevelopmentEndpoint(quantumServiceEndpoint(adapter))
                && !quantumServicePlaceholderEndpoint(quantumServiceEndpoint(adapter));
    }

    private String quantumServiceExternalImplementationReadinessReason(AppQuantumStoreAdapter adapter) {
        if (adapter == null) {
            return "store";
        }
        String endpoint = quantumServiceEndpoint(adapter);
        if (endpoint == null || endpoint.isBlank()) {
            return "endpoint";
        }
        if (!absoluteHttpEndpoint(endpoint)) {
            return "endpointInvalid";
        }
        if (quantumServiceDevelopmentEndpoint(endpoint)) {
            return "developmentEndpoint";
        }
        if (quantumServicePlaceholderEndpoint(endpoint)) {
            return "placeholderEndpoint";
        }
        return "-";
    }

    private String quantumServiceEndpoint(AppQuantumStoreAdapter adapter) {
        if (adapter == null) {
            return null;
        }
        return deploymentPlaceholderDefault(adapter.getConfiguration().get("endpoint"));
    }

    private String deploymentPlaceholderDefault(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            int separator = trimmed.indexOf(':');
            String propertyName = separator > 2
                    ? trimmed.substring(2, separator)
                    : trimmed.substring(2, trimmed.length() - 1);
            String propertyValue = System.getProperty(propertyName);
            if (propertyValue != null) {
                return propertyValue;
            }
            if (separator > 2 && separator < trimmed.length() - 1) {
                return trimmed.substring(separator + 1, trimmed.length() - 1);
            }
        }
        return value;
    }

    private boolean absoluteHttpEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean quantumServicePlaceholderEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return host != null
                    && ("example.test".equalsIgnoreCase(host)
                    || host.toLowerCase(Locale.ROOT).endsWith(".example.test")
                    || "example.com".equalsIgnoreCase(host)
                    || host.toLowerCase(Locale.ROOT).endsWith(".example.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean quantumServiceDevelopmentEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(lower)
                    || "127.0.0.1".equals(lower)
                    || "0.0.0.0".equals(lower)
                    || "::1".equals(lower);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String quantumServiceInternalProtocolActions(AppSemanticMigrationPlan plan) {
        List<String> actions = new ArrayList<>();
        if (plan.participantCategories().contains("objectSnapshot")) {
            actions.add("snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE");
            actions.add("objectSnapshotManaged=false");
        }
        if (plan.participantCategories().contains("objectCompensation")) {
            actions.add("reversalExecution=INPUT_NEGATIVE_DELTA");
            actions.add("objectCompensation=false");
        }
        if (plan.participantCategories().contains("frameworkIdempotency")) {
            actions.add("idempotencyBoundary=QUANTUM_SERVICE_INTERNAL");
        }
        actions.add("objectRecoveryRequired=false");
        return String.join("+", actions);
    }

    public List<String> validateExternalRemoteAdapterDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if (!"REMOTE_REFERENCE_ADAPTER".equals(plan.migrationType())) {
                continue;
            }
            errors.add("Remote service " + plan.serviceName()
                    + " is still backed by reference adapter " + plan.adapterName()
                    + "; bind a real external domain adapter in PHY for production profiles"
                    + "; target=" + valueOrDash(plan.targetKind()) + "/" + valueOrDash(plan.targetName())
                    + "; actions=replaceReferenceRemoteAdapter+bindExternalDomainAdapter"
                    + "; action=replaceReferenceRemoteAdapter+bindExternalDomainAdapter.");
        }
        return List.copyOf(errors);
    }

    public List<String> validateRemoteExternalEndpointDeclarations() {
        List<String> errors = new ArrayList<>();
        for (AppRemoteAdapter adapter : ruleBlock.getAppRemoteAdapters()) {
            String adapterName = adapter.getAdapterName() == null ? "" : adapter.getAdapterName();
            if (!isHttpJsonRemoteAdapter(adapterName)) {
                continue;
            }
            String endpoint = remoteExternalEndpoint(adapter);
            if (remoteExternalEndpointProductionReady(endpoint)) {
                continue;
            }
            String reason = remoteExternalEndpointReadinessReason(endpoint);
            String action = remoteExternalEndpointActions(reason);
            errors.add("Remote service " + adapter.getName()
                    + " uses non-production external endpoint " + adapter.getConfiguration().getOrDefault("endpoint", "")
                    + "; strict production profiles must bind a real external domain endpoint"
                    + "; reason=" + reason
                    + "; actions=" + action
                    + "; action=" + action + ".");
        }
        return List.copyOf(errors);
    }

    private String remoteExternalEndpointActions(String reason) {
        return switch (reason == null ? "" : reason) {
            case "endpoint" -> "configureRemoteEndpoint+bindProductionExternalDomain";
            case "endpointInvalid" -> "configureAbsoluteHttpRemoteEndpoint+bindProductionExternalDomain";
            case "developmentEndpoint" -> "replaceDevelopmentRemoteEndpoint+bindProductionExternalDomain";
            case "placeholderEndpoint" -> "replaceRemoteEndpointPlaceholder+bindProductionExternalDomain";
            default -> "bindProductionExternalDomain";
        };
    }

    private String remoteExternalEndpoint(AppRemoteAdapter adapter) {
        if (adapter == null) {
            return null;
        }
        return configValueForStaticValidation(adapter.getConfiguration().get("endpoint")).orElse(null);
    }

    private boolean remoteExternalEndpointProductionReady(String endpoint) {
        return absoluteHttpEndpoint(endpoint)
                && !developmentEndpoint(endpoint)
                && !placeholderEndpoint(endpoint);
    }

    private String remoteExternalEndpointReadinessReason(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "endpoint";
        }
        if (!absoluteHttpEndpoint(endpoint)) {
            return "endpointInvalid";
        }
        if (developmentEndpoint(endpoint)) {
            return "developmentEndpoint";
        }
        if (placeholderEndpoint(endpoint)) {
            return "placeholderEndpoint";
        }
        return "-";
    }

    private boolean developmentEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(lower)
                    || "127.0.0.1".equals(lower)
                    || "0.0.0.0".equals(lower)
                    || "::1".equals(lower);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean placeholderEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            return "example.test".equals(lower)
                    || lower.endsWith(".example.test")
                    || "example.com".equals(lower)
                    || lower.endsWith(".example.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public List<String> validatePlatformColumnBusinessShadowDeclarations() {
        List<String> errors = new ArrayList<>();
        String action = "removeTechnicalIdentityFromSppBusinessType+keepOnlyTrueBusinessCredentials";
        validateAppTableDeclarations();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if (!"SPP_TECHNICAL_FIELD_PLATFORM_COLUMN".equals(plan.migrationType())) {
                continue;
            }
            errors.add("APP DDL table " + plan.tableName()
                    + " already carries technical-looking SPP fields " + String.join(", ", plan.paths())
                    + " as platform columns; remove them from the SPP business type unless they are true business credentials"
                    + " (objectType=" + valueOrDash(plan.objectType())
                    + ", paths=" + valueOrDash(String.join("+", plan.paths()))
                    + ", actions=" + action
                    + ", action=" + action + ").");
        }
        return List.copyOf(errors);
    }

    public List<String> validateAppDdlPlatformPrimaryKeyDeclarations() {
        List<String> errors = new ArrayList<>();
        String action = "replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity+keepRuntimeIdentityOutsideSppRole";
        validateAppTableDeclarations();
        for (AppSemanticMigrationPlan plan : appSemanticMigrationPlans()) {
            if (!"APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())) {
                continue;
            }
            String identityKind = appDdlPlatformPrimaryKeyIdentityKind(plan);
            if ("ROLE_PERFORMER_IDENTITY".equals(identityKind)
                    || "COMPOSITION_IDENTITY".equals(identityKind)
                    || "BUSINESS_FIELD_IDENTITY".equals(identityKind)) {
                continue;
            }
            errors.add("APP DDL table " + plan.tableName()
                    + " still carries platform primary key identity " + String.join(", ", plan.paths())
                    + "; replace sidecar primary keys with business or composition identity"
                    + " and keep runtime identity outside SPP roles"
                    + " (objectType=" + valueOrDash(plan.objectType())
                    + ", paths=" + valueOrDash(String.join("+", plan.paths()))
                    + ", identityKind=" + identityKind
                    + ", actions=" + action
                    + ", action=" + action + ").");
        }
        return List.copyOf(errors);
    }

    public List<String> validateCurrentSnapshotAppDdlKeyDeclarations() {
        List<String> errors = new ArrayList<>();
        Map<String, AppTable> tableByBusinessType = appTableByBusinessType();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().hasCurrentObjectSnapshotRules()) {
                continue;
            }
            for (ObjectSnapshotRule rule : service.getScopeItem().getObjectSnapshotRules()) {
                if (rule.source() != ObjectSnapshotRule.Source.current || !hasValidObjectIdPath(rule.objectPath())) {
                    continue;
                }
                CurrentSnapshotAppDdlKeyEvidence keyEvidence =
                        currentSnapshotAppDdlKeyEvidence(rule, tableByBusinessType);
                if (keyEvidence.closed()) {
                    continue;
                }
                errors.add(currentSnapshotAppDdlKeyError(rule, service, keyEvidence));
            }
        }
        return List.copyOf(errors);
    }

    public int appSemanticRepositoryQueryPlanCount() {
        return appSemanticRepositoryRules().size();
    }

    public int appSemanticRepositoryReplacementReadyCount() {
        int count = 0;
        for (RepositoryHydrationRule rule : appSemanticRepositoryRules()) {
            if (rule.appDdlInferred()) {
                count++;
            }
        }
        return count;
    }

    public List<String> appSemanticRepositoryReplacementSources() {
        return appSemanticRepositoryRules().stream()
                .map(rule -> rule.appDdlInferred() ? "APP_DDL_INFERRED" : "EXPLICIT_REPOSITORY_ARGUMENT")
                .distinct()
                .toList();
    }

    public List<String> appSemanticRepositoryReplacementReadyServiceNames() {
        List<String> serviceNames = new ArrayList<>();
        for (SppService service : servicesWithRepositoryRules()) {
            if (service.getScopeItem().getRepositoryHydrationRules().stream()
                    .anyMatch(RepositoryHydrationRule::appDdlInferred)) {
                serviceNames.add(service.getFuncName());
            }
        }
        return serviceNames.stream().distinct().toList();
    }

    public List<String> appSemanticMissingRepositoryReplacementReadyServiceNames() {
        List<String> serviceNames = new ArrayList<>();
        for (SppService service : servicesWithRepositoryRules()) {
            if (service.getScopeItem().getRepositoryHydrationRules().stream()
                    .anyMatch(rule -> !rule.appDdlInferred())) {
                serviceNames.add(service.getFuncName());
            }
        }
        return serviceNames.stream().distinct().toList();
    }

    public List<AppSemanticMigrationPlan> appSemanticMigrationPlans() {
        List<AppSemanticMigrationPlan> plans = new ArrayList<>();
        Map<String, AppTable> tableByBusinessType = appTableByBusinessType();
        Set<String> tableBackedRoleFieldMigrationTypes = new LinkedHashSet<>();
        Set<String> rolesWithPerformer = roleNamesWithTakePerformer();
        for (AppTables appTables : ruleBlock.getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (appTable.getName() == null || appTable.getEntity() == null) {
                    continue;
                }
                List<String> objectBackedPrimaryKeyPaths = appTableObjectBackedPrimaryKeyPaths(
                        appTable, tableByBusinessType, new LinkedHashSet<>());
                if (!objectBackedPrimaryKeyPaths.isEmpty()) {
                    plans.add(new AppSemanticMigrationPlan("OBJECT_BACKED_PRIMARY_KEY", "APP_DDL_TABLE",
                            appTable.getName(), appTable.getName(), null, appTable.getEntity().getName(),
                            null, null, false, false, objectBackedPrimaryKeyPaths,
                            "Keep only business facts in the SPP object; migrate technical identity to APP DDL platform primary keys."));
                }
                List<String> platformPrimaryKeyPaths = appTablePlatformPrimaryKeyMigrationPaths(appTable);
                if (!platformPrimaryKeyPaths.isEmpty()) {
                    AppSemanticMigrationPlan platformPrimaryKeyPlan = new AppSemanticMigrationPlan(
                            "APP_DDL_PLATFORM_PRIMARY_KEY", "APP_DDL_TABLE",
                            appTable.getName(), appTable.getName(), null, appTable.getEntity().getName(),
                            null, null, false, false, platformPrimaryKeyPaths,
                            "APP DDL carries these primary keys as runtime identity; keep them out of SPP business roles and use a stable independent role identity when its composition can evolve.");
                    plans.add(platformPrimaryKeyPlan.withIdentityKind(
                            appDdlPlatformPrimaryKeyIdentityKind(platformPrimaryKeyPlan)));
                }
                List<String> objectBackedIndexPaths = appTableObjectBackedIndexPaths(appTable, tableByBusinessType);
                if (!objectBackedIndexPaths.isEmpty()) {
                    plans.add(new AppSemanticMigrationPlan("OBJECT_BACKED_INDEX", "APP_DDL_TABLE",
                            appTable.getName(), appTable.getName(), null, appTable.getEntity().getName(),
                            null, null, false, false, objectBackedIndexPaths,
                            "Keep only business lookup fields in the SPP object; migrate technical lookup keys to APP DDL platform columns or indexes."));
                }
                List<String> technicalPlatformColumnPaths = appTableTechnicalFieldPlatformColumnPaths(appTable);
                if (!technicalPlatformColumnPaths.isEmpty()) {
                    plans.add(new AppSemanticMigrationPlan("SPP_TECHNICAL_FIELD_PLATFORM_COLUMN", "APP_DDL_TABLE",
                            appTable.getName(), appTable.getName(), null, appTable.getEntity().getName(),
                            null, null, false, false, technicalPlatformColumnPaths,
                            "APP DDL already carries these technical fields as platform columns; keep them in SPP only when they are true business credentials."));
                }
                RoleFieldMigrationEvidence rolePerformerFieldEvidence =
                        rolePerformerFieldEvidence(appTable.getEntity().getName());
                if (!rolePerformerFieldEvidence.paths().isEmpty()) {
                    plans.add(new AppSemanticMigrationPlan("ROLE_PERFORMER_FIELD", "ROLE",
                            appTable.getEntity().getName(), appTable.getName(), null, appTable.getEntity().getName(),
                            null, null, false, false, rolePerformerFieldEvidence.paths(),
                            rolePerformerFieldEvidence.categories(),
                            "Role fields duplicate take(...) performers; model performer identity as APP DDL platform columns or runtime role bindings instead of technical identity fields in SPP."));
                    tableBackedRoleFieldMigrationTypes.add(appTable.getEntity().getName());
                }
                RoleFieldMigrationEvidence roleObjectFieldEvidence =
                        roleObjectFieldEvidence(appTable.getEntity().getName());
                if (!roleObjectFieldEvidence.paths().isEmpty()) {
                    plans.add(new AppSemanticMigrationPlan("ROLE_OBJECT_FIELD", "ROLE",
                            appTable.getEntity().getName(), appTable.getName(), null, appTable.getEntity().getName(),
                            null, null, false, false, roleObjectFieldEvidence.paths(),
                            roleObjectFieldEvidence.categories(),
                            "Role fields should describe role facts only; model object and role relationships through runtime participants, sidecars or APP DDL identity."));
                    tableBackedRoleFieldMigrationTypes.add(appTable.getEntity().getName());
                }
            }
        }
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppClass roleClass)
                    || roleClass.getType() != IConstance.CompilationUnitType.role || roleClass.isSystemBuiltin()) {
                continue;
            }
            if (!rolesWithPerformer.contains(roleClass.getName())) {
                plans.add(new AppSemanticMigrationPlan("ROLE_WITHOUT_PERFORMER", "ROLE",
                        roleClass.getName(), null, null, roleClass.getName(),
                        null, null, false, false, List.of(roleClass.getName()),
                        "Declare take Entity/Reference/Role as " + roleClass.getName()
                                + "; service-oriented roles must be played by runtime participants."));
            }
            if (tableBackedRoleFieldMigrationTypes.contains(roleClass.getName())) {
                continue;
            }
            RoleFieldMigrationEvidence rolePerformerFieldEvidence = rolePerformerFieldEvidence(roleClass.getName());
            if (!rolePerformerFieldEvidence.paths().isEmpty()) {
                plans.add(new AppSemanticMigrationPlan("ROLE_PERFORMER_FIELD", "ROLE",
                        roleClass.getName(), null, null, roleClass.getName(),
                        null, null, false, false, rolePerformerFieldEvidence.paths(),
                        rolePerformerFieldEvidence.categories(),
                        "Role fields duplicate take(...) performers; model performer identity as APP DDL platform columns or runtime role bindings instead of technical identity fields in SPP."));
            }
            RoleFieldMigrationEvidence roleObjectFieldEvidence = roleObjectFieldEvidence(roleClass.getName());
            if (!roleObjectFieldEvidence.paths().isEmpty()) {
                plans.add(new AppSemanticMigrationPlan("ROLE_OBJECT_FIELD", "ROLE",
                        roleClass.getName(), null, null, roleClass.getName(),
                        null, null, false, false, roleObjectFieldEvidence.paths(),
                        roleObjectFieldEvidence.categories(),
                        "Role fields should describe role facts only; model object and role relationships through runtime participants, sidecars or APP DDL identity."));
            }
        }
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service)) {
                continue;
            }
            if (service.getScopeItem() == null) {
                continue;
            }
            if (service.getScopeItem().getAsyncModifierNames().contains("asycn")) {
                plans.add(new AppSemanticMigrationPlan("LEGACY_ASYNC_MODIFIER", "SCOPE_SERVICE",
                        service.getFuncName(), null, service.getFuncName(), null,
                        null, null, false, false, List.of("asycn"),
                        "Replace legacy asycn modifier with async."));
            }
            if (!service.getScopeItem().isImplicitDefaultBinding() && !isDefaultGeneratedService(service)) {
                List<String> nonRoleParticipantPaths = serviceNonRoleParticipantPaths(service);
                if (!nonRoleParticipantPaths.isEmpty()) {
                    plans.add(new AppSemanticMigrationPlan("SERVICE_NON_ROLE_PARTICIPANT",
                            serviceBoundaryTargetKind(service),
                            service.getFuncName(), null, service.getFuncName(), null,
                            null, null, false, false, nonRoleParticipantPaths,
                            serviceNonRoleParticipantCategories(nonRoleParticipantPaths),
                            serviceNonRoleParticipantRecommendation(nonRoleParticipantPaths)));
                }
            }
            for (RepositoryHydrationRule rule : service.getScopeItem().getRepositoryHydrationRules()) {
                if (!rule.appDdlInferred()) {
                    CurrentSnapshotAppDdlKeyEvidence keyEvidence =
                            repositoryAppDdlKeyEvidence(rule, tableByBusinessType);
                    plans.add(new AppSemanticMigrationPlan("REPOSITORY_ESCAPE_HATCH", "REPOSITORY_QUERY",
                            service.getFuncName(), null, service.getFuncName(), rule.getObjectType(),
                            null, null, false, false, repositoryMigrationPaths(rule), List.of(), null, null,
                            keyEvidence.objectKeySource(), keyEvidence.objectKeyAppDdlIndexName(),
                            keyEvidence.objectKeyAppDdlDerived(), true,
                            keyEvidence.appDdlObjectKeyPaths(), keyEvidence.objectKeyColumns(),
                            keyEvidence.appDdlObjectKeyPushdown(), keyEvidence.externalObjectKeyRoots(),
                            "Prefer APP DDL primary keys, foreign keys or named indexes over explicit repository argument paths."));
                }
            }
            for (ObjectSnapshotRule rule : service.getScopeItem().getObjectSnapshotRules()) {
                if (rule.source() == ObjectSnapshotRule.Source.transaction) {
                    continue;
                }
                if (rule.source() == ObjectSnapshotRule.Source.current) {
                    CurrentSnapshotAppDdlKeyEvidence keyEvidence =
                            currentSnapshotAppDdlKeyEvidence(rule, tableByBusinessType);
                    plans.add(new AppSemanticMigrationPlan("CURRENT_SNAPSHOT_CONTEXT", "OBJECT_SNAPSHOT",
                            service.getFuncName(), null, service.getFuncName(), rule.getObjectType(),
                            null, null, false, false, rule.objectIdPaths(), List.of(), null, null,
                            keyEvidence.objectKeySource(), keyEvidence.objectKeyAppDdlIndexName(),
                            keyEvidence.objectKeyAppDdlDerived(), keyEvidence.objectKeyEscapeHatch(),
                            keyEvidence.appDdlObjectKeyPaths(), keyEvidence.objectKeyColumns(),
                            keyEvidence.appDdlObjectKeyPushdown(), keyEvidence.externalObjectKeyRoots(),
                            "Treat current(...) as a scenario context locator; prefer APP DDL aggregate relations or named indexes when the object identity can be modeled statically."));
                    continue;
                }
                if (!rule.appDdlInferred()) {
                    plans.add(new AppSemanticMigrationPlan("SNAPSHOT_ESCAPE_HATCH", "OBJECT_SNAPSHOT",
                            service.getFuncName(), null, service.getFuncName(), rule.getObjectType(),
                            null, null, false, false, rule.objectIdPaths(),
                            "Prefer APP DDL primary keys or named indexes over explicit snapshot object key paths."));
                }
            }
        }
        for (AppRemoteAdapter adapter : ruleBlock.getAppRemoteAdapters()) {
            if (isObjectRepositoryRemoteAdapter(adapter.getAdapterName())) {
                plans.add(new AppSemanticMigrationPlan("REMOTE_REFERENCE_ADAPTER", "REMOTE_SERVICE",
                        adapter.getName(), null, adapter.getName(), null,
                        adapter.getAdapterName(), remoteAdapterRole(adapter.getAdapterName()),
                        true, false, List.of(),
                        "Replace the reference adapter with a real external domain adapter for production deployment."));
            } else if (isHttpJsonRemoteAdapter(adapter.getAdapterName())) {
                plans.add(new AppSemanticMigrationPlan("REMOTE_EXTERNAL_ADAPTER", "REMOTE_SERVICE",
                        adapter.getName(), null, adapter.getName(), null,
                        adapter.getAdapterName(), remoteAdapterRole(adapter.getAdapterName()),
                        false, true, List.of(),
                        "Remote service is bound to an external domain adapter."));
            }
        }
        plans.addAll(quantumServiceInternalProtocolMigrationPlans());
        return List.copyOf(plans);
    }

    private List<AppSemanticMigrationPlan> quantumServiceInternalProtocolMigrationPlans() {
        List<AppSemanticMigrationPlan> plans = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) {
                continue;
            }
            ScopeItem scopeItem = service.getScopeItem();
            if (!scopeItem.isLocal()
                    || scopeItem.getTransactionType() != IConstance.TransactionType.quantum
                    || scopeItem.getQuantumConservationAttributeIds().isEmpty()
                    || (!scopeItem.hasObjectSnapshotRules() && !scopeItem.hasCompensationServices())) {
                continue;
            }
            AppQuantumStoreAdapter adapter = quantumServiceStoreAdapterForAttributes(
                    scopeItem.getQuantumConservationAttributeIds());
            if (adapter == null) {
                continue;
            }
            List<String> protocolSources = quantumServiceInternalProtocolSources(scopeItem);
            plans.add(new AppSemanticMigrationPlan("QUANTUM_SERVICE_INTERNAL_PROTOCOL",
                    "QUANTUM_ACCESS", service.getFuncName(), null, service.getFuncName(), null,
                    adapter.getAdapterName(), quantumStoreAdapterRole(adapter),
                    false, true, distinctPaths(scopeItem.getQuantumConservationAttributeIds()),
                    protocolSources, "QUANTUM_SERVICE_AUDIT_EVIDENCE", "QUANTUM_SERVICE_INTERNAL",
                    "Move the listed framework responsibilities for this quantum resource to the quantum service internal protocol."));
        }
        return plans;
    }

    private List<String> quantumServiceInternalProtocolSources(ScopeItem scopeItem) {
        List<String> sources = new ArrayList<>();
        if (scopeItem != null && scopeItem.hasObjectSnapshotRules()) {
            sources.add("objectSnapshot");
        }
        if (scopeItem != null && scopeItem.hasCompensationServices()) {
            sources.add("objectCompensation");
        }
        sources.add("frameworkIdempotency");
        return sources;
    }

    private AppQuantumStoreAdapter quantumServiceStoreAdapterForAttributes(List<String> attributeIds) {
        AppQuantumStoreAdapter adapter = null;
        for (String attributeId : new LinkedHashSet<>(attributeIds)) {
            String storeName = quantumStoreNameForAttribute(attributeId);
            if (storeName == null) {
                return null;
            }
            AppQuantumStoreAdapter currentAdapter = quantumStoreAdapter(storeName);
            if (currentAdapter == null || !"QUANTUM_SERVICE".equals(quantumImplementationStrategy(currentAdapter))) {
                return null;
            }
            if (adapter == null) {
                adapter = currentAdapter;
            } else if (!Objects.equals(adapter.getName(), currentAdapter.getName())) {
                return null;
            }
        }
        return adapter;
    }

    private AppQuantumStoreAdapter quantumStoreAdapter(String storeName) {
        for (AppQuantumStoreAdapter adapter : ruleBlock.getAppQuantumStoreAdapters()) {
            if (Objects.equals(storeName, adapter.getName())) {
                return adapter;
            }
        }
        return null;
    }

    private String quantumImplementationStrategy(AppQuantumStoreAdapter adapter) {
        String strategy = adapter.getConfiguration().get("implementationStrategy");
        if (strategy == null || strategy.isBlank()) {
            strategy = defaultQuantumImplementationStrategy(adapter.getAdapterName());
        }
        return strategy;
    }

    private String quantumStoreAdapterRole(AppQuantumStoreAdapter adapter) {
        String strategy = quantumImplementationStrategy(adapter);
        return strategy == null || strategy.isBlank() ? "CUSTOM_QUANTUM_ACCESS" : strategy;
    }

    private List<String> distinctPaths(List<String> paths) {
        return List.copyOf(new LinkedHashSet<>(paths));
    }

    private List<RepositoryHydrationRule> appSemanticRepositoryRules() {
        List<RepositoryHydrationRule> rules = new ArrayList<>();
        for (SppService service : servicesWithRepositoryRules()) {
            rules.addAll(service.getScopeItem().getRepositoryHydrationRules());
        }
        return rules;
    }

    private List<SppService> servicesWithRepositoryRules() {
        List<SppService> services = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) {
                continue;
            }
            if (!service.getScopeItem().getRepositoryHydrationRules().isEmpty()) {
                services.add(service);
            }
        }
        return services;
    }

    private String appSemanticMigrationWarning(AppSemanticMigrationPlan plan) {
        return switch (plan.migrationType()) {
            case "OBJECT_BACKED_PRIMARY_KEY" -> "APP DDL table " + plan.tableName() + " primary key paths "
                    + String.join(", ", plan.paths())
                    + " are still object-backed; keep them only when they are business facts,"
                    + " else migrate technical identity to platform primary keys.";
            case "APP_DDL_PLATFORM_PRIMARY_KEY" -> {
                if (appDdlPlatformPrimaryKeyClosedIdentity(plan.identityKind())) {
                    yield "";
                }
                yield "APP DDL table " + plan.tableName()
                        + " carries platform primary key paths "
                        + String.join(", ", plan.paths())
                        + " as runtime identity"
                        + " (identityKind=" + valueOrDash(plan.identityKind()) + ")"
                        + "; keep those identities outside SPP business roles and use composition identity for contract detail rows when available.";
            }
            case "OBJECT_BACKED_INDEX" -> "APP DDL table " + plan.tableName() + " index paths "
                    + String.join(", ", plan.paths())
                    + " are still object-backed; keep them only for business lookup fields,"
                    + " else migrate technical lookup keys to APP DDL platform columns or indexes.";
            case "SPP_TECHNICAL_FIELD_PLATFORM_COLUMN" -> "APP DDL table " + plan.tableName()
                    + " has platform columns for technical-looking SPP fields "
                    + String.join(", ", plan.paths())
                    + "; keep those SPP fields only when they are true business credentials.";
            case "ROLE_PERFORMER_FIELD" -> "Role " + plan.objectType()
                    + " still declares performer fields "
                    + String.join(", ", plan.paths())
                    + "; role performers should be supplied by take(...) runtime bindings and APP DDL platform columns.";
            case "ROLE_OBJECT_FIELD" -> "Role " + plan.objectType()
                    + " still declares object participant fields "
                    + String.join(", ", plan.paths())
                    + "; role fields should describe role facts only, while object or role relationships"
                    + " should be supplied by runtime participants, sidecars or APP DDL identity.";
            case "ROLE_WITHOUT_PERFORMER" -> "Role " + plan.objectType()
                    + " has no take performer declaration; declare which Entity, Reference, or Role"
                    + " can play this role at runtime.";
            case "SERVICE_NON_ROLE_PARTICIPANT" -> {
                yield serviceBoundaryLabel(plan.serviceName())
                        + " " + plan.serviceName()
                        + " still accepts non-Role participants "
                        + String.join(", ", plan.paths())
                        + "; " + plan.recommendation();
            }
            case "LEGACY_ASYNC_MODIFIER" -> "Scope service " + plan.serviceName()
                    + " uses legacy async modifier spelling asycn; replace it with async.";
            case "REPOSITORY_ESCAPE_HATCH" -> "Repository query " + plan.serviceName()
                    + " still uses explicit repository argument paths "
                    + String.join(", ", plan.paths())
                    + "; prefer APP DDL primary keys, foreign keys or named indexes.";
            case "SNAPSHOT_ESCAPE_HATCH" -> "Snapshot rule for service " + plan.serviceName()
                    + " object " + plan.objectType()
                    + " still uses explicit object key paths "
                    + String.join(", ", plan.paths())
                    + "; prefer APP DDL primary keys or named indexes.";
            case "CURRENT_SNAPSHOT_CONTEXT" -> {
                if (currentSnapshotContextClosedByAppDdl(plan)) {
                    yield "";
                }
                yield "Snapshot rule for service " + plan.serviceName()
                        + " object " + plan.objectType()
                        + " uses current context paths "
                        + String.join(", ", plan.paths())
                        + "; prefer APP DDL aggregate relations or named indexes when the identity can be modeled statically.";
            }
            case "REMOTE_REFERENCE_ADAPTER" -> strictAppDeclarationEnabled("strictExternalRemoteAdapter")
                    ? remoteReferenceAdapterWarning(plan)
                    : "";
            case "REMOTE_EXTERNAL_ADAPTER" -> "";
            case "QUANTUM_SERVICE_INTERNAL_PROTOCOL" -> "Quantum service " + plan.serviceName()
                    + " uses pure quantum service adapter " + plan.adapterName()
                    + " for attributes " + String.join(", ", plan.paths())
                    + "; move object snapshot, object compensation and framework idempotency expectations"
                    + " to the quantum service internal protocol.";
            default -> plan.recommendation();
        };
    }

    private String remoteReferenceAdapterWarning(AppSemanticMigrationPlan plan) {
        return "Remote service " + plan.serviceName()
                + " is still backed by reference adapter " + plan.adapterName()
                + "; keep it only as a mock/reference implementation until a real external adapter is bound.";
    }

    private boolean currentSnapshotContextClosedByAppDdl(AppSemanticMigrationPlan plan) {
        return plan.objectKeyAppDdlDerived()
                && plan.appDdlObjectKeyPushdown()
                && !plan.objectKeyEscapeHatch();
    }

    private boolean appDdlPlatformPrimaryKeyClosedIdentity(String identityKind) {
        return "ROLE_PERFORMER_IDENTITY".equals(identityKind)
                || "COMPOSITION_IDENTITY".equals(identityKind)
                || "BUSINESS_FIELD_IDENTITY".equals(identityKind);
    }

    private List<String> repositoryMigrationPaths(RepositoryHydrationRule rule) {
        List<String> paths = new ArrayList<>();
        for (RepositoryHydrationRule.KeyTarget target : rule.keyTargets()) {
            paths.add(target.getObjectIdPath());
        }
        return paths;
    }

    private boolean hasUpdateCall(List<IServiceBodyStatement> statements) {
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call && call.getCallee() != null
                    && call.getCallee().getServiceType() == IConstance.ServiceType.update) return true;
            for (var children : statementChildren(statement)) if (hasUpdateCall(children)) return true;
        }
        return false;
    }

    /** Domain-local services inherit the unique local scope; explicit bindings take precedence. */
    private List<String> bindDefaultLocalServices() {
        List<String> errors = new ArrayList<>();
        List<ScopeDefine> localScopes = ruleBlock.getAppScope().getScopelist().stream()
                .filter(ScopeDefine::isLocal).toList();
        if (localScopes.isEmpty()) return errors;
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || unit instanceof SppRoleMapper
                    || service.getScopeItem() != null || isDefaultGeneratedService(service)) continue;
            if (localScopes.size() != 1) {
                errors.add("Service " + service.getFuncName()
                        + " has ambiguous default local scopes; declare its scope explicitly.");
                continue;
            }
            ScopeItem item = new ScopeItem();
            item.setDeclaredServiceName(service.getFuncName());
            localScopes.get(0).addScopeItem(item);
            item.setService(service);
        }
        return errors;
    }

    private record ImplicitCrudCall(SppService owner, SppServiceCall call, String operation,
                                    SppClass participant, SppClass actual, int dimension) { }

    /** Resolve built-in CRUD entrances without requiring source atomic declarations. */
    public void prepareImplicitParticipantServices() {
        List<ImplicitCrudCall> pending = new ArrayList<>();
        for (var unit : new ArrayList<>(mapsOfClass.values())) {
            if (unit instanceof SppScenario scene)
                collectImplicitCrudCalls(scene, scene.getServiceBody().getSppServiceCallList(), pending);
        }
        // Resolve scalar calls first, making overload naming independent of source order.
        pending.sort(java.util.Comparator.comparingInt(ImplicitCrudCall::dimension));
        for (var entry : pending) {
            SppService service = implicitCrudService(entry.operation(), entry.participant(), entry.actual(), entry.dimension());
            if (service == null) continue;
            entry.call().setCallee(service);
            if (entry.owner().getField(service.getFuncName()) == null)
                entry.owner().addField(new SppField(service, service.getFuncName()));
        }
        // APP can bind standard queries even when no local scenario invokes them. They also
        // provide the primary-key entrances used to hydrate roles and stored relationships.
        for (var table : ruleBlock.getAppTables()) for (var mapping : table.getAppTableList()) {
            if (mapping.getEntity() != null)
                implicitCrudService("find", mapping.getEntity(), 0);
        }
        for (var unit : new ArrayList<>(mapsOfClass.values())) {
            if (!(unit instanceof SppService service) || service.isReal()) continue;
            String method = NameUtil.firstToLowerCase(service.getName(), true);
            for (String operation : List.of("find", "insert", "update", "delete")) {
                if (!method.startsWith(operation)) continue;
                var participant = mapsOfClass.get(method.substring(operation.length()));
                if (participant instanceof SppClass type && isCrudParticipant(type))
                    implicitCrudService(operation, type, 0);
            }
        }
    }

    private boolean isCrudParticipant(SppClass type) {
        return !type.isSystemBuiltin() && type.isReal() && type.getType() != null && Set.of(IConstance.CompilationUnitType.entity,
                IConstance.CompilationUnitType.role, IConstance.CompilationUnitType.contract,
                IConstance.CompilationUnitType.reference).contains(type.getType());
    }

    /** APP can configure a built-in entrance without a redundant SPP declaration. */
    public SppService resolveImplicitCrudService(String method) {
        var existing = mapsOfClass.get(NameUtil.firstToLowerCase(method, false));
        if (existing instanceof SppService service && service.isReal() && !isDefaultGeneratedService(service))
            return service;
        for (String operation : List.of("find", "insert", "update", "delete")) {
            if (!method.startsWith(operation)) continue;
            var participant = mapsOfClass.get(method.substring(operation.length()));
            if (participant instanceof SppClass type && isCrudParticipant(type))
                return implicitCrudService(operation, type, 0);
        }
        return null;
    }

    public boolean deferCrudParameterValidation(SppService service) {
        return !service.isReal() || isDefaultGeneratedService(service);
    }

    private void collectImplicitCrudCalls(SppService owner, List<IServiceBodyStatement> statements,
                                          List<ImplicitCrudCall> pending) {
        if (statements == null) return;
        for (var statement : statements) {
            if (statement instanceof SppServiceCall call && call.getCallee() != null
                    && (!call.getCallee().isReal() || isDefaultGeneratedService(call.getCallee()))) {
                String method = NameUtil.firstToLowerCase(call.getCallee().getName(), true);
                for (String operation : List.of("find", "insert", "update", "delete")) {
                    if (!method.startsWith(operation)) continue;
                    var unit = mapsOfClass.get(method.substring(operation.length()));
                    if (!(unit instanceof SppClass participant) || !isCrudParticipant(participant)) continue;
                    List<? extends SppParameter> actuals = call.getSppParameterList();
                    if (call.isImplicitArguments()) {
                        actuals = owner.getServiceBody().getSppLocalVarList().stream()
                                .filter(p -> p.getType() == participant).toList();
                        if (actuals.isEmpty()) actuals = owner.getServiceBody().getSppLocalVarList().stream()
                                .filter(p -> p.getType() instanceof SppClass actual
                                        && ParticipantRoleBindings.resolve(this, this, actual, participant) != null).toList();
                    }
                    if (actuals.size() != 1 || !(actuals.get(0).getType() instanceof SppClass actual)
                            || ParticipantRoleBindings.resolve(this, this, actual, participant) == null
                            || actuals.get(0).getArrayDimension() > 1) {
                        String message = "Built-in CRUD " + method + " in " + owner.getFuncName()
                                + " requires one unambiguous " + participant.getName()
                                + " participant or one participant collection; pass the participant explicitly.";
                        if (!appParseErrors.contains(message)) addAppParseError(message);
                        break;
                    }
                    pending.add(new ImplicitCrudCall(owner, call, operation, participant, actual,
                            actuals.get(0).getArrayDimension()));
                    break;
                }
            }
            for (var children : statementChildren(statement)) collectImplicitCrudCalls(owner, children, pending);
        }
    }

    SppService implicitCrudService(String operation, SppClass participant, int dimension) {
        return implicitCrudService(operation, participant, participant, dimension);
    }

    private SppService implicitCrudService(String operation, SppClass participant, SppClass actual, int dimension) {
        if (!isCrudParticipant(participant)) return null;
        String method = operation + participant.getName();
        String name = NameUtil.firstToLowerCase(method, false);
        var existing = mapsOfClass.get(name);
        if (existing instanceof SppService legacy && isDefaultGeneratedService(legacy)) {
            // Legacy UpdateEntity interfaces require a handwritten mapper. A convention call
            // instead uses the standard repository-backed runtime service, with no returns.
            legacy.setImplicitParticipantCrud(true);
            for (var defaults : sppDefaultServices) defaults.getServiceList().removeIf(service -> service == legacy);
            existing = null;
        }
        if (existing instanceof SppService service && service.isReal()) {
            if (!service.isImplicitParticipantCrud()) return service;
            var formal = service.getServiceBody().getLocalVar(0);
            if (formal.getArrayDimension() == dimension && formal.getType() == actual) return service;
            method += formal.getType() == actual ? dimension == 0 ? "Single" : "Collection"
                    : "As" + actual.getName() + (dimension == 0 ? "" : "Collection");
            name = NameUtil.firstToLowerCase(method, false);
            existing = mapsOfClass.get(name);
            if (existing instanceof SppService variant && variant.isImplicitParticipantCrud()) return variant;
            if (existing != null && existing.isReal()) {
                addAppParseError("Built-in CRUD implementation name conflicts with an explicit declaration: " + method);
                return null;
            }
        } else if (existing != null && !(existing instanceof SppService)) {
            addAppParseError("Built-in CRUD name conflicts with a participant declaration: " + method);
            return null;
        }
        SppService service = existing instanceof SppService placeholder ? placeholder : new SppService(name);
        service.setReal(true);
        service.setType(IConstance.CompilationUnitType.atomicservice);
        service.setFuncName(method);
        service.setImplicitParticipantCrud(true);
        service.setImplicitCrudTarget(participant);
        service.setServiceType(operation.equals("find") ? IConstance.ServiceType.query : IConstance.ServiceType.update);
        if (!operation.equals("find")) service.setPersistenceOperation(operation);
        var argument = new SppLocalVar(actual, actual.getName());
        argument.setArrayDimension(dimension);
        service.addLocalVar(argument);
        mapsOfClass.put(name, service);
        return service;
    }

    /** Materialize standard bindings after all APP/PHY sources have been parsed. */
    public List<String> prepareDefaultParticipantBindings() {
        prepareImplicitParticipantServices();
        List<String> errors = bindDefaultLocalServices();
        inferQuantumBindings(errors);
        var tables = appTableByBusinessType();
        var defaults = ruleBlock.getAppRepositoryAdapters().stream().filter(adapter ->
                "true".equals(adapter.getConfiguration().get("default"))).toList();
        var repositories = defaults.isEmpty() && ruleBlock.getAppRepositoryAdapters().size() == 1
                ? ruleBlock.getAppRepositoryAdapters() : defaults;
        var handler = new com.servpp.lang.general.app.RuleBlockHandler(new java.io.File(getName() + ".app"));
        handler.setSppDomain(this);
        var context = new org.antlr.v4.runtime.ParserRuleContext();
        context.start = new org.antlr.v4.runtime.CommonToken(0, "default participant binding");
        for (var unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || unit.getType() != IConstance.CompilationUnitType.atomicservice
                    || service.hasBusinessImplementation() || service.isDeclaredQuantumService()
                    || service.hasLocalServiceBinding() || isDefaultGeneratedService(service)) continue;
            if (ruleBlock.getLocalServiceBindings().stream().anyMatch(binding -> binding.getServiceName().equals(service.getFuncName()))) continue;
            var scope = service.getScopeItem();
            if (scope == null || !scope.isLocal() || !scope.getRepositoryHydrationRules().isEmpty()
                    || !scope.getPersistenceRules().isEmpty()) continue;
            var parameters = service.getServiceBody().getSppLocalVarList();
            if (parameters.stream().anyMatch(SppLocalVar::isQueryKey)) {
                inferKeyParticipantQuery(service, repositories, errors);
                continue;
            }
            if (parameters.size() != 1 || !(parameters.get(0).getType() instanceof SppClass participant)) continue;
            if (participant.getReferenceSource() != null) continue;
            // A take-based call targets the named participant, never the caller's own table.
            if (service.getImplicitCrudTarget() != null && service.getImplicitCrudTarget() != participant) continue;
            if (!tables.containsKey(participant.getName())) {
                if (service.getServiceType() == IConstance.ServiceType.query && participant.getReferenceSource() == null
                        && mapsOfClass.values().stream().anyMatch(mapping -> mapping instanceof SppRoleMapper mapper && mapper.getRole() == participant))
                    service.setQueryFromPerformers(true);
                if (service.isImplicitParticipantCrud() && participant.getReferenceSource() == null
                        && !service.isQueryFromPerformers())
                    errors.add("Built-in CRUD " + service.getFuncName() + " requires an APP table or an explicit provider binding for " + participant.getName() + ".");
                continue;
            }
            if (service.getServiceType() != IConstance.ServiceType.query && service.getServiceType() != IConstance.ServiceType.update) continue;
            if (repositories.size() != 1) {
                errors.add("Default repository for " + service.getFuncName() + " is missing or ambiguous; select a unique PHY default repository or bind explicitly.");
                continue;
            }
            var table = tables.get(participant.getName());
            var keys = inferSinglePerformerPlatformIdentity(participant, table, tables);
            if (keys.isEmpty()) {
                // Compatibility for historical explicit singleton declarations; never inferred for new models.
                var legacySingletons=ruleBlock.getAppScope().getScopelist().stream()
                        .flatMap(defined -> defined.getScopeItems().stream())
                        .flatMap(item -> item.getRepositoryHydrationRules().stream())
                        .filter(rule -> participant.getName().equals(rule.getObjectType())
                            && rule.keyTargets().size()==1 && rule.firstKey().isSingleton()).toList();
                if (table.getPrimaryKeys().isEmpty() && legacySingletons.size()==1)
                    keys=List.of(new RepositoryHydrationRule.KeyTarget(0,participant.getName()+".@singleton"));
            }
            boolean independentInsert = table.isIndependentIdentity() && service.getServiceType() == IConstance.ServiceType.update
                    && Set.of("insert", "insertIfAbsent").contains(service.getPersistenceOperation());
            if (independentInsert) keys = List.of(new RepositoryHydrationRule.KeyTarget(0, participant.getName() + ".@generatedIdentity"));
            boolean storedIdentity = keys.isEmpty()
                    && (service.isImplicitParticipantCrud() && service.getServiceType() == IConstance.ServiceType.query
                        || service.getServiceType() == IConstance.ServiceType.update
                            && !Set.of("insert", "insertIfAbsent").contains(service.getPersistenceOperation() == null ? "update" : service.getPersistenceOperation()))
                    && requiresStoredParticipantIdentity(participant, table, handler);
            if (storedIdentity)
                keys = List.of(new RepositoryHydrationRule.KeyTarget(0, participant.getName() + ".@storageIdentity"));
            if (keys.isEmpty()) keys = handler.repositoryTargetsFromAppDdl(context, participant.getName());
            if (keys.isEmpty()) continue;
            var repository = repositories.get(0);
            if (service.getServiceType() == IConstance.ServiceType.query) {
                String keySource = storedIdentity ? "APP_DDL_STORAGE_IDENTITY"
                        : handler.repositoryPrimaryKeySourceHint(participant.getName(), keys);
                scope.addRepositoryHydrationRule(new RepositoryHydrationRule(participant.getName(), keys,
                        parameters.get(0).getName(), repository.getName(), true,
                        keySource == null ? "APP_DDL_PRIMARY_KEY" : keySource));
                // Scalar absence is a business identification result; never a technical exception.
                if (parameters.get(0).getArrayDimension() == 0) scope.declareOptionalRepository();
            } else {
                String mode = scope.getTransactionModifierNames().isEmpty()
                        ? ruleBlock.hasTransactionPolicy() ? ruleBlock.getDefaultUpdateMode() : repository.getConfiguration().get("persistenceMode")
                        : scope.getTransactionModeName();
                String resource = scope.getResourceId() != null ? scope.getResourceId()
                        : ruleBlock.hasTransactionPolicy() ? ruleBlock.getDefaultUpdateResource() : repository.getConfiguration().get("persistenceResource");
                if (mode == null || resource == null || resource.isBlank()) {
                    errors.add("Default persistence for " + service.getFuncName()
                            + " requires PHY repository persistenceMode and persistenceResource, or an explicit APP persist binding.");
                    continue;
                }
                try {
                    if (scope.getTransactionModifierNames().isEmpty())
                        scope.setTransactionType(IConstance.TransactionType.valueOf(mode.toLowerCase(java.util.Locale.ROOT)));
                    if (scope.getResourceId() == null) scope.setResourceId(resource);
                    scope.getPersistenceRules().add(new com.servpp.lang.models.app.PersistenceRule(
                            service.getPersistenceOperation() == null ? "update" : service.getPersistenceOperation(), keys, participant.getName()));
                } catch (IllegalArgumentException failure) {
                    errors.add("Invalid PHY persistenceMode " + mode + " for repository " + repository.getName());
                }
            }
        }
        for (var unit : mapsOfClass.values()) if (unit instanceof SppService service)
            service.setDeploymentTransactionMode(ruleBlock.getDeploymentTransactionMode());
        applyTableAutoNumbers(errors);
        return errors;
    }

    /** An opaque APP key is carried by storage metadata, never inferred from unrelated facts. */
    private boolean requiresStoredParticipantIdentity(SppClass participant, AppTable table,
            com.servpp.lang.general.app.IScopeHandler handler) {
        var columns = handler.platformPrimaryKeys(table);
        if (table.isIndependentIdentity()) return true;
        if (columns.isEmpty()) return false;
        if (handler.roleTakeKeyTargets(participant.getName(), columns).size() == columns.size()) return false;
        return columns.stream().anyMatch(column -> participant.getField(column) == null);
    }

    public List<RepositoryHydrationRule.KeyTarget> inferSinglePerformerPlatformIdentity(SppClass participant) {
        var tables=appTableByBusinessType(); var table=tables.get(participant.getName());
        return table==null?List.of():inferSinglePerformerPlatformIdentity(participant,table,tables);
    }

    /** A single role may store its sole performer's identity in an APP-only primary-key column. */
    private List<RepositoryHydrationRule.KeyTarget> inferSinglePerformerPlatformIdentity(SppClass participant,
            AppTable table, Map<String,AppTable> tables) {
        var columns=table.getAppColumns().stream().filter(column -> column.isPrimaryKey() && !column.isObjectBacked()).toList();
        if (table.isIndependentIdentity() || !table.getPrimaryKeys().isEmpty() || columns.size()!=1) return List.of();
        var mappings=mapsOfClass.values().stream().filter(unit -> unit instanceof SppRoleMapper mapper
                && mapper.getRole()==participant && mapper.getEntities()!=null && mapper.getEntities().size()==1)
                .map(unit -> (SppRoleMapper)unit).toList();
        if (mappings.size()!=1) return List.of();
        var mapper=mappings.get(0); var performer=mapper.getEntities().get(0);
        var performerTable=tables.get(performer.getName());
        var paths=performerTable==null?performer.getIdentityPaths():ParticipantRoleBindings.primaryKeys(this,performer);
        if (paths.size()!=1 || !(mapper.isTakeAll() || mapper.isMapSame())) return List.of();
        var key=participant.getField(paths.get(0)); var original=performer.getField(paths.get(0));
        if (key==null || original==null || !key.getType().getName().equals(original.getType().getName()) || key.getArrayDimension()!=0) return List.of();
        return List.of(new RepositoryHydrationRule.KeyTarget(0,participant.getName()+"."+paths.get(0),
                participant.getName()+"."+columns.get(0).getName()));
    }

    private void inferQuantumBindings(List<String> errors) {
        if (ruleBlock.getDeploymentEnvironment() == null) return;
        var attributes = new java.util.LinkedHashSet<String>();
        for (var unit : mapsOfClass.values()) if (unit instanceof SppClass type && type.isReal()
                && type.getReferenceSource() == null && !(type instanceof SppService))
            for (var field : type.getSppFieldList()) if (field.isQuantum()) attributes.add(type.getName() + "." + field.getName());
        if (attributes.isEmpty()) return;
        String storeName = ruleBlock.getDeploymentQuantumResource();
        if (storeName == null && ruleBlock.getAppQuantumStoreAdapters().size() == 1)
            storeName = ruleBlock.getAppQuantumStoreAdapters().get(0).getName();
        if (storeName == null) { errors.add("Domain " + getName() + " requires a unique PHY quantum store or an environment quantum resource"); return; }
        final String selectedStore = storeName;
        var store = ruleBlock.getAppQuantumStores().stream().filter(candidate -> selectedStore.equals(candidate.getName())).findFirst().orElse(null);
        if (store == null) { store = new AppQuantumStore(storeName); ruleBlock.addQuantumStore(store); }
        for (String attribute : attributes) if (!store.getAttributeIds().contains(attribute)) store.addAttributeId(attribute);
        String resource = "LOCAL".equals(ruleBlock.getDeploymentTransactionMode()) ? ruleBlock.getDefaultUpdateResource() : storeName;
        for (var unit : mapsOfClass.values()) if (unit instanceof SppService service && service.isDeclaredQuantumService()) {
            var scope = service.getScopeItem();
            if (scope == null) continue;
            if (scope.getResourceId() == null) {
                scope.setTransactionType(IConstance.TransactionType.quantum);
                scope.setResourceId(resource);
                scope.setQuantumResourceInferred(true);
            }
            if (scope.isQuantumResourceInferred() || scope.getQuantumConservationAttributeIds().isEmpty())
                for (String attribute : service.getAssignedQuantumAttributeDirections().keySet())
                    if (!scope.getQuantumConservationAttributeIds().contains(attribute)) scope.getQuantumConservationAttributeIds().add(attribute);
        }
    }

    /** Infer an association query from an explicit condition participant and APP foreign keys. */
    private void inferKeyParticipantQuery(SppService service,
            List<com.servpp.lang.models.app.AppRepositoryAdapter> repositories, List<String> errors) {
        var parameters = service.getServiceBody().getSppLocalVarList();
        var conditions = parameters.stream().filter(SppLocalVar::isQueryKey).toList();
        var outputs = parameters.stream().filter(parameter -> !parameter.isQueryKey()).toList();
        String label = "Key participant query " + service.getFuncName();
        if (service.getServiceType() != IConstance.ServiceType.query || conditions.size() != 1
                || outputs.size() != 1 || conditions.get(0).getArrayDimension() != 0
                || outputs.get(0).getArrayDimension() != 1
                || !(conditions.get(0).getType() instanceof SppClass conditionType)
                || !(outputs.get(0).getType() instanceof SppClass resultType)) {
            errors.add(label + " requires one scalar key participant and one result collection; customize more complex queries in APP.");
            return;
        }
        var tables = appTableByBusinessType();
        var resultTable = tables.get(resultType.getName());
        if (repositories.size() != 1 || resultTable == null) {
            errors.add(label + " requires an APP result table and a unique PHY default repository, or an explicit APP query.");
            return;
        }
        var associations = resultTable.getForeignKeys().stream()
                .filter(foreign -> foreign.getForeignReference() instanceof SppClass target
                        && ParticipantRoleBindings.identity(this, target).equals(ParticipantRoleBindings.identity(this, conditionType)))
                .flatMap(foreign -> foreign.getKeys().stream())
                .filter(field -> field.getSppField().getArrayDimension() == 0
                        && field.getSppField().getType() instanceof SppClass target
                        && ParticipantRoleBindings.identity(this, target).equals(ParticipantRoleBindings.identity(this, conditionType)))
                .map(field -> field.getObjectPath()).distinct().toList();
        var identity = ParticipantRoleBindings.primaryKeys(this, conditionType);
        boolean storageIdentity = identity.equals(List.of("@storageIdentity"));
        var conditionTable = tables.get(conditionType.getName());
        var platformKeys = conditionTable == null ? List.<String>of() : conditionTable.getAppColumns().stream()
                .filter(column -> column.isPrimaryKey() && !column.isObjectBacked()).map(column -> column.getName()).toList();
        if (associations.size() != 1 || identity.isEmpty()
                || storageIdentity && platformKeys.size() != 1
                || !storageIdentity && identity.stream().anyMatch(path -> path.startsWith("@"))) {
            errors.add(label + " requires a unique APP foreign-key association and mapped condition identity; define an explicit APP query when ambiguous.");
            return;
        }
        int argumentIndex = parameters.indexOf(conditions.get(0));
        var keys = identity.stream().map(path -> new RepositoryHydrationRule.KeyTarget(argumentIndex,
                conditionType.getName() + "." + path,
                resultType.getName() + "." + associations.get(0) + "." + (storageIdentity ? platformKeys.get(0) : path))).toList();
        service.getScopeItem().addRepositoryHydrationRule(new RepositoryHydrationRule(resultType.getName(), keys,
                outputs.get(0).getName(), repositories.get(0).getName(), true, "SPP_KEY_PARTICIPANT"));
    }

    private void applyTableAutoNumbers(List<String> errors) {
        var tables = appTableByBusinessType();
        for (var unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null || service.hasLocalServiceBinding()) continue;
            var scope = service.getScopeItem();
            for (var write : scope.getPersistenceRules()) {
                if (!"insert".equals(write.operation()) && !"insertIfAbsent".equals(write.operation())) continue;
                var table = tables.get(write.objectType());
                if (table == null) continue;
                for (var entry : table.getAutoNumbers().entrySet()) {
                    var field = table.getEntity().getField(entry.getKey());
                    if (field.isBorrowedStorageFact() && !field.isStoredHere()) {
                        errors.add("APP auto numbering must be declared on the owning participant table: " + write.objectType() + "." + entry.getKey());
                        continue;
                    }
                    var existing = scope.getBusinessNumbers().stream().filter(number ->
                            number.target().argumentIndex() == write.argumentIndex()
                            && number.target().getObjectIdPath().equals(entry.getKey())).toList();
                    if (existing.isEmpty()) scope.getBusinessNumbers().add(new ScopeItem.BusinessNumber(
                            new RepositoryHydrationRule.KeyTarget(write.argumentIndex(), write.objectType() + "." + entry.getKey()), entry.getValue()));
                    else if (existing.stream().anyMatch(number -> !number.prefix().equals(entry.getValue())))
                        errors.add("Conflicting APP auto numbering for " + write.objectType() + "." + entry.getKey());
                }
            }
        }
    }

    private boolean isDefaultGeneratedService(SppService service) {
        for (SppDefaultService defaultService : sppDefaultServices) {
            if (defaultService.getServiceList().contains(service)) {
                return true;
            }
        }
        return false;
    }

    private String serviceBoundaryTargetKind(SppService service) {
        return service instanceof SppScenario ? "SCENARIO_PARTICIPANT" : "SERVICE_PARTICIPANT";
    }

    private String serviceBoundaryLabel(String serviceName) {
        SppService service = findServiceByFuncName(serviceName);
        return service instanceof SppScenario ? "Scenario" : "Service";
    }

    private List<String> serviceNonRoleParticipantPaths(SppService service) {
        List<String> paths = new ArrayList<>();
        for (SppLocalVar parameter : service.getServiceBody().getSppLocalVarList()) {
            if (parameter.isContextParameter()) {
                continue;
            }
            if (!(parameter.getType() instanceof SppClass parameterType)) {
                continue;
            }
            if (parameterType.getType() == IConstance.CompilationUnitType.entity
                    || parameterType.getType() == IConstance.CompilationUnitType.reference) {
                paths.add(service.getFuncName() + "." + parameter.getName() + ":"
                        + typedName(parameter));
            }
        }
        return List.copyOf(paths);
    }

    private String serviceNonRoleParticipantRecommendation(List<String> paths) {
        LinkedHashSet<String> recommendations = new LinkedHashSet<>();
        for (String path : paths == null ? List.<String>of() : paths) {
            String participantTypeName = serviceParticipantType(path);
            SppCompilationUnit participantType = mapsOfClass.get(participantTypeName);
            if (participantType == null) {
                recommendations.add("Resolve the participant through a remote Role adapter before it enters the service boundary.");
                continue;
            }
            switch (participantType.getType()) {
                case contract -> recommendations.add(
                        "Move Contract values out of the participant list and bind them as Role-signed scenario context or products.");
                case entity -> recommendations.add(serviceNonRoleEntityRecommendation(participantTypeName));
                case reference -> recommendations.add(serviceNonRoleReferenceRecommendation(participantTypeName));
                default -> recommendations.add(
                        "Keep Role participants at the service boundary and move concrete performer details to runtime identity.");
            }
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Services and scenarios should accept Role participants at the service boundary.");
        }
        return String.join(" ", recommendations);
    }

    private String serviceNonRoleEntityRecommendation(String participantTypeName) {
        List<String> candidateRoles = roleTypesPerformedBy(participantTypeName);
        if (!candidateRoles.isEmpty()) {
            return "Accept target Role " + String.join("+", candidateRoles)
                    + " and resolve the concrete Entity performer through role identity or repository query.";
        }
        return "Accept a Role participant and resolve the concrete Entity performer through role identity or repository query.";
    }

    private String serviceNonRoleReferenceRecommendation(String participantTypeName) {
        List<String> candidateRoles = roleTypesPerformedBy(participantTypeName);
        if (!candidateRoles.isEmpty()) {
            return "Keep external Reference objects behind target Role " + String.join("+", candidateRoles)
                    + " and a remote Role adapter instead of passing them as service participants.";
        }
        return "Keep external Reference objects behind a remote Role adapter instead of passing them as service participants.";
    }

    private List<String> serviceNonRoleParticipantCategories(List<String> paths) {
        List<String> categories = new ArrayList<>();
        for (String path : paths == null ? List.<String>of() : paths) {
            SppCompilationUnit participantType = mapsOfClass.get(serviceParticipantType(path));
            if (participantType == null) {
                categories.add("");
                continue;
            }
            categories.add(switch (participantType.getType()) {
                case contract -> "contracts";
                case entity -> "entities";
                case reference -> "references";
                case role -> "roles";
                default -> "";
            });
        }
        return List.copyOf(categories);
    }

    private String serviceParticipantType(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int colon = path.lastIndexOf(':');
        if (colon < 0 || colon == path.length() - 1) {
            return "";
        }
        String typeName = path.substring(colon + 1).trim();
        while (typeName.endsWith("[]")) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }
        return typeName;
    }

    private record RoleFieldMigrationEvidence(List<String> paths, List<String> categories) {
        private RoleFieldMigrationEvidence {
            paths = paths == null ? List.of() : List.copyOf(paths);
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    private RoleFieldMigrationEvidence rolePerformerFieldEvidence(String roleName) {
        SppCompilationUnit roleUnit = mapsOfClass.get(roleName);
        if (!(roleUnit instanceof SppClass roleClass)
                || roleClass.getType() != IConstance.CompilationUnitType.role) {
            return new RoleFieldMigrationEvidence(List.of(), List.of());
        }
        // Declared participant members are intrinsic composition, not duplicate technical fields.
        return new RoleFieldMigrationEvidence(List.of(),List.of());
    }

    private List<String> rolePerformerFieldPaths(String roleName) {
        return rolePerformerFieldEvidence(roleName).paths();
    }

    private RoleFieldMigrationEvidence roleObjectFieldEvidence(String roleName) {
        SppCompilationUnit roleUnit = mapsOfClass.get(roleName);
        if (!(roleUnit instanceof SppClass roleClass)
                || roleClass.getType() != IConstance.CompilationUnitType.role) {
            return new RoleFieldMigrationEvidence(List.of(), List.of());
        }
        Set<String> performerNames = rolePerformerNames(roleName);
        List<String> paths = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        for (SppField field : roleClass.getSppFieldList()) {
            if (field == null || field.getType() == null || field.isSyntheticRolePerformer()) {
                continue;
            }
            if (performerNames.contains(field.getType().getName())) {
                continue;
            }
            if (!(field.getType() instanceof SppClass fieldType)) {
                continue;
            }
            IConstance.CompilationUnitType type = fieldType.getType();
            if (type == IConstance.CompilationUnitType.entity
                    || type == IConstance.CompilationUnitType.reference
                    || type == IConstance.CompilationUnitType.contract
                    || type == IConstance.CompilationUnitType.role) {
                paths.add(roleName + "." + field.getName() + ":" + typedName(field));
                categories.add(roleFieldCategory(field));
            }
        }
        return new RoleFieldMigrationEvidence(paths, categories);
    }

    private List<String> roleObjectFieldPaths(String roleName) {
        return roleObjectFieldEvidence(roleName).paths();
    }

    private String roleFieldCategory(SppField field) {
        if (field == null || !(field.getType() instanceof SppClass fieldType)) {
            return "unknown";
        }
        return switch (fieldType.getType()) {
            case entity -> "entities";
            case reference -> "references";
            case contract -> "contracts";
            case role -> "roles";
            default -> "unknown";
        };
    }

    private String typedName(SppParameter parameter) {
        if (parameter == null || parameter.getType() == null) {
            return "";
        }
        return parameter.getType().getName() + parameter.getArrayString();
    }

    private Set<String> rolePerformerNames(String roleName) {
        Set<String> performerNames = new LinkedHashSet<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole() == null
                    || !roleName.equals(mapper.getRole().getName())) {
                continue;
            }
            for (SppClass performer : roleMapperPerformers(mapper)) {
                if (performer != null && performer.getName() != null) {
                    performerNames.add(performer.getName());
                }
            }
        }
        return performerNames;
    }

    private List<SppClass> roleMapperPerformers(SppRoleMapper mapper) {
        if (mapper == null) {
            return List.of();
        }
        Map<String, SppClass> performers = new LinkedHashMap<>();
        if (mapper.getEntities() != null) {
            for (SppClass performer : mapper.getEntities()) {
                if (performer != null && performer.getName() != null) {
                    performers.putIfAbsent(performer.getName(), performer);
                }
            }
        }
        for (SppLocalVar localVar : mapper.getSppFieldList()) {
            if (!(localVar instanceof SppRoleField roleField)
                    || roleField.getEntityName() == null
                    || roleField.getEntityName().isBlank()) {
                continue;
            }
            SppCompilationUnit performer = mapsOfClass.get(roleField.getEntityName());
            if (performer instanceof SppClass performerClass) {
                performers.putIfAbsent(performerClass.getName(), performerClass);
            }
        }
        return new ArrayList<>(performers.values());
    }

    private List<String> roleTypesPerformedBy(String performerType) {
        if (performerType == null || performerType.isBlank()) {
            return List.of();
        }
        List<String> roleTypes = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppRoleMapper mapper) || mapper.getRole() == null) {
                continue;
            }
            for (SppClass performer : roleMapperPerformers(mapper)) {
                if (performer != null && performerType.equals(performer.getName())) {
                    roleTypes.add(mapper.getRole().getName());
                    break;
                }
            }
        }
        return roleTypes.stream().distinct().toList();
    }

    private List<String> appTableTechnicalFieldPlatformColumnPaths(AppTable appTable) {
        List<String> paths = new ArrayList<>();
        if (appTable == null || appTable.getEntity() == null) {
            return paths;
        }
        Set<String> businessCredentialFields = appTableBusinessCredentialFields(appTable);
        for (AppColumn column : appTable.getAppColumns()) {
            if (column.isObjectBacked() || column.getName() == null
                    || !column.isBusinessFieldShadow()
                    || !isTechnicalIdentifierName(column.getName())
                    || businessCredentialFields.contains(column.getName())) {
                continue;
            }
            paths.add(appTable.getEntity().getName() + "." + column.getName());
        }
        return paths;
    }

    private Set<String> appTableBusinessCredentialFields(AppTable appTable) {
        Set<String> fields = new LinkedHashSet<>();
        if (appTable == null || appTable.getAnnotations() == null) {
            return fields;
        }
        for (AnnotationDefine annotation : appTable.getAnnotations()) {
            if (annotation == null || !"businessCredential".equals(annotation.getName())) {
                continue;
            }
            String value = annotation.getParameters().get("fields");
            if (value == null || value.isBlank()) {
                value = annotation.getParameters().get("field");
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String field : value.split(",")) {
                String trimmed = field.trim();
                if (!trimmed.isBlank()) {
                    fields.add(trimmed);
                }
            }
        }
        return fields;
    }

    private List<String> appTablePlatformPrimaryKeyMigrationPaths(AppTable appTable) {
        List<String> paths = new ArrayList<>();
        if (appTable == null) {
            return paths;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (column.isPrimaryKey() && !column.isObjectBacked()
                    && column.getName() != null && !column.getName().isBlank()) {
                paths.add(appTable.getName() + "." + column.getName());
            }
        }
        return paths;
    }

    private CurrentSnapshotAppDdlKeyEvidence currentSnapshotAppDdlKeyEvidence(ObjectSnapshotRule rule,
                                                                               Map<String, AppTable> tableByBusinessType) {
        AppTable appTable = tableByBusinessType.get(rule.getObjectType());
        Set<String> currentKeyPaths = normalizeAppDdlKeyPaths(rule.getObjectType(), rule.objectIdPaths());
        if (appTable == null || currentKeyPaths.isEmpty()) {
            return CurrentSnapshotAppDdlKeyEvidence.escapeHatch(currentKeyPaths);
        }
        Set<String> primaryKeyPaths = normalizeAppDdlKeyPaths(rule.getObjectType(),
                appTableObjectBackedPrimaryKeyPaths(appTable, tableByBusinessType, new LinkedHashSet<>()));
        primaryKeyPaths.addAll(normalizeAppDdlTableKeyPaths(rule.getObjectType(), appTable.getName(),
                appTablePlatformPrimaryKeyPaths(appTable)));
        if (primaryKeyPaths.equals(currentKeyPaths)) {
            return appDdlKeyEvidence("APP_DDL_PRIMARY_KEY", "", appTable, primaryKeyPaths, tableByBusinessType);
        }
        for (AppIndex index : appTable.getAppIndexes()) {
            Set<String> indexPaths = normalizeAppDdlTableKeyPaths(rule.getObjectType(), appTable.getName(),
                    index.getPaths());
            if (indexPaths.equals(currentKeyPaths)) {
                return appDdlKeyEvidence("APP_DDL_NAMED_INDEX", index.getName(), appTable,
                        indexPaths, tableByBusinessType);
            }
        }
        Set<String> legacyIndexPaths = normalizeAppDdlTableKeyPaths(rule.getObjectType(), appTable.getName(),
                appTable.getIndexes());
        if (legacyIndexPaths.equals(currentKeyPaths)) {
            return appDdlKeyEvidence("APP_DDL_INDEX", "", appTable, legacyIndexPaths, tableByBusinessType);
        }
        return CurrentSnapshotAppDdlKeyEvidence.escapeHatch(currentKeyPaths);
    }

    private CurrentSnapshotAppDdlKeyEvidence repositoryAppDdlKeyEvidence(RepositoryHydrationRule rule,
                                                                         Map<String, AppTable> tableByBusinessType) {
        AppTable appTable = tableByBusinessType.get(rule.getObjectType());
        Set<String> repositoryKeyPaths = normalizeAppDdlKeyPaths(rule.getObjectType(),
                rule.keyTargets().stream()
                        .map(RepositoryHydrationRule.KeyTarget::getRepositoryObjectIdPath)
                        .toList());
        if (appTable == null || repositoryKeyPaths.isEmpty()) {
            return CurrentSnapshotAppDdlKeyEvidence.escapeHatch(repositoryKeyPaths);
        }
        Set<String> primaryKeyPaths = normalizeAppDdlKeyPaths(rule.getObjectType(),
                appTableObjectBackedPrimaryKeyPaths(appTable, tableByBusinessType, new LinkedHashSet<>()));
        primaryKeyPaths.addAll(normalizeAppDdlTableKeyPaths(rule.getObjectType(), appTable.getName(),
                appTablePlatformPrimaryKeyPaths(appTable)));
        if (primaryKeyPaths.equals(repositoryKeyPaths)) {
            return appDdlKeyEvidence("APP_DDL_PRIMARY_KEY", "", appTable, primaryKeyPaths, tableByBusinessType);
        }
        for (AppIndex index : appTable.getAppIndexes()) {
            Set<String> indexPaths = normalizeAppDdlTableKeyPaths(rule.getObjectType(), appTable.getName(),
                    index.getPaths());
            if (indexPaths.equals(repositoryKeyPaths)) {
                return appDdlKeyEvidence("APP_DDL_NAMED_INDEX", index.getName(), appTable,
                        indexPaths, tableByBusinessType);
            }
        }
        Set<String> legacyIndexPaths = normalizeAppDdlTableKeyPaths(rule.getObjectType(), appTable.getName(),
                appTable.getIndexes());
        if (legacyIndexPaths.equals(repositoryKeyPaths)) {
            return appDdlKeyEvidence("APP_DDL_INDEX", "", appTable, legacyIndexPaths, tableByBusinessType);
        }
        return CurrentSnapshotAppDdlKeyEvidence.escapeHatch(repositoryKeyPaths);
    }

    private String currentSnapshotAppDdlKeyError(ObjectSnapshotRule rule,
                                                 SppService service,
                                                 CurrentSnapshotAppDdlKeyEvidence keyEvidence) {
        List<String> missingReasons = new ArrayList<>();
        if (!keyEvidence.objectKeyAppDdlDerived()) {
            missingReasons.add("appDdlDerived");
        }
        if (keyEvidence.objectKeyEscapeHatch()) {
            missingReasons.add("escapeHatchClear");
        }
        if (!keyEvidence.appDdlObjectKeyPushdown()) {
            missingReasons.add("appDdlObjectKeyPushdown");
        }
        String action = "declareAppDdlPrimaryKeyOrNamedIndex+pushCurrentObjectKeyToAppDdlColumns";
        return "Current snapshot " + rule.objectPath() + " on service "
                + service.getFuncName()
                + " must be covered by APP DDL primary keys or named indexes; declare a table key/index for "
                + rule.getObjectType() + " and keep current(...) as a scenario context locator only"
                + " (missing=" + String.join("+", missingReasons)
                + ", keySource=" + valueOrDash(keyEvidence.objectKeySource())
                + ", index=" + valueOrDash(keyEvidence.objectKeyAppDdlIndexName())
                + ", paths=" + valueOrDash(String.join("+", rule.objectIdPaths()))
                + ", appDdlPaths=" + valueOrDash(String.join("+", keyEvidence.appDdlObjectKeyPaths()))
                + ", columns=" + valueOrDash(String.join("+", keyEvidence.objectKeyColumns()))
                + ", externalRoots=" + valueOrDash(String.join("+", keyEvidence.externalObjectKeyRoots()))
                + ", actions=" + action
                + ", action=" + action + ").";
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private CurrentSnapshotAppDdlKeyEvidence appDdlKeyEvidence(String source,
                                                               String indexName,
                                                               AppTable appTable,
                                                               Set<String> paths,
                                                               Map<String, AppTable> tableByBusinessType) {
        List<String> appDdlPaths = List.copyOf(paths);
        List<String> columns = appDdlObjectKeyColumns(appTable, appDdlPaths, tableByBusinessType);
        return new CurrentSnapshotAppDdlKeyEvidence(source, indexName, true, false, appDdlPaths, columns,
                columns.size() == appDdlPaths.size(), externalObjectKeyRoots(appDdlPaths));
    }

    private List<String> appDdlObjectKeyColumns(AppTable appTable,
                                                List<String> paths,
                                                Map<String, AppTable> tableByBusinessType) {
        List<String> columns = new ArrayList<>();
        Set<String> tableColumns = collectAppTableColumnNames(appTable, tableByBusinessType);
        Map<String, String> columnByLowerName = new LinkedHashMap<>();
        for (String columnName : tableColumns) {
            columnByLowerName.put(columnName.toLowerCase(Locale.ROOT), columnName);
        }
        for (String path : paths == null ? List.<String>of() : paths) {
            String column = appDdlObjectKeyColumn(path, tableColumns, columnByLowerName);
            if (column != null && !columns.contains(column)) {
                columns.add(column);
            }
        }
        return List.copyOf(columns);
    }

    private String appDdlObjectKeyColumn(String path,
                                         Set<String> tableColumns,
                                         Map<String, String> columnByLowerName) {
        for (String candidate : appDdlIndexColumnNameCandidates(null, path)) {
            if (tableColumns.contains(candidate)) {
                return candidate;
            }
            String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
            if (columnByLowerName.containsKey(lowerCandidate)) {
                return columnByLowerName.get(lowerCandidate);
            }
        }
        return null;
    }

    private static List<String> externalObjectKeyRoots(List<String> paths) {
        List<String> roots = new ArrayList<>();
        for (String path : paths == null ? List.<String>of() : paths) {
            if (path == null) {
                continue;
            }
            int dot = path.indexOf('.');
            if (dot > 0) {
                String root = path.substring(0, dot);
                if (!roots.contains(root)) {
                    roots.add(root);
                }
            }
        }
        return List.copyOf(roots);
    }

    private record CurrentSnapshotAppDdlKeyEvidence(String objectKeySource,
                                                    String objectKeyAppDdlIndexName,
                                                    boolean objectKeyAppDdlDerived,
                                                    boolean objectKeyEscapeHatch,
                                                    List<String> appDdlObjectKeyPaths,
                                                    List<String> objectKeyColumns,
                                                    boolean appDdlObjectKeyPushdown,
                                                    List<String> externalObjectKeyRoots) {
        private static CurrentSnapshotAppDdlKeyEvidence escapeHatch(Set<String> paths) {
            List<String> appDdlPaths = List.copyOf(paths);
            return new CurrentSnapshotAppDdlKeyEvidence("CURRENT_CONTEXT_ESCAPE_HATCH", "", false, true,
                    appDdlPaths, List.of(), false, SppDomain.externalObjectKeyRoots(appDdlPaths));
        }

        private boolean closed() {
            return objectKeyAppDdlDerived && !objectKeyEscapeHatch && appDdlObjectKeyPushdown;
        }
    }

    private Set<String> normalizeAppDdlKeyPaths(String objectType, List<String> paths) {
        Set<String> normalized = new LinkedHashSet<>();
        String prefix = objectType == null || objectType.isBlank() ? "" : objectType + ".";
        for (String path : paths == null ? List.<String>of() : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String value = path.trim();
            if (!prefix.isBlank() && value.startsWith(prefix)) {
                value = value.substring(prefix.length());
            }
            normalized.add(value);
        }
        return normalized;
    }

    private Set<String> normalizeAppDdlTableKeyPaths(String objectType, String tableName, List<String> paths) {
        Set<String> normalized = new LinkedHashSet<>();
        String objectPrefix = objectType == null || objectType.isBlank() ? "" : objectType + ".";
        String tablePrefix = tableName == null || tableName.isBlank() ? "" : tableName + ".";
        for (String path : paths == null ? List.<String>of() : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String value = path.trim();
            if (!objectPrefix.isBlank() && value.startsWith(objectPrefix)) {
                value = value.substring(objectPrefix.length());
            } else if (!tablePrefix.isBlank() && value.startsWith(tablePrefix)) {
                value = value.substring(tablePrefix.length());
            }
            normalized.add(value);
        }
        return normalized;
    }

    private boolean isTechnicalIdentifierName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("skuid")
                || lower.endsWith("id")
                || lower.endsWith("no");
    }

    private Map<String, AppTable> appTableByBusinessType() {
        Map<String, AppTable> tableByBusinessType = referencedAppTables();
        for (AppTables appTables : ruleBlock.getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (appTable.getEntity() != null) {
                    tableByBusinessType.putIfAbsent(appTable.getEntity().getName(), appTable);
                }
            }
        }
        return tableByBusinessType;
    }

    private boolean isAppDdlPlatformPrimaryKeyRolePerformerIdentity(AppSemanticMigrationPlan plan) {
        return "ROLE_PERFORMER_IDENTITY".equals(appDdlPlatformPrimaryKeyIdentityKind(plan));
    }

    private String appDdlPlatformPrimaryKeyIdentityKind(AppSemanticMigrationPlan plan) {
        if (plan == null || plan.objectType() == null || plan.objectType().isBlank()) {
            return "SIDECAR_PLATFORM_IDENTITY";
        }
        SppCompilationUnit unit = mapsOfClass.get(plan.objectType());
        if (!(unit instanceof SppClass)) {
            return "SIDECAR_PLATFORM_IDENTITY";
        }
        AppTable roleTable = appTableByBusinessType().get(plan.objectType());
        if (roleTable == null) {
            return "SIDECAR_PLATFORM_IDENTITY";
        }
        Set<String> primaryKeyColumns = appDdlPlatformPrimaryKeyPlanColumns(plan);
        if (primaryKeyColumns.isEmpty()) {
            return "SIDECAR_PLATFORM_IDENTITY";
        }
        if (appDdlPlatformPrimaryKeyBusinessFieldIdentity(roleTable, primaryKeyColumns)) {
            return "BUSINESS_FIELD_IDENTITY";
        }
        Set<String> performerIdentityColumns = rolePerformerIdentityColumns(roleTable, plan.objectType());
        if (performerIdentityColumns.isEmpty()) {
            return "SIDECAR_PLATFORM_IDENTITY";
        }
        if (performerIdentityColumns.containsAll(primaryKeyColumns)) {
            return "ROLE_PERFORMER_IDENTITY";
        }
        if (primaryKeyColumns.containsAll(performerIdentityColumns)
                && primaryKeyColumns.size() > performerIdentityColumns.size()) {
            return "COMPOSITION_IDENTITY";
        }
        return "SIDECAR_PLATFORM_IDENTITY";
    }

    private boolean appDdlPlatformPrimaryKeyBusinessFieldIdentity(AppTable appTable, Set<String> primaryKeyColumns) {
        if (appTable == null || primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return false;
        }
        Set<String> businessShadowColumns = appTable.getAppColumns().stream()
                .filter(AppColumn::isBusinessFieldShadow)
                .map(AppColumn::getName)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return businessShadowColumns.containsAll(primaryKeyColumns);
    }

    private Set<String> appDdlPlatformPrimaryKeyPlanColumns(AppSemanticMigrationPlan plan) {
        Set<String> columns = new LinkedHashSet<>();
        for (String path : plan.paths() == null ? List.<String>of() : plan.paths()) {
            if (path == null || path.isBlank()) {
                continue;
            }
            int dot = path.lastIndexOf('.');
            String column = dot >= 0 ? path.substring(dot + 1) : path;
            if (!column.isBlank()) {
                columns.add(column);
            }
        }
        return columns;
    }

    private Set<String> rolePerformerIdentityColumns(AppTable roleTable, String roleType) {
        Set<String> columns = new LinkedHashSet<>();
        Map<String, AppTable> tableByBusinessType = appTableByBusinessType();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppRoleMapper mapper)
                    || mapper.getRole() == null
                    || !roleType.equals(mapper.getRole().getName())) {
                continue;
            }
            for (SppClass performer : roleMapperPerformers(mapper)) {
                if (performer == null || performer.getName() == null) {
                    continue;
                }
                String performerRuntimeName = NameUtil.firstToLowerCase(performer.getName(), true);
                AppTable performerTable = tableByBusinessType.get(performer.getName());
                String performerPlatformPrimaryKey = firstPlatformPrimaryKey(performerTable);
                String roleParticipantPlatformColumn = roleParticipantPlatformColumn(roleTable, performer,
                        performerPlatformPrimaryKey);
                if (roleParticipantPlatformColumn != null) {
                    columns.add(roleParticipantPlatformColumn);
                }
                Set<String> performerKeyPaths = new LinkedHashSet<>(
                        appTableObjectBackedPrimaryKeyPaths(performerTable, tableByBusinessType,
                                new LinkedHashSet<>()));
                performerKeyPaths.addAll(appTablePlatformPrimaryKeyPaths(performerTable));
                for (String keyPath : performerKeyPaths) {
                    columns.addAll(matchingRoleTablePlatformColumns(roleTable,
                            appDdlIndexColumnNameCandidates(roleTable, performerRuntimeName + "." + keyPath)));
                }
                columns.addAll(roleParticipantPlatformColumns(roleTable, performer));
            }
        }
        return columns;
    }

    private Set<String> matchingRoleTablePlatformColumns(AppTable roleTable, Set<String> candidateNames) {
        if (roleTable == null || candidateNames == null || candidateNames.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedCandidates = candidateNames.stream()
                .map(this::normalizeRoleParticipantColumn)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> columns = new LinkedHashSet<>();
        for (AppColumn column : roleTable.getAppColumns()) {
            if (column == null || column.isObjectBacked() || column.getName() == null
                    || column.getName().isBlank()) {
                continue;
            }
            if (candidateNames.contains(column.getName())
                    || normalizedCandidates.contains(normalizeRoleParticipantColumn(column.getName()))) {
                columns.add(column.getName());
            }
        }
        return columns;
    }

    private String firstPlatformPrimaryKey(AppTable appTable) {
        if (appTable == null) {
            return null;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (column.isPrimaryKey() && !column.isObjectBacked()
                    && column.getName() != null && !column.getName().isBlank()) {
                return column.getName();
            }
        }
        return null;
    }

    private String roleParticipantPlatformColumn(AppTable roleTable, SppClass performer, String performerPrimaryKey) {
        if (roleTable == null || performer == null || performerPrimaryKey == null) {
            return null;
        }
        String expected = normalizeRoleParticipantColumn(performer.getName() + "_" + performerPrimaryKey);
        for (AppColumn column : roleTable.getAppColumns()) {
            if (column == null || column.isObjectBacked() || column.getName() == null
                    || column.getName().isBlank()) {
                continue;
            }
            if (expected.equals(normalizeRoleParticipantColumn(column.getName()))) {
                return column.getName();
            }
        }
        return null;
    }

    private Set<String> roleParticipantPlatformColumns(AppTable roleTable, SppClass performer) {
        if (roleTable == null || performer == null || performer.getName() == null) {
            return Set.of();
        }
        Set<String> expected = new LinkedHashSet<>();
        for (SppField field : performer.getSppFieldList()) {
            if (field == null || field.getName() == null || field.getName().isBlank()) {
                continue;
            }
            expected.add(normalizeRoleParticipantColumn(performer.getName() + "_" + field.getName()));
        }
        Set<String> columns = new LinkedHashSet<>();
        for (AppColumn column : roleTable.getAppColumns()) {
            if (column == null || column.isObjectBacked() || column.getName() == null
                    || column.getName().isBlank()) {
                continue;
            }
            if (expected.contains(normalizeRoleParticipantColumn(column.getName()))) {
                columns.add(column.getName());
            }
        }
        return columns;
    }

    private String normalizeRoleParticipantColumn(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }

    private String appDdlFieldReferenceObjectPath(SppFieldReference fieldReference) {
        if (fieldReference == null) {
            return "";
        }
        String objectPath = fieldReference.getObjectPath();
        if (objectPath != null && !objectPath.isBlank()) {
            return objectPath;
        }
        return fieldReference.getSppField() == null ? "" : fieldReference.getSppField().getName();
    }

    private String appDdlFieldReferenceObjectPath(AppTable appTable, SppFieldReference fieldReference) {
        String objectPath = appDdlFieldReferenceObjectPath(fieldReference);
        if (appTable == null || appTable.getEntity() == null || fieldReference == null
                || objectPath == null || objectPath.isBlank()
                || hasFieldPath(appTable.getEntity(), objectPath)) {
            return objectPath;
        }
        SppField field = fieldReference.getSppField();
        if (field == null || field.getParent() == null
                || field.getParent().getName().equals(appTable.getEntity().getName())) {
            return objectPath;
        }
        List<String> ownerPaths = memberPathsForObjectType(appTable.getEntity(), field.getParent().getName(),
                new HashSet<>());
        for (String ownerPath : ownerPaths) {
            String candidate = ownerPath + "." + objectPath;
            if (hasFieldPath(appTable.getEntity(), candidate)) {
                return candidate;
            }
        }
        return objectPath;
    }

    private String appDdlFieldReferenceQualifiedName(SppFieldReference fieldReference) {
        if (fieldReference == null) {
            return "";
        }
        String qualifiedName = fieldReference.getQualifiedName();
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            return qualifiedName;
        }
        return fieldReference.getSppField() == null ? "" : qualifiedFieldName(fieldReference.getSppField());
    }

    private String appDdlColumnNameFromObjectPath(String objectPath) {
        return objectPath == null ? "" : objectPath.replace(".", "_").toLowerCase(Locale.ROOT);
    }

    private boolean appDdlFieldReferenceBelongsToAppTable(AppTable appTable, SppFieldReference fieldReference) {
        if (appTable == null || appTable.getEntity() == null || fieldReference == null) {
            return false;
        }
        return hasFieldPath(appTable.getEntity(), appDdlFieldReferenceObjectPath(appTable, fieldReference));
    }

    private List<String> memberPathsForObjectType(SppClass rootClass, String objectType, Set<String> visitingTypes) {
        if (rootClass == null || objectType == null || !visitingTypes.add(rootClass.getName())) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (SppField field : rootClass.getSppFieldList()) {
            if (field.getArrayDimension() != 0 || !(field.getType() instanceof SppClass fieldClass)) {
                continue;
            }
            if (objectType.equals(fieldClass.getName())) {
                paths.add(field.getName());
                continue;
            }
            for (String nestedPath : memberPathsForObjectType(fieldClass, objectType, visitingTypes)) {
                paths.add(field.getName() + "." + nestedPath);
            }
        }
        visitingTypes.remove(rootClass.getName());
        return paths;
    }

    private List<String> appTableObjectBackedPrimaryKeyPaths(AppTable appTable,
                                                             Map<String, AppTable> tableByBusinessType,
                                                             Set<AppTable> visiting) {
        List<String> paths = new ArrayList<>();
        if (appTable == null || !visiting.add(appTable)) {
            return paths;
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field == null || field.getArrayDimension() != 0) {
                continue;
            }
            String objectPath = appDdlFieldReferenceObjectPath(appTable, primaryKey);
            if (isScalarDbField(field)) {
                paths.add(objectPath);
            } else if (field.getType() != null) {
                AppTable targetTable = tableByBusinessType.get(field.getType().getName());
                for (String nestedPath : appTableObjectBackedPrimaryKeyPaths(targetTable,
                        tableByBusinessType, visiting)) {
                    paths.add(objectPath + "." + nestedPath);
                }
            }
        }
        visiting.remove(appTable);
        return paths;
    }

    private List<String> appTableObjectBackedIndexPaths(AppTable appTable,
                                                        Map<String, AppTable> tableByBusinessType) {
        List<String> paths = new ArrayList<>();
        for (AppIndex index : appTable.getAppIndexes()) {
            for (String path : index.getPaths()) {
                if (isObjectBackedAppDdlIndexPath(appTable, tableByBusinessType, path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private boolean isObjectBackedAppDdlIndexPath(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                                  String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Set<String> candidateColumnNames = appDdlIndexColumnNameCandidates(appTable, path);
        for (AppColumn column : appTable.getAppColumns()) {
            String columnName = column.getName();
            if (candidateColumnNames.contains(columnName)
                    || candidateColumnNames.contains(columnName.toLowerCase(Locale.ROOT))) {
                return column.isObjectBacked();
            }
        }
        SppField businessField = appTableIndexBusinessField(appTable, path);
        if (businessField != null) {
            return businessField.getArrayDimension() == 0 && isScalarDbField(businessField);
        }
        for (String columnName : collectAppTableColumnNames(appTable, tableByBusinessType)) {
            if (candidateColumnNames.contains(columnName)
                    || candidateColumnNames.contains(columnName.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (isDeclaredForeignPrimaryKeyIndexPath(appTable, tableByBusinessType, path, candidateColumnNames)) {
            return false;
        }
        return path.contains(".");
    }

    private SppField appTableIndexBusinessField(AppTable appTable, String path) {
        if (appTable == null || appTable.getEntity() == null || path == null || path.isBlank()) {
            return null;
        }
        String normalizedPath = path;
        String businessTypePrefix = appTable.getEntity().getName() + ".";
        if (normalizedPath.startsWith(businessTypePrefix)) {
            normalizedPath = normalizedPath.substring(businessTypePrefix.length());
        }
        if (normalizedPath.contains(".")) {
            return null;
        }
        return appTable.getEntity().getField(normalizedPath);
    }

    private boolean isDeclaredForeignPrimaryKeyIndexPath(AppTable appTable,
                                                         Map<String, AppTable> tableByBusinessType,
                                                         String path,
                                                         Set<String> candidateColumnNames) {
        if (appTable == null || appTable.getEntity() == null || path == null) {
            return false;
        }
        String prefix = appTable.getEntity().getName() + ".";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String[] segments = path.substring(prefix.length()).split("\\.");
        if (segments.length < 2) {
            return false;
        }
        SppField indexRootField = appTable.getEntity().getField(segments[0]);
        if (indexRootField == null) {
            return false;
        }
        boolean declaredForeignKey = appTable.getForeignKeys().stream()
                .flatMap(foreign -> foreign.getKeys().stream())
                .anyMatch(key -> indexRootField.equals(key.getSppField()));
        if (!declaredForeignKey) {
            return false;
        }
        AppTable targetTable = indexRootField.getType() == null
                ? null
                : tableByBusinessType.get(indexRootField.getType().getName());
        Set<String> targetPrimaryKeyColumns = getPrimaryKeyColumnNames(targetTable, tableByBusinessType);
        if (targetPrimaryKeyColumns.isEmpty()) {
            return false;
        }
        String targetPath = String.join(".", Arrays.copyOfRange(segments, 1, segments.length));
        for (String targetPrimaryKeyColumn : targetPrimaryKeyColumns) {
            if (targetPath.equals(targetPrimaryKeyColumn)) {
                String generatedColumn = generatedAppColumnName(appTable, indexRootField, targetPrimaryKeyColumn);
                return candidateColumnNames.contains(generatedColumn);
            }
        }
        return false;
    }

    private Set<String> appDdlIndexColumnNameCandidates(AppTable appTable, String path) {
        Set<String> values = new LinkedHashSet<>();
        values.add(path);
        values.add(path.replace(".", "_"));
        if (appTable != null && appTable.getEntity() != null) {
            String businessTypePrefix = appTable.getEntity().getName() + ".";
            if (path.startsWith(businessTypePrefix)) {
                String normalizedPath = path.substring(businessTypePrefix.length());
                values.add(normalizedPath);
                values.add(normalizedPath.replace(".", "_"));
            }
        }
        Set<String> lowerCaseValues = new LinkedHashSet<>();
        for (String value : values) {
            lowerCaseValues.add(value.toLowerCase(Locale.ROOT));
        }
        values.addAll(lowerCaseValues);
        return values;
    }

    private void validateTableTargetResolution(AppTable appTable, List<String> errors) {
        for (String undefinedBusinessTypeName : appTable.getUndefinedBusinessTypeNames()) {
            errors.add("Table " + appTable.getName()
                    + " must map to a defined business type, but "
                    + undefinedBusinessTypeName + " is not defined.");
        }
        for (Map.Entry<String, IConstance.CompilationUnitType> nonBusinessType
                : appTable.getNonBusinessTypeNames().entrySet()) {
            errors.add("Table " + appTable.getName()
                    + " must map to a business type, but " + nonBusinessType.getKey()
                    + " is " + nonBusinessType.getValue().name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    private void validateSingleTableBusinessType(AppTable appTable, List<String> errors) {
        for (String extraBusinessTypeName : appTable.getExtraBusinessTypeNames()) {
            errors.add("Table " + appTable.getName()
                    + " must map to exactly one business type; already mapped to "
                    + appTable.getEntity().getName() + ", cannot also map "
                    + extraBusinessTypeName + ".");
        }
    }

    public List<String> validateAppMapperDeclarations() {
        List<String> errors = new ArrayList<>();
        for (String duplicateMapperName : ruleBlock.getDuplicateMapperNames()) {
            errors.add("APP mapper " + duplicateMapperName
                    + " is declared multiple times.");
        }
        Map<String, AppTable> tableByBusinessType = referencedAppTables();
        Map<String, Integer> tableCountByBusinessType = new LinkedHashMap<>();
        for (var table : new HashSet<>(tableByBusinessType.values())) tableCountByBusinessType.put(table.getEntity().getName(), 1);
        for (AppTables appTables : ruleBlock.getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (appTable.getEntity() != null && appTable.getEntity().isReal()) {
                    tableByBusinessType.putIfAbsent(appTable.getEntity().getName(), appTable);
                    tableCountByBusinessType.merge(appTable.getEntity().getName(), 1, Integer::sum);
                }
            }
        }
        for (AppMapper appMapper : ruleBlock.getAppMappers()) {
            AppTable appTable = appMapper.getAppTable();
            validateMapperParameters(appMapper, errors);
            if (appTable == null || appTable.getName() == null) {
                errors.add("Mapper table " + appMapper.getDeclaredTableName() + " is not defined.");
                continue;
            }
            if (!hasValidTableTarget(appTable, tableCountByBusinessType)) {
                errors.add("Mapper table " + appMapper.getName()
                        + " does not reference a stable APP table target.");
                continue;
            }
            if (!hasStableObjectKeyTargets(appTable, tableByBusinessType, tableCountByBusinessType,
                    Collections.newSetFromMap(new IdentityHashMap<>()))) {
                errors.add("Mapper table " + appMapper.getName()
                        + " depends on unstable APP table object key targets.");
                continue;
            }
            if (!hasStableMapperTableShape(appTable, tableByBusinessType, tableCountByBusinessType)) {
                errors.add("Mapper table " + appMapper.getName()
                        + " does not reference a generatable APP table.");
                continue;
            }
            Set<String> tableColumns = collectAppTableColumnNames(appTable, tableByBusinessType);
            Map<String, String> tableColumnTypes = collectAppTableColumnTypeNames(appTable, tableByBusinessType);
            validateMapperDuplicateSources(appMapper, tableColumns, errors);
            for (MapperItem mapperItem : appMapper.getMapperItemList()) {
                if (!tableColumns.contains(mapperItem.getName())) {
                    errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                            + " is not defined in APP table " + appTable.getName() + ".");
                }
            }
            validateMapperColumnTypes(appMapper, tableColumnTypes, errors);
            validateMapperSqlTypePrecisionScale(appMapper, errors);
            validateMapperOutputColumns(appMapper, tableColumns, errors);
        }
        return errors;
    }

    private void validateMapperParameters(AppMapper appMapper, List<String> errors) {
        if (!appMapper.getMapParameters().isEmpty()) {
            errors.add("Mapper " + appMapper.getName()
                    + " declares unsupported parameters: "
                    + String.join(", ", appMapper.getMapParameters()) + ".");
        }
    }

    private Set<String> collectAppTableColumnNames(AppTable appTable, Map<String, AppTable> tableByBusinessType) {
        Set<String> columns = new LinkedHashSet<>();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            columns.add(appColumn.getName());
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            addTableFieldColumns(appTable, columns, primaryKey, tableByBusinessType);
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                addTableFieldColumns(appTable, columns, keyReference.getSppField(), tableByBusinessType);
            }
        }
        addImplicitObjectFieldColumns(appTable, columns, tableByBusinessType);
        if (appTable.getEntity() != null) {
            for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
                if (((SppField)field).isStoredHere() && field.getArrayDimension() == 0 && isScalarDbField(field)) {
                    columns.add(field.getName());
                }
            }
        }
        return columns;
    }

    private Map<String, String> collectAppTableColumnTypeNames(AppTable appTable,
                                                               Map<String, AppTable> tableByBusinessType) {
        Map<String, String> columnTypes = new LinkedHashMap<>();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            putColumnType(columnTypes, appColumn.getName(), typeName(appColumn.getType()));
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            addTableFieldColumnTypes(appTable, columnTypes, primaryKey, tableByBusinessType);
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                addTableFieldColumnTypes(appTable, columnTypes, keyReference.getSppField(), tableByBusinessType);
            }
        }
        addImplicitObjectFieldColumnTypes(appTable, columnTypes, tableByBusinessType);
        if (appTable.getEntity() != null) {
            for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
                if (((SppField)field).isStoredHere() && field.getArrayDimension() == 0 && isScalarDbField(field)) {
                    putColumnType(columnTypes, field.getName(), typeName(field.getType()));
                }
            }
        }
        return columnTypes;
    }

    private void validateTableOutputColumns(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                            List<String> errors) {
        Map<String, String> outputColumns = new LinkedHashMap<>();
        Set<String> explicitColumnNames = new LinkedHashSet<>();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (!explicitColumnNames.add(appColumn.getName())) {
                errors.add("Table " + appTable.getName()
                        + " declares duplicate APP column " + appColumn.getName() + ".");
            }
            validateExplicitColumnModifiers(appTable, appColumn, errors);
            validateExplicitColumnPrecisionScale(appTable, appColumn, errors);
            validateExplicitColumnBusinessFieldShadow(appTable, appColumn, errors);
            addTableOutputColumn(appTable, outputColumns, appColumn.getName(),
                    "APP column " + appColumn.getName(), errors);
        }
        if (appTable.getEntity() != null) {
            for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
                if (((SppField)field).isStoredHere() && field.getArrayDimension() == 0 && isScalarDbField(field)
                        && !explicitColumnNames.contains(field.getName())) {
                    addTableOutputColumn(appTable, outputColumns, field.getName(),
                            "business field " + qualifiedFieldName((SppField) field), errors);
                }
            }
        }
        validateImplicitObjectExpansionCycles(appTable, tableByBusinessType, errors);
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            addTableFieldOutputColumns(appTable, outputColumns, explicitColumnNames, primaryKey,
                    tableByBusinessType,
                    "primary key " + appDdlFieldReferenceQualifiedName(primaryKey), errors);
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                addTableFieldOutputColumns(appTable, outputColumns, explicitColumnNames, keyReference.getSppField(),
                        tableByBusinessType,
                        "foreign key " + qualifiedFieldName(keyReference.getSppField()), errors);
            }
        }
        addImplicitObjectFieldOutputColumns(appTable, outputColumns, explicitColumnNames, tableByBusinessType, errors);
    }

    private void validateExplicitColumnModifiers(AppTable appTable, AppColumn appColumn, List<String> errors) {
        if (appColumn.getName() == null) {
            return;
        }
        if (appColumn.getPrimaryKeyModifierCount() > 1) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " declares primarykey multiple times.");
        }
        if (appColumn.getNullableModifierCount() > 1) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " declares null multiple times.");
        }
        if (appColumn.getNotNullModifierCount() > 1) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " declares notnull multiple times.");
        }
        if (appColumn.hasConflictingNullabilityModifiers()) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " cannot declare both null and notnull.");
        }
    }

    private void validateImplicitObjectExpansionCycles(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                                       List<String> errors) {
        if (appTable.getEntity() == null) {
            return;
        }
        Set<SppField> explicitlyExpandedFields = explicitlyExpandedObjectFields(appTable);
        for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isScalarDbField(field)
                    || explicitlyExpandedFields.contains(field)
                    || !(field instanceof SppField sppField)
                    || !(field.getType() instanceof SppClass refClass)) {
                continue;
            }
            if (hasRecursiveImplicitObjectExpansion(refClass,
                    Collections.newSetFromMap(new IdentityHashMap<>()))) {
                errors.add("Table " + appTable.getName() + " business field "
                        + qualifiedFieldName(sppField)
                        + " cannot be implicitly expanded because " + refClass.getName()
                        + " has recursive object fields; declare foreignkey("
                        + qualifiedFieldName(sppField) + ") or avoid recursive implicit expansion.");
            }
        }
    }

    private boolean hasRecursiveImplicitObjectExpansion(SppClass refClass, Set<SppClass> visiting) {
        if (refClass == null) {
            return false;
        }
        if (!visiting.add(refClass)) {
            return true;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isScalarDbField(field)
                    || !(field.getType() instanceof SppClass nestedClass)) {
                continue;
            }
            if (hasRecursiveImplicitObjectExpansion(nestedClass, visiting)) {
                visiting.remove(refClass);
                return true;
            }
        }
        visiting.remove(refClass);
        return false;
    }

    private void validateExplicitColumnPrecisionScale(AppTable appTable, AppColumn appColumn, List<String> errors) {
        if (appColumn.getType() == null || appColumn.getName() == null
                || (appColumn.getPrecision() <= 0 && appColumn.getScale() <= 0)) {
            return;
        }
        String typeName = appColumn.getType().getName();
        if (appColumn.getPrecision() > 0 && !supportsExplicitColumnPrecision(typeName)) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " with type " + typeName + " cannot declare precision.");
        }
        if (appColumn.getScale() > 0 && !supportsExplicitColumnScale(typeName)) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " with type " + typeName + " cannot declare scale.");
        }
    }

    private boolean supportsExplicitColumnPrecision(String typeName) {
        return "String".equals(typeName) || supportsExplicitColumnScale(typeName);
    }

    private boolean supportsExplicitColumnScale(String typeName) {
        return "double".equals(typeName) || "decimal".equals(typeName) || "BigDecimal".equals(typeName);
    }

    private void validateExplicitColumnBusinessFieldShadow(AppTable appTable, AppColumn appColumn, List<String> errors) {
        if (appTable.getEntity() == null || appColumn.getName() == null) {
            return;
        }
        SppLocalVar businessField = appTable.getEntity().getField(appColumn.getName());
        if (businessField == null) {
            return;
        }
        appColumn.setBusinessFieldShadow(true);
        if (businessField.getArrayDimension() != 0 || !isScalarDbField(businessField)) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " cannot shadow non-scalar business field "
                    + qualifiedFieldName((SppField) businessField) + ".");
            return;
        }
        String appColumnType = appColumn.getType() == null ? "<unknown>" : appColumn.getType().getName();
        String businessFieldType = businessField.getType() == null ? "<unknown>" : businessField.getType().getName();
        if (!appColumnType.equals(businessFieldType)) {
            errors.add("Table " + appTable.getName() + " APP column " + appColumn.getName()
                    + " shadows business field " + qualifiedFieldName((SppField) businessField)
                    + " but has type " + appColumnType + " instead of " + businessFieldType + ".");
        }
    }

    private void addTableFieldOutputColumns(AppTable appTable, Map<String, String> outputColumns,
                                            Set<String> explicitColumnNames, SppFieldReference fieldReference,
                                            Map<String, AppTable> tableByBusinessType, String source,
                                            List<String> errors) {
        SppField field = fieldReference == null ? null : fieldReference.getSppField();
        if (field == null || field.getArrayDimension() != 0) {
            return;
        }
        String objectPath = appDdlFieldReferenceObjectPath(fieldReference);
        if (isScalarDbField(field) && objectPath.contains(".")) {
            String columnName = appDdlColumnNameFromObjectPath(objectPath);
            if (!explicitColumnNames.contains(columnName)
                    && !outputColumns.containsKey(columnName)) {
                addTableOutputColumn(appTable, outputColumns, columnName, source, errors);
            }
            return;
        }
        addTableFieldOutputColumns(appTable, outputColumns, explicitColumnNames, field,
                tableByBusinessType, source, errors);
    }

    private void addTableFieldOutputColumns(AppTable appTable, Map<String, String> outputColumns,
                                            Set<String> explicitColumnNames, SppField field,
                                            Map<String, AppTable> tableByBusinessType, String source,
                                            List<String> errors) {
        if (field == null) {
            return;
        }
        if (field.getArrayDimension() != 0) {
            return;
        }
        if (isScalarDbField(field)) {
            if (explicitColumnNames.contains(field.getName())
                    || outputColumns.containsKey(field.getName())) {
                return;
            }
            addTableOutputColumn(appTable, outputColumns, field.getName(), source, errors);
            return;
        }
        AppTable targetTable = tableByBusinessType.get(field.getType().getName());
        if (targetTable == null) {
            addTableOutputColumn(appTable, outputColumns, field.getName(), source, errors);
            return;
        }
        for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(targetTable, tableByBusinessType)) {
            String columnName = generatedAppColumnName(appTable, field, targetPrimaryKeyName);
            if (!explicitColumnNames.contains(columnName)) {
                addTableOutputColumn(appTable, outputColumns, columnName, source, errors);
            }
        }
    }

    private void addTableOutputColumn(AppTable appTable, Map<String, String> outputColumns, String columnName,
                                      String source, List<String> errors) {
        if (columnName == null) {
            return;
        }
        String previousSource = outputColumns.putIfAbsent(columnName, source);
        // One business relationship may be expanded as a field, PK and FK.
        // These declarations constrain the same column rather than create distinct columns.
        String previousPath = previousSource == null ? null : tableColumnSourcePath(previousSource);
        String currentPath = tableColumnSourcePath(source);
        boolean sharedRelationKey = previousPath != null && previousPath.equals(currentPath);
        if (previousSource != null && !previousSource.equals(source) && !sharedRelationKey) {
            errors.add("Table " + appTable.getName() + " output column " + columnName
                    + " is produced by multiple APP table fields: " + previousSource + " and " + source + ".");
        }
    }

    private String tableColumnSourcePath(String source) {
        for (String prefix : List.of("primary key ", "foreign key ", "business field "))
            if (source.startsWith(prefix)) return source.substring(prefix.length());
        return null;
    }

    private void addTableFieldColumns(AppTable appTable, Set<String> columns, SppField field,
                                      Map<String, AppTable> tableByBusinessType) {
        if (field == null) {
            return;
        }
        if (field.getArrayDimension() != 0) {
            return;
        }
        if (isScalarDbField(field)) {
            columns.add(field.getName());
            return;
        }
        AppTable targetTable = tableByBusinessType.get(field.getType().getName());
        if (targetTable == null) {
            columns.add(field.getName());
            return;
        }
        for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(targetTable, tableByBusinessType)) {
            columns.add(generatedAppColumnName(appTable, field, targetPrimaryKeyName));
        }
    }

    private void addTableFieldColumns(AppTable appTable, Set<String> columns, SppFieldReference fieldReference,
                                      Map<String, AppTable> tableByBusinessType) {
        SppField field = fieldReference == null ? null : fieldReference.getSppField();
        if (field == null || field.getArrayDimension() != 0) {
            return;
        }
        String objectPath = appDdlFieldReferenceObjectPath(fieldReference);
        if (isScalarDbField(field) && objectPath.contains(".")) {
            columns.add(appDdlColumnNameFromObjectPath(objectPath));
            return;
        }
        addTableFieldColumns(appTable, columns, field, tableByBusinessType);
    }

    private void addTableFieldColumnTypes(AppTable appTable, Map<String, String> columnTypes, SppField field,
                                          Map<String, AppTable> tableByBusinessType) {
        if (field == null) {
            return;
        }
        if (field.getArrayDimension() != 0) {
            return;
        }
        if (isScalarDbField(field)) {
            putColumnType(columnTypes, field.getName(), typeName(field.getType()));
            return;
        }
        AppTable targetTable = tableByBusinessType.get(field.getType().getName());
        if (targetTable == null) {
            putColumnType(columnTypes, field.getName(), typeName(field.getType()));
            return;
        }
        for (Map.Entry<String, String> targetPrimaryKeyType : getPrimaryKeyColumnTypeNames(targetTable,
                tableByBusinessType).entrySet()) {
            putColumnType(columnTypes, generatedAppColumnName(appTable, field, targetPrimaryKeyType.getKey()),
                    targetPrimaryKeyType.getValue());
        }
    }

    private void addTableFieldColumnTypes(AppTable appTable, Map<String, String> columnTypes,
                                          SppFieldReference fieldReference,
                                          Map<String, AppTable> tableByBusinessType) {
        SppField field = fieldReference == null ? null : fieldReference.getSppField();
        if (field == null || field.getArrayDimension() != 0) {
            return;
        }
        String objectPath = appDdlFieldReferenceObjectPath(fieldReference);
        if (isScalarDbField(field) && objectPath.contains(".")) {
            putColumnType(columnTypes, appDdlColumnNameFromObjectPath(objectPath), typeName(field.getType()));
            return;
        }
        addTableFieldColumnTypes(appTable, columnTypes, field, tableByBusinessType);
    }

    private void addImplicitObjectFieldColumns(AppTable appTable, Set<String> columns,
                                               Map<String, AppTable> tableByBusinessType) {
        if (appTable.getEntity() == null) {
            return;
        }
        Set<SppField> explicitlyExpandedFields = explicitlyExpandedObjectFields(appTable);
        Map<String, AppTable> foreignTableByType = foreignTableByType(appTable, tableByBusinessType);
        for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isScalarDbField(field)
                    || explicitlyExpandedFields.contains(field)) {
                continue;
            }
            addGeneratedObjectFieldColumns(appTable, columns, field, tableByBusinessType, foreignTableByType);
        }
    }

    private void addImplicitObjectFieldColumnTypes(AppTable appTable, Map<String, String> columnTypes,
                                                  Map<String, AppTable> tableByBusinessType) {
        if (appTable.getEntity() == null) {
            return;
        }
        Set<SppField> explicitlyExpandedFields = explicitlyExpandedObjectFields(appTable);
        Map<String, AppTable> foreignTableByType = foreignTableByType(appTable, tableByBusinessType);
        for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isScalarDbField(field)
                    || explicitlyExpandedFields.contains(field)) {
                continue;
            }
            addGeneratedObjectFieldColumnTypes(appTable, columnTypes, field, tableByBusinessType,
                    foreignTableByType);
        }
    }

    private void addImplicitObjectFieldOutputColumns(AppTable appTable, Map<String, String> outputColumns,
                                                    Set<String> explicitColumnNames,
                                                    Map<String, AppTable> tableByBusinessType,
                                                    List<String> errors) {
        if (appTable.getEntity() == null) {
            return;
        }
        Set<SppField> explicitlyExpandedFields = explicitlyExpandedObjectFields(appTable);
        Map<String, AppTable> foreignTableByType = foreignTableByType(appTable, tableByBusinessType);
        for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isScalarDbField(field)
                    || explicitlyExpandedFields.contains(field)) {
                continue;
            }
            addGeneratedObjectFieldOutputColumns(appTable, outputColumns, explicitColumnNames, field, tableByBusinessType,
                    foreignTableByType, errors);
        }
    }

    private void addGeneratedObjectFieldColumns(AppTable appTable, Set<String> columns, SppLocalVar field,
                                                Map<String, AppTable> tableByBusinessType,
                                                Map<String, AppTable> foreignTableByType) {
        AppTable foreignTable = foreignTableByType.get(field.getType().getName());
        if (foreignTable != null) {
            for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(foreignTable, tableByBusinessType)) {
                columns.add(generatedAppColumnName(appTable, field, targetPrimaryKeyName));
            }
            return;
        }
        if (field.getType() instanceof SppClass refClass) {
            addExpandedRefEntityColumns(appTable, columns, field.getName(), refClass,
                    containsSameType(appTable.getEntity(), field),
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private void addGeneratedObjectFieldOutputColumns(AppTable appTable, Map<String, String> outputColumns,
                                                      Set<String> explicitColumnNames,
                                                      SppLocalVar field,
                                                      Map<String, AppTable> tableByBusinessType,
                                                      Map<String, AppTable> foreignTableByType,
                                                      List<String> errors) {
        AppTable foreignTable = foreignTableByType.get(field.getType().getName());
        String source = "business field " + qualifiedFieldName((SppField) field);
        if (foreignTable != null) {
            for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(foreignTable, tableByBusinessType)) {
                String columnName = generatedAppColumnName(appTable, field, targetPrimaryKeyName);
                if (!explicitColumnNames.contains(columnName)) {
                    addTableOutputColumn(appTable, outputColumns, columnName, source, errors);
                }
            }
            return;
        }
        if (field.getType() instanceof SppClass refClass) {
            addExpandedRefEntityOutputColumns(appTable, outputColumns, explicitColumnNames, field.getName(), refClass,
                    containsSameType(appTable.getEntity(), field), source,
                    Collections.newSetFromMap(new IdentityHashMap<>()), errors);
        }
    }

    private void addGeneratedObjectFieldColumnTypes(AppTable appTable, Map<String, String> columnTypes,
                                                    SppLocalVar field,
                                                    Map<String, AppTable> tableByBusinessType,
                                                    Map<String, AppTable> foreignTableByType) {
        AppTable foreignTable = foreignTableByType.get(field.getType().getName());
        if (foreignTable != null) {
            for (Map.Entry<String, String> targetPrimaryKeyType : getPrimaryKeyColumnTypeNames(foreignTable,
                    tableByBusinessType).entrySet()) {
                putColumnType(columnTypes, generatedAppColumnName(appTable, field, targetPrimaryKeyType.getKey()),
                        targetPrimaryKeyType.getValue());
            }
            return;
        }
        if (field.getType() instanceof SppClass refClass) {
            addExpandedRefEntityColumnTypes(appTable, columnTypes, field.getName(), refClass,
                    containsSameType(appTable.getEntity(), field),
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private void addExpandedRefEntityColumns(AppTable appTable, Set<String> columns, String superName,
                                             SppClass refClass, boolean hasSameType,
                                             Set<SppClass> visiting) {
        if (refClass == null || !visiting.add(refClass)) {
            return;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            if (field.getArrayDimension() != 0) {
                continue;
            }
            String columnName = generatedAppColumnName(appTable, superName, field.getName(), hasSameType);
            if (isScalarDbField(field)) {
                columns.add(columnName);
            } else if (field.getType() instanceof SppClass nestedClass) {
                addExpandedRefEntityColumns(appTable, columns, columnName, nestedClass,
                        containsSameType(refClass, field), visiting);
            }
        }
        visiting.remove(refClass);
    }

    private void addExpandedRefEntityColumnTypes(AppTable appTable, Map<String, String> columnTypes,
                                                 String superName,
                                                 SppClass refClass, boolean hasSameType,
                                                 Set<SppClass> visiting) {
        if (refClass == null || !visiting.add(refClass)) {
            return;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            if (field.getArrayDimension() != 0) {
                continue;
            }
            String columnName = generatedAppColumnName(appTable, superName, field.getName(), hasSameType);
            if (isScalarDbField(field)) {
                putColumnType(columnTypes, columnName, typeName(field.getType()));
            } else if (field.getType() instanceof SppClass nestedClass) {
                addExpandedRefEntityColumnTypes(appTable, columnTypes, columnName, nestedClass,
                        containsSameType(refClass, field), visiting);
            }
        }
        visiting.remove(refClass);
    }

    private void addExpandedRefEntityOutputColumns(AppTable appTable, Map<String, String> outputColumns,
                                                   Set<String> explicitColumnNames,
                                                   String superName,
                                                   SppClass refClass, boolean hasSameType, String source,
                                                   Set<SppClass> visiting, List<String> errors) {
        if (refClass == null || !visiting.add(refClass)) {
            return;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            if (field.getArrayDimension() != 0) {
                continue;
            }
            String columnName = generatedAppColumnName(appTable, superName, field.getName(), hasSameType);
            if (isScalarDbField(field)) {
                if (!explicitColumnNames.contains(columnName)) {
                    addTableOutputColumn(appTable, outputColumns, columnName, source, errors);
                }
            } else if (field.getType() instanceof SppClass nestedClass) {
                addExpandedRefEntityOutputColumns(appTable, outputColumns, explicitColumnNames, columnName, nestedClass,
                        containsSameType(refClass, field), source, visiting, errors);
            }
        }
        visiting.remove(refClass);
    }

    private Set<SppField> explicitlyExpandedObjectFields(AppTable appTable) {
        Set<SppField> fields = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field != null && !isScalarDbField(field)) {
                fields.add(field);
                continue;
            }
            SppField rootField = appDdlFieldReferenceRootField(appTable, primaryKey);
            if (rootField != null && !isScalarDbField(rootField)) {
                fields.add(rootField);
            }
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field != null && !isScalarDbField(field)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private SppField appDdlFieldReferenceRootField(AppTable appTable, SppFieldReference fieldReference) {
        if (appTable == null || appTable.getEntity() == null || fieldReference == null) {
            return null;
        }
        String objectPath = appDdlFieldReferenceObjectPath(fieldReference);
        if (objectPath.isBlank()) {
            return null;
        }
        String rootFieldName = objectPath.contains(".")
                ? objectPath.substring(0, objectPath.indexOf('.'))
                : objectPath;
        return appTable.getEntity().getField(rootFieldName);
    }

    private Map<String, AppTable> foreignTableByType(AppTable appTable,
                                                     Map<String, AppTable> tableByBusinessType) {
        Map<String, AppTable> foreignTableByType = new LinkedHashMap<>();
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field == null || field.getType() == null || isScalarDbField(field)) {
                    continue;
                }
                AppTable targetTable = tableByBusinessType.get(field.getType().getName());
                if (targetTable != null) {
                    foreignTableByType.putIfAbsent(field.getType().getName(), targetTable);
                }
            }
        }
        return foreignTableByType;
    }

    private Set<String> getPrimaryKeyColumnNames(AppTable appTable, Map<String, AppTable> tableByBusinessType) {
        return getPrimaryKeyColumnNames(appTable, tableByBusinessType,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Set<String> getPrimaryKeyColumnNames(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                                 Set<AppTable> visiting) {
        Set<String> primaryKeyNames = new LinkedHashSet<>();
        if (appTable == null || !visiting.add(appTable)) {
            return primaryKeyNames;
        }
        for (SppFieldReference targetPrimaryKey : appTable.getPrimaryKeys()) {
            SppField targetPrimaryKeyField = targetPrimaryKey.getSppField();
            if (targetPrimaryKeyField != null && targetPrimaryKeyField.getArrayDimension() != 0) {
                continue;
            }
            String objectPath = appDdlFieldReferenceObjectPath(targetPrimaryKey);
            if (targetPrimaryKeyField != null && isScalarDbField(targetPrimaryKeyField)) {
                primaryKeyNames.add(appDdlColumnNameFromObjectPath(objectPath));
            } else if (targetPrimaryKeyField != null && targetPrimaryKeyField.getType() != null) {
                AppTable targetTable = tableByBusinessType.get(targetPrimaryKeyField.getType().getName());
                for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(targetTable, tableByBusinessType,
                        visiting)) {
                    primaryKeyNames.add(generatedAppColumnName(appTable, objectPath,
                            targetPrimaryKeyName, containsSameType(appTable.getEntity(), targetPrimaryKeyField)));
                }
            }
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                primaryKeyNames.add(appColumn.getName());
            }
        }
        visiting.remove(appTable);
        return primaryKeyNames;
    }

    private Map<String, String> getPrimaryKeyColumnTypeNames(AppTable appTable,
                                                             Map<String, AppTable> tableByBusinessType) {
        return getPrimaryKeyColumnTypeNames(appTable, tableByBusinessType,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Map<String, String> getPrimaryKeyColumnTypeNames(AppTable appTable,
                                                             Map<String, AppTable> tableByBusinessType,
                                                             Set<AppTable> visiting) {
        Map<String, String> primaryKeyTypes = new LinkedHashMap<>();
        if (appTable == null || !visiting.add(appTable)) {
            return primaryKeyTypes;
        }
        for (SppFieldReference targetPrimaryKey : appTable.getPrimaryKeys()) {
            SppField targetPrimaryKeyField = targetPrimaryKey.getSppField();
            if (targetPrimaryKeyField != null && targetPrimaryKeyField.getArrayDimension() != 0) {
                continue;
            }
            String objectPath = appDdlFieldReferenceObjectPath(targetPrimaryKey);
            if (targetPrimaryKeyField != null && isScalarDbField(targetPrimaryKeyField)) {
                putColumnType(primaryKeyTypes, appDdlColumnNameFromObjectPath(objectPath),
                        typeName(targetPrimaryKeyField.getType()));
            } else if (targetPrimaryKeyField != null && targetPrimaryKeyField.getType() != null) {
                AppTable targetTable = tableByBusinessType.get(targetPrimaryKeyField.getType().getName());
                for (Map.Entry<String, String> targetPrimaryKeyType : getPrimaryKeyColumnTypeNames(targetTable,
                        tableByBusinessType, visiting).entrySet()) {
                    putColumnType(primaryKeyTypes, generatedAppColumnName(appTable, objectPath,
                            targetPrimaryKeyType.getKey(), containsSameType(appTable.getEntity(), targetPrimaryKeyField)),
                            targetPrimaryKeyType.getValue());
                }
            }
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                putColumnType(primaryKeyTypes, appColumn.getName(), typeName(appColumn.getType()));
            }
        }
        visiting.remove(appTable);
        return primaryKeyTypes;
    }

    private void validateMapperDuplicateSources(AppMapper appMapper, Set<String> tableColumns, List<String> errors) {
        Set<String> sourceColumns = new LinkedHashSet<>();
        Set<String> duplicateSourceColumns = new LinkedHashSet<>();
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            if (!sourceColumns.add(mapperItem.getName())) {
                duplicateSourceColumns.add(mapperItem.getName());
            }
        }
        for (String duplicateSourceColumn : duplicateSourceColumns) {
            errors.add("Mapper " + appMapper.getName()
                    + " declares duplicate source column " + duplicateSourceColumn + ".");
        }
    }

    /** Explicit physical text columns may represent the two built-in exact numeric types. */
    public boolean supportsExactNumericTextColumn(String tableName, String columnName, String sqlType) {
        if (!"String".equals(sqlType)) { return false; }
        for (AppTables tables : ruleBlock.getAppTables()) {
            for (AppTable table : tables.getAppTableList()) {
                if (!tableName.equals(table.getName()) || table.getEntity() == null) { continue; }
                SppField field = table.getEntity().getField(columnName);
                if (field == null || field.getType() == null || field.getArrayDimension() != 0
                        || !com.servpp.lang.models.app.NumericBinding.isNumeric(field.getType().getName())) { return false; }
                String type = field.getPhysicalJavaType();
                try {
                    for (var binding : ruleBlock.getNumericBindings()) {
                        if (binding.matches(field.getDeclaredNumericSpecification())) { type = binding.javaType(); }
                    }
                    for (var binding : ruleBlock.getNumericBindings()) {
                        if (binding.target().equals(table.getEntity().getName() + "." + columnName)) { type = binding.javaType(); }
                    }
                } catch (IllegalArgumentException invalidBinding) {
                    // PHY validation reports this binding; mapper validation must remain a diagnostic pass.
                    return false;
                }
                return "java.math.BigDecimal".equals(type) || "java.math.BigInteger".equals(type);
            }
        }
        return false;
    }

    private void validateMapperColumnTypes(AppMapper appMapper, Map<String, String> tableColumnTypes,
                                           List<String> errors) {
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            String mapperType = mapperItem.getSqlType().getType();
            String tableType = tableColumnTypes.get(mapperItem.getName());
            if (mapperType == null || tableType == null || mapperType.equals(tableType)
                    || supportsExactNumericTextColumn(appMapper.getName(), mapperItem.getName(), mapperType)) {
                continue;
            }
            errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                    + " declares type " + mapperType + " but APP table column "
                    + mapperItem.getName() + " has type " + tableType + ".");
        }
    }

    private void validateMapperSqlTypePrecisionScale(AppMapper appMapper, List<String> errors) {
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            SQlType sqlType = mapperItem.getSqlType();
            if (sqlType == null || sqlType.getType() == null
                    || (sqlType.getPrecision() == null && sqlType.getScale() == null)) {
                continue;
            }
            String typeName = sqlType.getType();
            if (sqlType.getPrecision() != null && !supportsExplicitColumnPrecision(typeName)) {
                errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                        + " with type " + typeName + " cannot declare precision.");
            }
            if (sqlType.getScale() != null && !supportsExplicitColumnScale(typeName)) {
                errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                        + " with type " + typeName + " cannot declare scale.");
            }
        }
    }

    private void putColumnType(Map<String, String> columnTypes, String columnName, String typeName) {
        if (columnName != null && typeName != null) {
            columnTypes.putIfAbsent(columnName, typeName);
        }
    }

    private String typeName(SppCompilationUnit type) {
        return type == null ? null : type.getName();
    }

    private void validateMapperOutputColumns(AppMapper appMapper, Set<String> tableColumns, List<String> errors) {
        Map<String, String> outputColumns = new LinkedHashMap<>();
        for (String tableColumn : tableColumns) {
            outputColumns.put(tableColumn, tableColumn);
        }
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            if (!tableColumns.contains(mapperItem.getName())) {
                continue;
            }
            String outputName = mapperItem.getMapName() == null ? mapperItem.getName() : mapperItem.getMapName();
            outputColumns.remove(mapperItem.getName());
            String previousSource = outputColumns.putIfAbsent(outputName, mapperItem.getName());
            if (previousSource != null && !previousSource.equals(mapperItem.getName())) {
                errors.add("Mapper " + appMapper.getName() + " output column " + outputName
                        + " is produced by multiple APP table columns.");
            }
        }
    }

    private void validateTablePrimaryKeys(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                          Map<String, Integer> tableCountByBusinessType,
                                          List<String> errors) {
        if (!tableHasPrimaryKey(appTable)) {
            errors.add("Table " + appTable.getName()
                    + " must declare at least one primary key.");
            return;
        }
        Set<String> primaryKeyNames = new LinkedHashSet<>();
        Set<String> duplicatePrimaryKeyNames = new LinkedHashSet<>();
        Map<String, String> primaryKeyColumnNames = new LinkedHashMap<>();
        Set<String> duplicatePrimaryKeyColumnNames = new LinkedHashSet<>();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                recordPrimaryKeyColumnName(appColumn.getName(), primaryKeyColumnNames,
                        duplicatePrimaryKeyColumnNames);
            }
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField primaryKeyField = primaryKey.getSppField();
            if (primaryKeyField == null) {
                continue;
            }
            String primaryKeyName = appDdlFieldReferenceQualifiedName(primaryKey);
            if (!primaryKeyNames.add(primaryKeyName)) {
                duplicatePrimaryKeyNames.add(primaryKeyName);
                continue;
            }
            String primaryKeyObjectPath = appDdlFieldReferenceObjectPath(appTable, primaryKey);
            if (!appDdlFieldReferenceBelongsToAppTable(appTable, primaryKey)) {
                errors.add("Primary key " + primaryKeyName + " on table " + appTable.getName()
                        + " must belong to " + appTable.getEntity().getName() + ".");
                continue;
            }
            if (primaryKeyField.getArrayDimension() != 0) {
                errors.add("Primary key " + primaryKeyName + " on table " + appTable.getName()
                        + " must refer to a single field.");
                continue;
            }
            if (primaryKeyField.isQuantum()) {
                errors.add("Primary key " + primaryKeyName + " on table " + appTable.getName()
                        + " must not reference a quantum field; quantum values are stored in APP quantum stores.");
                continue;
            }
            if (isScalarDbField(primaryKeyField)) {
                String columnName = appDdlColumnNameFromObjectPath(primaryKeyObjectPath);
                recordPrimaryKeyColumnName(columnName, primaryKeyColumnNames,
                        duplicatePrimaryKeyColumnNames);
                continue;
            }
            String targetType = primaryKeyField.getType().getName();
            AppTable targetTable = tableByBusinessType.get(targetType);
            if (targetTable == null) {
                errors.add("Primary key " + primaryKeyName
                        + " on table " + appTable.getName()
                        + " references " + targetType + " without an APP table mapping.");
                continue;
            }
            if (!hasValidTableTarget(targetTable, tableCountByBusinessType)) {
                errors.add("Primary key " + primaryKeyName
                        + " on table " + appTable.getName()
                        + " references table " + targetTable.getName()
                        + " with invalid APP table target.");
                continue;
            }
            if (!tableHasPrimaryKey(targetTable)) {
                errors.add("Primary key " + primaryKeyName
                        + " on table " + appTable.getName()
                        + " references table " + targetTable.getName()
                        + " without primary keys.");
                continue;
            }
            if (!hasStableReferencedTableShape(targetTable, tableByBusinessType)) {
                errors.add("Primary key " + primaryKeyName
                        + " on table " + appTable.getName()
                        + " references table " + targetTable.getName()
                        + " with unstable APP table shape.");
                continue;
            }
            Set<String> targetPrimaryKeyColumnNames = getPrimaryKeyColumnNames(targetTable, tableByBusinessType);
            if (targetPrimaryKeyColumnNames.isEmpty()) {
                errors.add("Primary key " + primaryKeyName
                        + " on table " + appTable.getName()
                        + " references table " + targetTable.getName()
                        + " without expandable primary key columns.");
                continue;
            }
            for (String targetPrimaryKeyName : targetPrimaryKeyColumnNames) {
                String columnName = generatedAppColumnName(appTable, primaryKeyObjectPath, targetPrimaryKeyName,
                        containsSameType(appTable.getEntity(), primaryKeyField));
                recordPrimaryKeyColumnName(columnName, primaryKeyColumnNames,
                        duplicatePrimaryKeyColumnNames);
            }
        }
        for (String duplicatePrimaryKeyName : duplicatePrimaryKeyNames) {
            errors.add("Table " + appTable.getName()
                    + " declares duplicate primary key " + duplicatePrimaryKeyName + ".");
        }
        for (String duplicatePrimaryKeyColumnName : duplicatePrimaryKeyColumnNames) {
            errors.add("Table " + appTable.getName()
                    + " declares duplicate primary key column " + duplicatePrimaryKeyColumnName + ".");
        }
    }

    private void recordPrimaryKeyColumnName(String columnName, Map<String, String> primaryKeyColumnNames,
                                            Set<String> duplicatePrimaryKeyColumnNames) {
        if (columnName == null) {
            return;
        }
        String normalizedColumnName = columnName.toLowerCase(Locale.ROOT);
        String previousColumnName = primaryKeyColumnNames.putIfAbsent(normalizedColumnName, columnName);
        if (previousColumnName != null) {
            duplicatePrimaryKeyColumnNames.add(previousColumnName);
        }
    }

    private void validateTableForeignKeys(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                          Map<String, Integer> tableCountByBusinessType,
                                          List<String> errors) {
        Set<String> foreignKeyNames = new LinkedHashSet<>();
        Set<String> duplicateForeignKeyNames = new LinkedHashSet<>();
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            if (appForeign.getKeys().isEmpty()) {
                errors.add("Foreign key on table " + appTable.getName()
                        + " must declare at least one field.");
                continue;
            }
            Set<String> foreignKeyTargetTypes = new LinkedHashSet<>();
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField keyField = keyReference.getSppField();
                if (keyField == null) {
                    continue;
                }
                String keyName = qualifiedFieldName(keyField);
                if (!foreignKeyNames.add(keyName)) {
                    duplicateForeignKeyNames.add(keyName);
                }
                if (!fieldBelongsToAppTable(appTable, keyField)) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " must belong to " + appTable.getEntity().getName() + ".");
                    continue;
                }
                if (keyField.getArrayDimension() != 0) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " must refer to a single object field.");
                    continue;
                }
                if (keyField.isQuantum()) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " must not reference a quantum field; quantum values are stored in APP quantum stores.");
                    continue;
                }
                if (isScalarDbField(keyField)) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " must refer to an object field.");
                    continue;
                }
                String targetType = keyField.getType().getName();
                foreignKeyTargetTypes.add(targetType);
                if (appForeign.getDeclaredForeignReferenceName() != null
                        && appForeign.getForeignReference() == null) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references undefined type "
                            + appForeign.getDeclaredForeignReferenceName() + ".");
                    continue;
                }
                if (appForeign.getForeignReference() != null
                        && !(appForeign.getForeignReference() instanceof SppClass)) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references " + appForeign.getForeignReference().getName()
                            + " but it is not a business type.");
                    continue;
                }
                if (appForeign.getForeignReference() != null
                        && !targetType.equals(appForeign.getForeignReference().getName())) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references " + appForeign.getForeignReference().getName()
                            + " but field type is " + targetType + ".");
                    continue;
                }
                AppTable targetTable = tableByBusinessType.get(targetType);
                if (targetTable == null) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references " + targetType + " without an APP table mapping.");
                    continue;
                }
                if (!hasValidTableTarget(targetTable, tableCountByBusinessType)) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references table " + targetTable.getName()
                            + " with invalid APP table target.");
                    continue;
                }
                if (!tableHasPrimaryKey(targetTable)) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references table " + targetTable.getName()
                            + " without primary keys.");
                    continue;
                }
                if (!hasStableReferencedTableShape(targetTable, tableByBusinessType)) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references table " + targetTable.getName()
                            + " with unstable APP table shape.");
                    continue;
                }
                if (getPrimaryKeyColumnNames(targetTable, tableByBusinessType).isEmpty()) {
                    errors.add("Foreign key " + keyName + " on table " + appTable.getName()
                            + " references table " + targetTable.getName()
                            + " without expandable primary key columns.");
                }
            }
            if (foreignKeyTargetTypes.size() > 1) {
                errors.add("Foreign key on table " + appTable.getName()
                        + " must reference one target business type per declaration, but declares "
                        + String.join(" and ", foreignKeyTargetTypes) + ".");
            }
        }
        for (String duplicateForeignKeyName : duplicateForeignKeyNames) {
            errors.add("Table " + appTable.getName()
                    + " declares duplicate foreign key " + duplicateForeignKeyName + ".");
        }
    }

    private boolean hasValidTableTarget(AppTable appTable, Map<String, Integer> tableCountByBusinessType) {
        if (appTable == null || appTable.getEntity() == null || !appTable.getEntity().isReal()
                || ruleBlock.getDuplicateTableNames().contains(appTable.getName())
                || !appTable.getExtraBusinessTypeNames().isEmpty()
                || !appTable.getUndefinedBusinessTypeNames().isEmpty()
                || !appTable.getNonBusinessTypeNames().isEmpty()) {
            return false;
        }
        IConstance.CompilationUnitType expectedType = expectedTableTargetType(appTable.getCategory());
        return expectedType != null
                && appTable.getEntity().getType() == expectedType
                && tableCountByBusinessType.getOrDefault(appTable.getEntity().getName(), 0) == 1;
    }

    private boolean hasStableObjectKeyTargets(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                              Map<String, Integer> tableCountByBusinessType,
                                              Set<AppTable> visitingTables) {
        if (appTable == null || !visitingTables.add(appTable)) {
            return false;
        }
        try {
            for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
                if (!hasStableObjectKeyTarget(primaryKey.getSppField(), tableByBusinessType,
                        tableCountByBusinessType, visitingTables)) {
                    return false;
                }
            }
            for (AppForeign appForeign : appTable.getForeignKeys()) {
                for (SppFieldReference keyReference : appForeign.getKeys()) {
                    if (!hasStableObjectKeyTarget(keyReference.getSppField(), tableByBusinessType,
                            tableCountByBusinessType, visitingTables)) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            visitingTables.remove(appTable);
        }
    }

    private boolean hasStableObjectKeyTarget(SppField field, Map<String, AppTable> tableByBusinessType,
                                             Map<String, Integer> tableCountByBusinessType,
                                             Set<AppTable> visitingTables) {
        if (field == null || field.getArrayDimension() != 0 || isScalarDbField(field)) {
            return true;
        }
        AppTable targetTable = tableByBusinessType.get(field.getType().getName());
        return targetTable != null
                && hasValidTableTarget(targetTable, tableCountByBusinessType)
                && tableHasPrimaryKey(targetTable)
                && !getPrimaryKeyColumnNames(targetTable, tableByBusinessType).isEmpty()
                && hasStableObjectKeyTargets(targetTable, tableByBusinessType,
                        tableCountByBusinessType, visitingTables);
    }

    private boolean hasStableMapperTableShape(AppTable appTable, Map<String, AppTable> tableByBusinessType,
                                              Map<String, Integer> tableCountByBusinessType) {
        List<String> tableErrors = new ArrayList<>();
        validateTablePrimaryKeys(appTable, tableByBusinessType, tableCountByBusinessType, tableErrors);
        validateTableForeignKeys(appTable, tableByBusinessType, tableCountByBusinessType, tableErrors);
        validateTableOutputColumns(appTable, tableByBusinessType, tableErrors);
        return tableErrors.isEmpty();
    }

    private boolean hasStableReferencedTableShape(AppTable appTable, Map<String, AppTable> tableByBusinessType) {
        List<String> tableErrors = new ArrayList<>();
        validateTableOutputColumns(appTable, tableByBusinessType, tableErrors);
        return tableErrors.isEmpty();
    }

    private boolean fieldBelongsToAppTable(AppTable appTable, SppField field) {
        return field != null
                && field.getParent() != null
                && appTable.getEntity() != null
                && appTable.getEntity().getName().equals(field.getParent().getName());
    }

    private boolean tableHasPrimaryKey(AppTable appTable) {
        if (!appTable.getPrimaryKeys().isEmpty()) {
            return true;
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    private String generatedAppColumnName(AppTable appTable, SppLocalVar ownerField, String columnName) {
        return generatedAppColumnName(appTable, ownerField.getName(), columnName,
                containsSameType(appTable.getEntity(), ownerField));
    }

    private String generatedAppColumnName(AppTable appTable, String superName, String columnName,
                                          boolean duplicateTypeField) {
        String namingType = appTableNamingType(appTable, duplicateTypeField);
        String pascalName = NameUtil.firstToLowerCase(superName, false)
                + NameUtil.firstToLowerCase(columnName, false);
        return switch (namingType) {
            case "lowercase" -> columnName.toLowerCase(Locale.ROOT);
            case "UPPERCASE" -> columnName.toUpperCase(Locale.ROOT);
            case "camelCase" -> NameUtil.firstToLowerCase(pascalName, true);
            case "PascalCase" -> pascalName;
            case "pascallowercase" -> pascalName.toLowerCase(Locale.ROOT);
            case "PASCALUPPERCASE" -> pascalName.toUpperCase(Locale.ROOT);
            case "snake_case" -> (superName + "_" + columnName).toLowerCase(Locale.ROOT);
            default -> (superName + "_" + columnName).toLowerCase(Locale.ROOT);
        };
    }

    private boolean containsSameType(SppClass sppClass, SppLocalVar localVar) {
        if (sppClass == null || localVar == null || localVar.getType() == null) {
            return false;
        }
        int count = 0;
        for (SppLocalVar sppLocalVar : sppClass.getSppFieldList()) {
            if (sppLocalVar.getType() != null
                    && sppLocalVar.getType().getName().equals(localVar.getType().getName())
                    && sppLocalVar.getArrayDimension() == localVar.getArrayDimension()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private String appTableNamingType(AppTable appTable, boolean duplicateTypeField) {
        AppTableNamingConfig namingConfig = new AppTableNamingConfig();
        for (AppTables appTables : ruleBlock.getAppTables()) {
            if (appTables.getAppTableList().contains(appTable)) {
                applyInsideEntityNamingConfig(namingConfig, appTables.getAnnotations());
                break;
            }
        }
        applyInsideEntityNamingConfig(namingConfig, appTable.getAnnotations());
        return duplicateTypeField ? namingConfig.dupNamingType() : namingConfig.namingType();
    }

    private void applyInsideEntityNamingConfig(AppTableNamingConfig namingConfig, List<AnnotationDefine> annotations) {
        if (annotations == null) {
            return;
        }
        for (AnnotationDefine annotation : annotations) {
            if (!"insideEntity".equals(annotation.getName())) {
                continue;
            }
            String namingType = annotation.getParameters().get("namingType");
            if (namingType != null) {
                namingConfig.namingType = namingType;
            }
            String dupNamingType = annotation.getParameters().get("dupNamingType");
            if (dupNamingType != null) {
                namingConfig.dupNamingType = dupNamingType;
            }
        }
    }

    private static class AppTableNamingConfig {
        private String namingType = "snake_case";
        private String dupNamingType;

        private String namingType() {
            return namingType;
        }

        private String dupNamingType() {
            return dupNamingType == null ? namingType : dupNamingType;
        }
    }

    private String qualifiedFieldName(SppField field) {
        if (field == null) {
            return "<unknown>";
        }
        if (field.getParent() == null) {
            return field.getName();
        }
        return field.getParent().getName() + "." + field.getName();
    }

    private boolean isScalarDbField(SppLocalVar sppLocalVar) {
        IConstance.CompilationUnitType type = sppLocalVar.getType().getType();
        return type == null || type == IConstance.CompilationUnitType.Enum;
    }

    private IConstance.CompilationUnitType expectedTableTargetType(AppTables.Category category) {
        return switch (category) {
            case entities -> IConstance.CompilationUnitType.entity;
            case roles -> IConstance.CompilationUnitType.role;
            case contracts -> IConstance.CompilationUnitType.contract;
            case other -> null;
        };
    }

    public List<String> validateUniqueScopeServiceDeclarations() {
        List<String> errors = new ArrayList<>();
        Map<String, ScopeItem> declaredServices = new LinkedHashMap<>();
        Set<String> duplicatedServices = new LinkedHashSet<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                SppService service = scopeItem.getService();
                if (scopeItem.hasMultipleAsyncModifiers()) {
                    errors.add("Scope service " + scopeItemServiceName(scopeItem)
                            + " declares async multiple times: "
                            + String.join(", ", scopeItem.getAsyncModifierNames()) + ".");
                }
                if (service == null) {
                    if (scopeItem.getDeclaredServiceName() != null
                            && !"_default".equals(scopeItem.getDeclaredServiceName())) {
                        errors.add("Scope service " + scopeItem.getDeclaredServiceName()
                                + " is not defined or is not a service.");
                    }
                    continue;
                }
                ScopeItem previous = declaredServices.putIfAbsent(service.getFuncName(), scopeItem);
                if (previous != null && previous != scopeItem && duplicatedServices.add(service.getFuncName())) {
                    errors.add("Service " + service.getFuncName()
                            + " is declared multiple times in APP scope.");
                }
            }
        }
        return errors;
    }

    public List<String> validateTransactionResourceBindings() {
        List<String> errors = new ArrayList<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                SppService service = scopeItem.getService();
                String serviceName = scopeItemServiceName(scopeItem);
                if (scopeItem.hasMultipleTransactionModifiers()) {
                    errors.add("Scope service " + serviceName
                            + " declares multiple transaction modes: "
                            + String.join(", ", scopeItem.getTransactionModifierNames()) + ".");
                }
                if (scopeItem.hasMultipleResourceModifiers()) {
                    errors.add("Scope service " + serviceName
                            + " declares multiple transaction resources: "
                            + String.join(", ", scopeItem.getResourceModifierIds()) + ".");
                }
                if (scopeItem.getResourceId() == null
                        && scopeItem.getTransactionModifierNames().contains("DB")) {
                    errors.add("Scope service " + serviceName
                            + " declares unsupported DB transaction mode; omit the modifier or use AT, TCC, QUANTUM, or SAGA.");
                }
                if (ruleBlock.hasTransactionPolicy() && service != null && !service.isDeclaredQuantumService()
                        && service.getServiceType() == IConstance.ServiceType.update
                        && scopeItem.getTransactionType() == IConstance.TransactionType.tcc)
                    errors.add("Ordinary update " + serviceName + " cannot use TCC; TCC is a quantum implementation strategy.");
                if (ruleBlock.hasTransactionPolicy() && service != null && !service.isDeclaredQuantumService()
                        && service.getServiceType() == IConstance.ServiceType.update
                        && scopeItem.getTransactionType() == IConstance.TransactionType.quantum)
                    errors.add("Ordinary update " + serviceName + " cannot use QUANTUM mode; declare a quantum update for quantum facts.");
                if (ruleBlock.hasTransactionPolicy() && service != null && !service.isDeclaredQuantumService()
                        && scopeItem.getPersistenceRules().stream().map(rule -> rule.objectType()).distinct().count() > 1)
                    errors.add("Ordinary update " + serviceName + " must persist a single table.");
                if (service != null
                        && service.isDeclaredQuantumService()
                        && !scopeItem.getTransactionModifierNames().isEmpty()
                        && scopeItem.getTransactionType() != IConstance.TransactionType.quantum) {
                    errors.add("Quantum service " + service.getFuncName()
                            + " must be declared with QUANTUM transaction mode in APP scope.");
                }
                if (scopeItem.getResourceId() != null
                        && scopeItem.getTransactionType() != IConstance.TransactionType.db
                        && scopeItem.isResourceDeclaredBeforeTransactionMode()) {
                    errors.add("Transaction resource " + scopeItem.getResourceId()
                            + " on service " + serviceName
                            + " must be declared after AT, TCC, or QUANTUM mode.");
                }
                if (scopeItem.getResourceId() != null
                        && scopeItem.hasResourceArguments()
                        && scopeItem.isResourceDeclaredBeforeTransactionMode()) {
                    errors.add("Transaction resource " + scopeItem.getResourceId()
                            + " on service " + serviceName
                            + " must declare AT, TCC, or QUANTUM before resource arguments: "
                            + String.join(", ", scopeItem.getResourceArgumentNames()) + ".");
                }
                if (scopeItem.getResourceId() != null
                        && scopeItem.getTransactionType() != IConstance.TransactionType.tcc
                        && scopeItem.getTransactionType() != IConstance.TransactionType.quantum
                        && scopeItem.hasResourceArguments()) {
                    errors.add("Transaction resource " + scopeItem.getResourceId()
                            + " on service " + serviceName
                            + " must not declare resource arguments unless it is TCC or QUANTUM: "
                            + String.join(", ", scopeItem.getResourceArgumentNames()) + ".");
                }
                if (service == null || !scopeItem.isLocal()
                        || !requiresLocalAtomicUpdateBinding(scopeItem)) {
                    continue;
                }
                if (scopeItem.getResourceId() != null
                        && scopeItem.getTransactionType() == IConstance.TransactionType.db) {
                    errors.add("Transaction resource " + scopeItem.getResourceId()
                            + " on service " + service.getFuncName()
                            + " must declare AT, TCC, or QUANTUM mode.");
                }
                if (service.getType() != IConstance.CompilationUnitType.atomicservice
                        || service.getServiceType() != IConstance.ServiceType.update) {
                    errors.add(transactionBindingName(scopeItem)
                            + " on service " + service.getFuncName()
                            + " must bind a local atomic update service.");
                }
            }
        }
        return errors;
    }

    private String scopeItemServiceName(ScopeItem scopeItem) {
        if (scopeItem.getService() != null) {
            return scopeItem.getService().getFuncName();
        }
        if (scopeItem.getDeclaredServiceName() != null) {
            return scopeItem.getDeclaredServiceName();
        }
        return "<unknown>";
    }

    private String transactionBindingName(ScopeItem scopeItem) {
        if (scopeItem.getResourceId() != null) {
            return "Transaction resource " + scopeItem.getResourceId();
        }
        return "Transaction mode " + scopeItem.getTransactionModeName();
    }

    private boolean requiresLocalAtomicUpdateBinding(ScopeItem scopeItem) {
        return scopeItem.getResourceId() != null
                || scopeItem.getTransactionType() == IConstance.TransactionType.at
                || scopeItem.getTransactionType() == IConstance.TransactionType.tcc
                || scopeItem.getTransactionType() == IConstance.TransactionType.quantum;
    }

    public List<String> validateTransactionResourceModes() {
        Map<String, String> resourceModes = new LinkedHashMap<>();
        Map<String, String> resourceServices = new LinkedHashMap<>();
        Map<String, Set<String>> quantumResourceAttributes = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                SppService service = scopeItem.getService();
                String resourceId = scopeItem.getResourceId();
                if (service == null || resourceId == null || resourceId.isBlank()) {
                    continue;
                }
                String mode = scopeItem.getTransactionModeName();
                addTransactionResourceMode(resourceId, mode, service.getFuncName(), resourceModes,
                        resourceServices, errors);
                if (scopeItem.getTransactionType() != IConstance.TransactionType.quantum) {
                    continue;
                }
                // A shared deployment resource hosts independent inferred conservation groups.
                // Only an explicit resource attribute contract fixes one conserved set.
                if (scopeItem.isQuantumResourceInferred()) continue;
                Set<String> attributes = new TreeSet<>(scopeItem.getQuantumConservationAttributeIds());
                Set<String> previousAttributes = quantumResourceAttributes.putIfAbsent(resourceId, attributes);
                if (previousAttributes != null && !previousAttributes.equals(attributes)) {
                    errors.add("Quantum resource " + resourceId + " uses multiple conserved attribute sets: "
                            + previousAttributes + " in " + resourceServices.get(resourceId)
                            + " and " + attributes + " in " + service.getFuncName());
                }
            }
        }
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service)
                    || unit.getType() != IConstance.CompilationUnitType.atomicservice
                    || !service.hasWriteOperations()) {
                continue;
            }
            addTransactionResourceMode(service.getTransactionResourceIdLiteral(), service.getTransactionModeName(),
                    service.getFuncName(), resourceModes, resourceServices, errors);
        }
        return errors;
    }

    private void addTransactionResourceMode(String resourceId, String mode, String serviceName,
                                            Map<String, String> resourceModes,
                                            Map<String, String> resourceServices,
                                            List<String> errors) {
        String previousMode = resourceModes.putIfAbsent(resourceId, mode);
        if (previousMode == null) {
            resourceServices.put(resourceId, serviceName);
            return;
        }
        if (!previousMode.equals(mode)) {
            errors.add("Resource " + resourceId + " uses multiple transaction modes: "
                    + previousMode + " in " + resourceServices.get(resourceId)
                    + " and " + mode + " in " + serviceName);
        }
    }

    public List<String> validateQuantumConservationDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().hasQuantumConservationAttributes()) {
                continue;
            }
            List<String> declaredAttributes = service.getScopeItem().getQuantumConservationAttributeIds();
            if (!service.getScopeItem().isLocal()
                    || unit.getType() != IConstance.CompilationUnitType.atomicservice
                    || service.getServiceType() != IConstance.ServiceType.update) {
                errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                        + " on service " + service.getFuncName()
                        + " must bind a local atomic update service.");
                continue;
            }
            if (new LinkedHashSet<>(declaredAttributes).size() < 2) {
                errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                        + " must declare at least two distinct quantum attributes");
            }
            validateQuantumAttributeDuplicates(service, declaredAttributes, errors);
            validateQuantumResourceStoreOwnership(service, declaredAttributes, errors);
            for (String attributeId : declaredAttributes) {
                String error = validateQuantumAttribute(attributeId);
                if (error != null) {
                    errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                            + " declares invalid attribute " + attributeId + ": " + error);
                    continue;
                }
                error = validateQuantumAttributeServiceParameter(service, attributeId);
                if (error != null) {
                    errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                            + " declares inaccessible attribute " + attributeId + " on service "
                            + service.getFuncName() + ": " + error);
                }
                if (!isDeclaredInQuantumStore(attributeId)) {
                    errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                            + " declares attribute " + attributeId
                            + " that is not assigned to any APP quantum store.");
                }
            }
            List<String> assignedAttributes = service.getAssignedQuantumAttributeIds();
            if (assignedAttributes.isEmpty()) {
                errors.add("Quantum service " + service.getFuncName()
                        + " must write declared quantum attributes in resource "
                        + service.getTransactionResourceIdLiteral());
                continue;
            }
            int distinctDeclaredAttributeCount = new LinkedHashSet<>(declaredAttributes).size();
            // Conservation is a subset of the quantum state, not its persistence allowlist.
            // All writes must still belong to the same quantum storage coordination scope.
            List<String> allQuantumAttributes = new ArrayList<>(declaredAttributes);
            for (String attribute : assignedAttributes) if (!allQuantumAttributes.contains(attribute)) allQuantumAttributes.add(attribute);
            if (allQuantumAttributes.size() != declaredAttributes.size())
                validateQuantumResourceStoreOwnership(service, allQuantumAttributes, errors);
            if (distinctDeclaredAttributeCount >= 2
                    && distinctDeclaredAttributeCount == declaredAttributes.size()) {
                validateQuantumConservationDirections(service, declaredAttributes, errors);
            }
        }
        return errors;
    }

    public List<String> validateQuantumStoreDeclarations() {
        List<String> errors = new ArrayList<>();
        for (String duplicateStoreName : ruleBlock.getDuplicateQuantumStoreNames()) {
            errors.add("Quantum store " + duplicateStoreName + " is declared multiple times.");
        }
        Map<String, String> storeByAttribute = new LinkedHashMap<>();
        for (com.servpp.lang.models.app.AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
            if (store.getAttributeIds().isEmpty()) {
                errors.add("Quantum store " + store.getName()
                        + " must declare at least one APP quantum attribute.");
            }
            Set<String> seen = new LinkedHashSet<>();
            for (String attributeId : store.getAttributeIds()) {
                if (!seen.add(attributeId)) {
                    errors.add("Quantum store " + store.getName()
                            + " declares duplicate attribute " + attributeId + ".");
                    continue;
                }
                String previousStore = storeByAttribute.putIfAbsent(attributeId, store.getName());
                if (previousStore != null && !previousStore.equals(store.getName())) {
                    errors.add("Quantum attribute " + attributeId
                            + " is assigned to multiple APP quantum stores: "
                            + previousStore + " and " + store.getName() + ".");
                }
                String error = validateQuantumAttribute(attributeId);
                var owner = mapsOfClass.get(attributeId.split("\\.")[0]);
                if (owner instanceof SppClass imported && imported.getReferenceSource() != null)
                    error = "external quantum attributes belong to their provider domain";
                if (error != null) {
                    errors.add("Quantum store " + store.getName()
                            + " declares invalid attribute " + attributeId + ": " + error);
                }
            }
        }
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppClass sppClass) || !unit.isReal()
                    || sppClass.getType() == IConstance.CompilationUnitType.reference && sppClass.getReferenceSource() != null) {
                continue;
            }
            for (SppField field : sppClass.getSppFieldList()) {
                if (field.isQuantum()) {
                    String attributeId = sppClass.getName() + "." + field.getName();
                    if (!storeByAttribute.containsKey(attributeId)) {
                        errors.add("Quantum field " + attributeId
                                + " must be assigned to an APP quantum store.");
                    }
                }
            }
        }
        return errors;
    }

    private boolean hasQuantumStore(String storeName) {
        for (AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
            if (store.getName().equals(storeName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasQuantumStoreAdapter(String storeName) {
        for (AppQuantumStoreAdapter adapter : ruleBlock.getAppQuantumStoreAdapters()) {
            if (adapter.getName().equals(storeName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeclaredInQuantumStore(String attributeId) {
        for (AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
            if (store.getAttributeIds().contains(attributeId)) {
                return true;
            }
        }
        return false;
    }

    private void validateQuantumResourceStoreOwnership(SppService service, List<String> declaredAttributes,
                                                       List<String> errors) {
        Map<String, List<String>> attributesByStore = new LinkedHashMap<>();
        for (String attributeId : declaredAttributes) {
            String storeName = quantumStoreNameForAttribute(attributeId);
            if (storeName != null) {
                attributesByStore.computeIfAbsent(storeName, ignored -> new ArrayList<>()).add(attributeId);
            }
        }
        if (attributesByStore.size() <= 1) {
            return;
        }
        List<String> storeAssignments = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : attributesByStore.entrySet()) {
            storeAssignments.add(entry.getKey() + "=" + entry.getValue());
        }
        errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                + " declares attributes across multiple APP quantum stores: "
                + String.join(", ", storeAssignments) + ".");
    }

    private String quantumStoreNameForAttribute(String attributeId) {
        for (AppQuantumStore store : ruleBlock.getAppQuantumStores()) {
            if (store.getAttributeIds().contains(attributeId)) {
                return store.getName();
            }
        }
        return null;
    }

    private void validateQuantumConservationDirections(SppService service, List<String> declaredAttributes,
                                                       List<String> errors) {
        Map<String, Set<String>> assignedDirections = service.getAssignedQuantumAttributeDirections();
        if (assignedDirections.isEmpty()) {
            return;
        }
        Set<String> directions = new LinkedHashSet<>();
        for (String attributeId : declaredAttributes) {
            Set<String> attributeDirections = assignedDirections.get(attributeId);
            if (attributeDirections != null) {
                directions.addAll(attributeDirections);
            }
        }
        if (!directions.isEmpty() && (!directions.contains("increase") || !directions.contains("decrease"))) {
            errors.add("Quantum service " + service.getFuncName()
                    + " must both increase and decrease declared quantum attributes in resource "
                    + service.getTransactionResourceIdLiteral() + ".");
        }
    }

    private String validateQuantumAttributeServiceParameter(SppService service, String attributeId) {
        String attributeOwnerType = objectType(attributeId);
        for (SppLocalVar parameter : service.getServiceBody().getSppLocalVarList()) {
            if (parameter.getType() != null
                    && attributeOwnerType.equals(parameter.getType().getName())
                    && parameter.getArrayDimension() <= 1) {
                return null;
            }
        }
        return "service parameters do not include a scalar or one-dimensional " + attributeOwnerType + " object";
    }

    private void validateQuantumAttributeDuplicates(SppService service, List<String> declaredAttributes,
                                                    List<String> errors) {
        Set<String> attributes = new LinkedHashSet<>();
        Set<String> duplicateAttributes = new LinkedHashSet<>();
        for (String attributeId : declaredAttributes) {
            if (!attributes.add(attributeId)) {
                duplicateAttributes.add(attributeId);
            }
        }
        for (String duplicateAttribute : duplicateAttributes) {
            errors.add("Quantum resource " + service.getTransactionResourceIdLiteral()
                    + " declares duplicate quantum attribute " + duplicateAttribute + ".");
        }
    }

    public List<String> validateRemoteScopeDeclarations() {
        List<String> errors = new ArrayList<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            if (scopeDefine.isLocal()) {
                continue;
            }
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                String serviceName = scopeItemServiceName(scopeItem);
                if (scopeItem.hasRepositoryHydrationRules()) {
                    errors.add("Remote service " + serviceName
                            + " cannot declare repository hydration rules; use a remote adapter instead.");
                }
                if (scopeItem.hasObjectSnapshotRules()) {
                    errors.add("Remote service " + serviceName
                            + " cannot declare object snapshot rules; snapshots belong to local runtime ownership.");
                }
                if (scopeItem.hasCompensationServices()) {
                    errors.add("Remote service " + serviceName
                            + " cannot declare compensation rules; use a remote adapter instead.");
                }
                if (scopeItem.getTransactionType() != IConstance.TransactionType.db
                        || scopeItem.getResourceId() != null) {
                    errors.add("Remote service " + serviceName
                            + " cannot declare local transaction mode or resource; use a remote adapter instead.");
                }
            }
        }
        return errors;
    }

    public List<String> validateRemoteCompensationDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().hasRemoteCompensationServices()) {
                continue;
            }
            if (service.getScopeItem().isLocal()) {
                errors.add("Remote compensation rule on service " + service.getFuncName()
                        + " must be declared in remote scope.");
                continue;
            }
            if (!isRemoteCompensationSource(service)) {
                errors.add("Remote compensation rule on service " + service.getFuncName()
                        + " must be declared on a remote atomic update service or remote scenario service.");
                continue;
            }
            validateRemoteCompensationDuplicates(service, errors);
            for (String compensationServiceName : service.getScopeItem().getRemoteCompensationServiceNames()) {
                SppService compensationService = findServiceByFuncName(compensationServiceName);
                if (compensationService == null) {
                    errors.add("Remote compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName() + " not found.");
                    continue;
                }
                if (compensationService.equals(service)) {
                    errors.add("Remote compensation service " + compensationServiceName
                            + " cannot compensate itself.");
                    continue;
                }
                if (compensationService.getType() != IConstance.CompilationUnitType.atomicservice
                        || compensationService.getScopeItem() == null
                        || compensationService.getScopeItem().isLocal()
                        || compensationService.getServiceType() != IConstance.ServiceType.update) {
                    errors.add("Remote compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName()
                            + " must be a remote atomic update service.");
                    continue;
                }
                if (compensationService.getScopeItem().hasRemoteCompensationServices()) {
                    errors.add("Remote compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName()
                            + " cannot declare nested remote compensation services.");
                    continue;
                }
                String parameterError = validateSameParameterList(service, compensationService,
                        "Remote compensation service " + compensationService.getFuncName(), service.getFuncName());
                if (parameterError != null) {
                    errors.add(parameterError);
                }
            }
        }
        return errors;
    }

    private boolean isRemoteCompensationSource(SppService service) {
        if (service.getType() == IConstance.CompilationUnitType.atomicservice) {
            return service.getServiceType() == IConstance.ServiceType.update;
        }
        return service.getType() == IConstance.CompilationUnitType.scenario;
    }

    private void validateRemoteCompensationDuplicates(SppService service, List<String> errors) {
        Set<String> compensationServices = new LinkedHashSet<>();
        Set<String> duplicateServices = new LinkedHashSet<>();
        for (String compensationServiceName : service.getScopeItem().getRemoteCompensationServiceNames()) {
            if (!compensationServices.add(compensationServiceName)) {
                duplicateServices.add(compensationServiceName);
            }
        }
        for (String duplicateService : duplicateServices) {
            errors.add("Remote compensation rule on service " + service.getFuncName()
                    + " declares duplicate remote compensation service " + duplicateService + ".");
        }
    }

    public List<String> validateAppServiceImplementations() {
        List<String> errors = new ArrayList<>();
        for (String duplicateServiceName : ruleBlock.getAppServices().getDuplicateServiceNames()) {
            errors.add("APP service implementation " + duplicateServiceName
                    + " is declared multiple times.");
        }
        for (AppService appService : ruleBlock.getAppServices().getAppServiceList()) {
            SppService service = appService.getSppService();
            if (service == null) {
                errors.add("APP service implementation " + appService.getName()
                        + " must bind a defined service.");
                continue;
            }
            if (service.isDeclaredQuantumService() && service.hasBusinessImplementation() && service.getDeclaredJavaStatements() != null
                    && service.getDeclaredJavaStatements().stream().anyMatch(statement -> statement instanceof com.servpp.lang.models.app.expr.IOperationExpression)) {
                errors.add("Quantum service " + service.getFuncName() + " already defines its updates in SPP; APP cannot define a second update implementation.");
            }
            if (service.getScopeItem() != null && !service.getScopeItem().isLocal()) {
                errors.add("APP service implementation " + service.getFuncName()
                        + " must bind a local scope service.");
            }
            List<String> writeOperationPaths = service.getWriteOperationPaths();
            if (service.getType() == IConstance.CompilationUnitType.atomicservice
                    && (service.getServiceType() == IConstance.ServiceType.check
                    || service.getServiceType() == IConstance.ServiceType.query)
                    && !writeOperationPaths.isEmpty()) {
                errors.add("APP " + service.getServiceType().name().toLowerCase(Locale.ROOT)
                        + " service implementation " + service.getFuncName()
                        + " cannot write business objects: "
                        + String.join(", ", writeOperationPaths)
                        + ". Move state changes to atomic update services.");
            }
        }
        return errors;
    }

    public List<String> validateHeaderDeclarations() {
        List<String> errors = new ArrayList<>();
        Set<String> emittedErrors = new LinkedHashSet<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            validateScopeHeaderReference(scopeDefine.getIn(), "input", emittedErrors);
            validateScopeHeaderReference(scopeDefine.getOut(), "output", emittedErrors);
        }
        for (AppHeader header : ruleBlock.getHeaders()) {
            for (AppHeader extendsHeader : header.getExtendsHeaders()) {
                if (extendsHeader.isTemp()) {
                    emittedErrors.add("Header " + header.getName()
                            + " extends undefined header " + extendsHeader.getName() + ".");
                }
            }
            validateHeaderDuplicateExtends(header, emittedErrors);
            validateHeaderFieldModifiers(header, emittedErrors);
            validateHeaderIdempotencyFieldTypes(header, emittedErrors);
            validateHeaderDuplicateDeclaredFields(header, emittedErrors);
            validateHeaderDeclaredFieldCollisions(header, emittedErrors);
            validateHeaderFieldOverrides(header, emittedErrors);
            validateHeaderInheritedFieldCollisions(header, emittedErrors);
        }
        errors.addAll(emittedErrors);

        Set<AppHeader> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AppHeader> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AppHeader header : ruleBlock.getHeaders()) {
            detectHeaderInheritanceCycle(header, visiting, visited, errors);
        }
        return errors;
    }

    private void validateHeaderDuplicateExtends(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp()) {
            return;
        }
        for (String duplicateExtendsHeaderName : header.getDuplicateExtendsHeaderNames()) {
            errors.add("Header " + header.getName()
                    + " extends header " + duplicateExtendsHeaderName + " multiple times.");
        }
    }

    private void validateHeaderFieldModifiers(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp()) {
            return;
        }
        for (SppExtendField field : header.getSppExtendFields()) {
            if (field.getNotNullModifierCount() > 1) {
                errors.add("Header " + header.getName() + " field " + field.getName()
                        + " declares notnull multiple times.");
            }
            if (field.getUniqueModifierCount() > 1) {
                errors.add("Header " + header.getName() + " field " + field.getName()
                        + " declares unique multiple times.");
            }
        }
    }

    private void validateHeaderDuplicateDeclaredFields(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp()) {
            return;
        }
        for (String duplicateFieldName : header.getDuplicateFieldNames()) {
            errors.add("Header " + header.getName()
                    + " declares duplicate field " + duplicateFieldName + ".");
        }
    }

    private void validateHeaderIdempotencyFieldTypes(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp()) {
            return;
        }
        for (SppExtendField field : header.getDeclaredUniqueIdempotencyFields()) {
            if (!header.isStringField(field)) {
                errors.add("Header " + header.getName() + " idempotency field "
                        + field.getName() + " must have type String.");
            }
        }
    }

    private void validateHeaderDeclaredFieldCollisions(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp() || hasHeaderInheritanceCycle(header)) {
            return;
        }
        for (SppExtendField field : header.getSppExtendFields()) {
            if (field.getOverrideSuper() != null) {
                continue;
            }
            SppExtendField inheritedField = findInheritedHeaderField(header, field.getName(),
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            if (inheritedField != null) {
                errors.add("Header " + header.getName() + " field " + field.getName()
                        + " duplicates inherited field " + field.getName() + ".");
            }
        }
    }

    private void validateHeaderFieldOverrides(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp()) {
            return;
        }
        for (SppExtendField field : header.getSppExtendFields()) {
            if (field.getOverrideSuper() == null) {
                continue;
            }
            SppExtendField inheritedField = findInheritedHeaderField(header, field.getOverrideSuper(),
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            if (inheritedField == null) {
                errors.add("Header " + header.getName() + " field " + field.getName()
                        + " overrides undefined inherited field " + field.getOverrideSuper() + ".");
                continue;
            }
            if (field.getType() == null || inheritedField.getType() == null
                    || !field.getType().getName().equals(inheritedField.getType().getName())) {
                errors.add("Header " + header.getName() + " field " + field.getName()
                        + " override target " + field.getOverrideSuper()
                        + " must have the same type.");
            }
        }
    }

    private void validateHeaderInheritedFieldCollisions(AppHeader header, Set<String> errors) {
        if (header == null || header.isTemp() || hasHeaderInheritanceCycle(header)) {
            return;
        }
        Map<String, String> inheritedFields = new LinkedHashMap<>();
        Map<String, String> duplicateFields = new LinkedHashMap<>();
        collectInheritedHeaderFields(header, inheritedFields, duplicateFields,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        for (Map.Entry<String, String> duplicateField : duplicateFields.entrySet()) {
            errors.add("Header " + header.getName() + " inherits field " + duplicateField.getKey()
                    + " from multiple headers: " + duplicateField.getValue() + ".");
        }
    }

    private void collectInheritedHeaderFields(AppHeader header, Map<String, String> inheritedFields,
                                              Map<String, String> duplicateFields, Set<AppHeader> visited) {
        for (AppHeader extendsHeader : header.getExtendsHeaders()) {
            if (extendsHeader.isTemp() || !visited.add(extendsHeader)) {
                continue;
            }
            for (SppExtendField field : extendsHeader.getSppExtendFields()) {
                String previousHeader = inheritedFields.putIfAbsent(field.getName(), extendsHeader.getName());
                if (previousHeader != null && !previousHeader.equals(extendsHeader.getName())) {
                    duplicateFields.putIfAbsent(field.getName(), previousHeader + " and " + extendsHeader.getName());
                }
            }
            collectInheritedHeaderFields(extendsHeader, inheritedFields, duplicateFields, visited);
        }
    }

    private boolean hasHeaderInheritanceCycle(AppHeader header) {
        return hasHeaderInheritanceCycle(header, Collections.newSetFromMap(new IdentityHashMap<>()),
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private boolean hasHeaderInheritanceCycle(AppHeader header, Set<AppHeader> visiting, Set<AppHeader> visited) {
        if (header == null || header.isTemp() || visited.contains(header)) {
            return false;
        }
        if (!visiting.add(header)) {
            return true;
        }
        for (AppHeader extendsHeader : header.getExtendsHeaders()) {
            if (hasHeaderInheritanceCycle(extendsHeader, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(header);
        visited.add(header);
        return false;
    }

    private SppExtendField findInheritedHeaderField(AppHeader header, String fieldName, Set<AppHeader> visited) {
        if (header == null || !visited.add(header)) {
            return null;
        }
        for (AppHeader extendsHeader : header.getExtendsHeaders()) {
            if (extendsHeader.isTemp()) {
                continue;
            }
            SppExtendField field = extendsHeader.findField(fieldName);
            if (field != null) {
                return field;
            }
            field = findInheritedHeaderField(extendsHeader, fieldName, visited);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private void validateScopeHeaderReference(AppHeader header, String direction, Set<String> errors) {
        if (header != null && header.isTemp()) {
            errors.add("Scope " + direction + " header " + header.getName()
                    + " is referenced but not defined.");
        }
    }

    private void detectHeaderInheritanceCycle(AppHeader header, Set<AppHeader> visiting, Set<AppHeader> visited,
                                              List<String> errors) {
        if (header == null || header.isTemp() || visited.contains(header)) {
            return;
        }
        if (!visiting.add(header)) {
            errors.add("Header inheritance cycle detected at " + header.getName() + ".");
            return;
        }
        for (AppHeader extendsHeader : header.getExtendsHeaders()) {
            detectHeaderInheritanceCycle(extendsHeader, visiting, visited, errors);
        }
        visiting.remove(header);
        visited.add(header);
    }

    public List<String> validatePersistenceDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) { continue; }
            var scope = service.getScopeItem();
            if (scope.getPersistenceRules().isEmpty() && scope.getPersistenceVersions().isEmpty()) { continue; }
            String label = "Persistence service " + service.getFuncName();
            if ("QUANTUM".equals(service.getTransactionModeName()) && !scope.hasQuantumConservationAttributes()
                    && service.getDeclaredJavaStatements() != null && !service.getDeclaredJavaStatements().isEmpty()) {
                errors.add(label + " without conserved attributes must only persist prepared records; declare value calculations in separate services.");
            }
            if (!scope.isLocal() || scope.isAsync()
                    || unit.getType() != IConstance.CompilationUnitType.atomicservice
                    || service.getServiceType() != IConstance.ServiceType.update) {
                errors.add(label + " must be a synchronous local atomic update service.");
            }
            Set<Integer> targets = new LinkedHashSet<>();
            for (var rule : scope.getPersistenceRules()) {
                if (rule.keys().isEmpty()) { errors.add(label + " must declare identity keys."); continue; }
                if (!targets.add(rule.argumentIndex())) { errors.add(label + " declares duplicate write targets."); }
                Set<String> keys = new LinkedHashSet<>();
                for (var key : rule.keys()) {
                    boolean performerKey = !key.getObjectType().equals(rule.objectType());
                    if (performerKey) {
                        var handler = new com.servpp.lang.general.app.RuleBlockHandler(new java.io.File(getName() + ".app"));
                        handler.setSppDomain(this);
                        var table = appTableByBusinessType().get(rule.objectType());
                        var inferred = table == null ? List.<RepositoryHydrationRule.KeyTarget>of()
                                : handler.repositoryTargetsFromRoleTakePrimaryKeys(null, table, rule.objectType());
                        var parameters = service.getServiceBody().getSppLocalVarList();
                        if (key.argumentIndex() < 0 || key.argumentIndex() >= parameters.size()
                                || parameters.get(key.argumentIndex()).getType() == null
                                || !rule.objectType().equals(parameters.get(key.argumentIndex()).getType().getName())
                                || !inferred.contains(key))
                            errors.add(label + " identity key must match a declared role performer primary key.");
                    } else validatePersistenceTarget(service, key, true, errors);
                    if (key.argumentIndex() != rule.argumentIndex()) {
                        errors.add(label + " identity keys must belong to the same argument and object type.");
                    }
                    if (!keys.add(performerKey ? key.objectPath() : key.getObjectIdPath())) { errors.add(label + " has duplicate identity keys."); }
                }
            }
            for (var number : scope.getBusinessNumbers()) {
                validatePersistenceTarget(service, number.target(), true, errors);
                var field = resolveFieldPath(mapsOfClass.get(number.target().getObjectType()), number.target().getObjectIdPath());
                if (field == null || !"String".equals(field.getType().getName()) || field.getBusinessMeaning() == null
                        || !number.prefix().matches("[A-Za-z0-9_-]+")
                        || scope.getPersistenceRules().stream().noneMatch(r -> r.argumentIndex() == number.target().argumentIndex() && ("insert".equals(r.operation()) || "insertIfAbsent".equals(r.operation()))))
                    errors.add(label + " business numbering requires a declared business string on an insert target and an alphanumeric prefix.");
            }
            Set<Integer> guarded = new LinkedHashSet<>();
            for (var version : scope.getPersistenceVersions()) {
                validatePersistenceTarget(service, version.expected(), true, errors);
                validatePersistenceTarget(service, version.proposed(), true, errors);
                int target = version.proposed().argumentIndex();
                var parameters = service.getServiceBody().getSppLocalVarList();
                if (target >= 0 && target < parameters.size() && parameters.get(target).getArrayDimension() == 1
                        && version.expected().argumentIndex() != target)
                    errors.add(label + " collection version guards use each participant internal query baseline.");
                if (!guarded.add(target)) { errors.add(label + " declares duplicate version guards."); }
                var matches = scope.getPersistenceRules().stream()
                        .filter(r -> r.argumentIndex() == target && "update".equals(r.operation())).toList();
                if (matches.size() != 1) { errors.add(label + " version guard requires exactly one update target."); }
                if (!version.expected().getObjectType().equals(version.proposed().getObjectType())
                        || !version.expected().getObjectIdPath().equals(version.proposed().getObjectIdPath())) {
                    errors.add(label + " version guard must compare the same field; a single participant uses its internal query baseline.");
                }
                for (var key : java.util.List.of(version.expected(), version.proposed())) {
                    SppField field = resolveFieldPath(mapsOfClass.get(key.getObjectType()), key.getObjectIdPath());
                    if (field != null && !java.util.Set.of("short", "int", "integer", "long").contains(field.getType().getName())) {
                        errors.add(label + " version field must have an integral numeric type.");
                    }
                }
            }
        }
        return errors;
    }

    private void validatePersistenceTarget(SppService service, RepositoryHydrationRule.KeyTarget key,
                                           boolean collectionAllowed, List<String> errors) {
        var parameters = service.getServiceBody().getSppLocalVarList();
        String label = "Persistence service " + service.getFuncName();
        if (key.argumentIndex() < 0 || key.argumentIndex() >= parameters.size()) {
            errors.add(label + " argument index is outside parameter list."); return;
        }
        SppLocalVar parameter = parameters.get(key.argumentIndex());
        if (parameter.getType() == null || !key.getObjectType().equals(parameter.getType().getName())) {
            errors.add(label + " target object type must match service parameter type."); return;
        }
        if (parameter.getArrayDimension() > (collectionAllowed ? 1 : 0)) {
            errors.add(label + " version guards require scalar parameters; writes support one-dimensional collections.");
        }
        if (key.isSingleton()) return;
        if (Set.of("@storageIdentity","@generatedIdentity").contains(key.getObjectIdPath()) && hasOpaqueAppPrimaryKey(parameter.getType().getName())) return;
        if (parameter.getType() instanceof SppClass type && ParticipantRoleBindings.isStorageIdentityPath(this,type,key.getObjectIdPath())) return;
        if (key.isPlatform()) {
            var table = appTableByBusinessType().get(key.getObjectType());
            String columnName = key.getObjectIdPath().substring("@platform:".length());
            var column = table == null ? null : table.getAppColumns().stream().filter(c -> columnName.equals(c.getName())).findFirst().orElse(null);
            if (column == null || column.isObjectBacked() || !java.util.Set.of("long", "int", "integer").contains(column.getType().getName()))
                errors.add(label + " platform version requires an integral APP-only column: " + key.objectPath());
            return;
        }
        SppField field = resolveFieldPath(parameter.getType(), key.getObjectIdPath());
        if (field == null || !isObjectIdField(field)
                || firstArrayFieldPath(parameter.getType(), key.getObjectIdPath()) != null) {
            errors.add(label + " target field must be an existing scalar property: " + key.objectPath());
        }
    }

    private List<String> validateWebDeclarations() {
        List<String> errors = new ArrayList<>();
        for (String action : ruleBlock.getInvalidWebActions()) errors.add("APP web action must be expose or start: " + action);
        for (List<String> names : List.of(ruleBlock.getWebServiceNames(), ruleBlock.getWebStartupNames())) {
            Set<String> seen = new LinkedHashSet<>();
            for (String name : names) {
                if (!seen.add(name)) errors.add("APP web service " + name + " is declared multiple times.");
                SppService service = mapsOfClass.values().stream().filter(unit -> unit instanceof SppService)
                        .map(unit -> (SppService)unit).filter(unit -> name.equals(unit.getFuncName())).findFirst().orElse(null);
                if (!(service instanceof SppScenario) || service.getScopeItem() == null
                        || !service.getScopeItem().isLocal() || service.getScopeItem().isAsync()) {
                    errors.add("APP web service " + name + " must name a synchronous local scenario.");
                }
            }
        }
        return errors;
    }

    private List<String> validateExampleDeclarations() {
        List<String> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String name : ruleBlock.getExampleServiceNames()) {
            if (!seen.add(name)) errors.add("APP example " + name + " is declared multiple times.");
            SppService service = mapsOfClass.values().stream()
                    .filter(unit -> unit instanceof SppService)
                    .map(unit -> (SppService) unit)
                    .filter(unit -> name.equals(unit.getFuncName())).findFirst().orElse(null);
            if (!(service instanceof SppScenario) || service.getScopeItem() == null
                    || !service.getScopeItem().isLocal() || service.getScopeItem().isAsync()) {
                errors.add("APP example " + name + " must name a synchronous local scenario.");
                continue;
            }
            var fault = ruleBlock.getExampleFaults().get(name);
            var barrier = ruleBlock.getExampleBarriers().get(name);
            var restart = ruleBlock.getExampleRestarts().get(name);
            var jdbcFault = ruleBlock.getExampleJdbcFaults().get(name);
            if (jdbcFault != null) {
                if (!List.of("beforeCommit", "commitBefore", "commitAfter", "afterCommit").contains(jdbcFault.point())) {
                    errors.add("APP example " + name + " JDBC fault point is unsupported.");
                }
                var target = mapsOfClass.values().stream().filter(unit -> unit instanceof SppService)
                    .map(unit -> (SppService) unit).filter(unit -> jdbcFault.serviceName().equals(unit.getFuncName())).findFirst().orElse(null);
                if (target == null || target instanceof SppScenario || target.getScopeItem() == null
                    || !target.getScopeItem().isLocal() || target.getScopeItem().isAsync()
                    || target.getServiceType() != IConstance.ServiceType.update) {
                    errors.add("APP example " + name + " JDBC fault target must be a synchronous local atomic update.");
                }
                if (fault != null || barrier != null || restart != null) errors.add("APP example " + name + " JDBC fault cannot combine other controls.");
            }
            if (restart != null) {
                if (!restart.datasourceProperty().matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                    errors.add("APP example " + name + " restart datasource property must be a nonempty configuration name.");
                }
                var target = mapsOfClass.values().stream().filter(unit -> unit instanceof SppService)
                    .map(unit -> (SppService) unit).filter(unit -> restart.serviceName().equals(unit.getFuncName())).findFirst().orElse(null);
                if (!(target instanceof SppScenario) || target.getScopeItem() == null
                    || !target.getScopeItem().isLocal() || target.getScopeItem().isAsync()) {
                    errors.add("APP example " + name + " restart target must be a synchronous local scenario.");
                } else {
                    for (var parameter : target.getServiceBody().getSppLocalVarList()) {
                        var type = parameter.getType();
                        if (parameter.getArrayDimension() != 0 || type == null
                            || !(type.getType() == IConstance.CompilationUnitType.role || type.getType() == IConstance.CompilationUnitType.contract)) {
                            errors.add("APP example " + name + " restart requires scalar Role or Contract participants.");
                        }
                    }
                }
                if (fault != null || barrier != null) errors.add("APP example " + name + " restart cannot combine fault or barrier.");
            }
            if (barrier != null) {
                if (barrier.parties() < 2 || barrier.parties() > 64) {
                    errors.add("APP example " + name + " barrier requires 2 to 64 parties.");
                }
                var target = mapsOfClass.values().stream().filter(unit -> unit instanceof SppService)
                    .map(unit -> (SppService) unit).filter(unit -> barrier.serviceName().equals(unit.getFuncName())).findFirst().orElse(null);
                if (target == null || target instanceof SppScenario || target.getScopeItem() == null
                    || !target.getScopeItem().isLocal() || target.getScopeItem().isAsync()) {
                    errors.add("APP example " + name + " barrier target must be a synchronous local atomic service.");
                }
                if (fault != null) errors.add("APP example " + name + " cannot combine barrier and one-shot fault.");
            }
            if (fault != null) {
                if (!List.of("before", "after").contains(fault.timing())) {
                    errors.add("APP example " + name + " fault timing must be before or after.");
                }
                if (fault.times() < 1) {
                    errors.add("APP example " + name + " fault times must be a positive integer.");
                }
                if (fault.afterServiceName() != null) {
                    var anchor = findServiceByFuncName(fault.afterServiceName());
                    if (anchor == null || anchor instanceof SppScenario || anchor.getScopeItem() == null
                            || !anchor.getScopeItem().isLocal() || anchor.getScopeItem().isAsync()) {
                        errors.add("APP example " + name + " fault activation target must be a synchronous local atomic service.");
                    }
                }
                var target = mapsOfClass.values().stream().filter(unit -> unit instanceof SppService)
                        .map(unit -> (SppService) unit).filter(unit -> fault.serviceName().equals(unit.getFuncName()))
                        .findFirst().orElse(null);
                if (target == null || target instanceof SppScenario || target.getScopeItem() == null
                        || !target.getScopeItem().isLocal() || target.getScopeItem().isAsync()) {
                    errors.add("APP example " + name + " fault target must be a synchronous local atomic service.");
                }
            }
            for (var parameter : service.getServiceBody().getSppLocalVarList()) {
                var type = parameter.getType();
                if (parameter.getArrayDimension() != 0 || type == null
                        || !(type.getType() == IConstance.CompilationUnitType.role
                        || type.getType() == IConstance.CompilationUnitType.contract)) {
                    errors.add("APP example " + name + " requires scalar Role or Contract participants.");
                }
            }
        }
        return errors;
    }

    private List<String> validateBusinessIdempotencyDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) continue;
            var scope = service.getScopeItem();
            if (service.isBusinessManagedIdempotency()) {
                if (!scope.isLocal() || !(service instanceof SppScenario) || scope.isAsync() || scope.getBusinessIdempotencyModifiers() != 1)
                    errors.add("Business-managed idempotency for " + service.getFuncName() + " requires a synchronous local scenario and exactly one idempotency business modifier.");
            }
            if (scope.getIdempotencyKeyPaths().isEmpty()) continue;
            if (!scope.isLocal() || !(service instanceof SppScenario) || scope.isAsync() || service.isBusinessManagedIdempotency())
                errors.add("Declared idempotency requires a synchronous local scenario and cannot combine with idempotency business: " + service.getFuncName());
            var paths = new ArrayList<>(scope.getIdempotencyKeyPaths()); paths.addAll(scope.getIdempotencyFactsPaths());
            for (String path : paths) {
                boolean headerKey = path != null && path.startsWith("AppHeader.") && scope.getScopeDefine().getIn() != null
                        && scope.getScopeDefine().getIn().getSppExtendFields().stream().anyMatch(f -> path.equals("AppHeader."+f.getName()));
                if (path == null || (service.getServiceBody().getQualifieField(path) == null && !headerKey))
                    errors.add("Undefined APP idempotency input " + path + " in " + service.getFuncName());
            }
        }
        return errors;
    }

    public List<String> validateRepositoryHydrationDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) {
                continue;
            }
            if (service.getScopeItem().isRepositoryReadLocked()) {
                if (service.getScopeItem().getRepositoryReadLocks() != 1
                        || service.getScopeItem().getRepositoryHydrationRules().size() != 1
                        || service.getScopeItem().isAsync()) {
                    errors.add("Locked repository query " + service.getFuncName() + " requires one synchronous repository binding.");
                }

            }
            if (service.getScopeItem().isOptionalRepository()) {
                if (service.getScopeItem().getOptionalRepositoryModifiers() != 1
                        || service.getScopeItem().getRepositoryHydrationRules().size() != 1) {
                    errors.add("Optional repository query " + service.getFuncName() + " requires exactly one repository binding and one optional modifier.");
                }

            }
            if (!service.getScopeItem().hasRepositoryHydrationRules() && !service.getScopeItem().isOptionalRepository()) { continue; }
            if (!service.getScopeItem().isLocal()
                    || unit.getType() != IConstance.CompilationUnitType.atomicservice
                    || service.getServiceType() != IConstance.ServiceType.query) {
                errors.add("Repository hydration service " + service.getFuncName()
                        + " must be a local atomic query service.");
                continue;
            }
            for (RepositoryHydrationRule rule : service.getScopeItem().getRepositoryHydrationRules()) {
                validateRepositoryTargetOwnership(service, rule, errors);
                for (RepositoryHydrationRule.KeyTarget target : rule.keyTargets()) {
                    validateArgumentObjectRule("Repository", service,
                            target.argumentIndex(), target.objectPath(), errors);
                }
            }
            validateRepositoryDuplicateResponseTargets(service, errors);
            validateRepositoryResponseTargets(service, errors);
        }
        return errors;
    }

    private void validateRepositoryTargetOwnership(SppService service, RepositoryHydrationRule rule,
                                                   List<String> errors) {
        SppCompilationUnit targetType = mapsOfClass.get(rule.getObjectType());
        if (targetType != null && targetType.getType() == IConstance.CompilationUnitType.reference) {
            errors.add("Repository hydration service " + service.getFuncName()
                    + " targets Reference " + rule.getObjectType()
                    + "; declare it in remote scope and bind a PHY remote adapter instead of local repository.");
        }
    }

    private void validateRepositoryDuplicateResponseTargets(SppService service, List<String> errors) {
        Set<String> responseTargets = new LinkedHashSet<>();
        Set<String> duplicateTargets = new LinkedHashSet<>();
        for (RepositoryHydrationRule rule : service.getScopeItem().getRepositoryHydrationRules()) {
            if (!responseTargets.add(rule.responseMethodName())) {
                duplicateTargets.add(rule.responseMethodName());
            }
        }
        for (String duplicateTarget : duplicateTargets) {
            errors.add("Repository hydration service " + service.getFuncName()
                    + " declares duplicate response target " + duplicateTarget + ".");
        }
    }

    public List<String> validateTccResourceDeclarations() {
        List<String> errors = new ArrayList<>();
        Set<String> referencedLifecycleServices = collectTccLifecycleServiceNames();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null) {
                continue;
            }
            if (service.getScopeItem().hasTccReservationRules()
                    && service.getScopeItem().getTransactionType() != IConstance.TransactionType.tcc) {
                service.getScopeItem().getTccReservationRules().forEach(rule ->
                        errors.add("TCC reservation " + rule.sourceAttributeId() + " -> "
                                + rule.reservationAttributeId()
                                + " on service " + service.getFuncName()
                                + " must be declared on a TCC resource service."));
            }
            validateTccReservationTargets(service, errors);
            if (service.getScopeItem().getTransactionType() != IConstance.TransactionType.tcc) {
                continue;
            }
            if (service.getScopeItem().getResourceId() != null
                    && service.getScopeItem().getResourceServiceNames().isEmpty()) {
                if (!referencedLifecycleServices.contains(service.getFuncName())) {
                    errors.add("TCC resource " + service.getTransactionResourceIdLiteral()
                            + " on service " + service.getFuncName()
                            + " must declare confirm and cancel services or be referenced as a TCC lifecycle service.");
                }
                continue;
            }
            if (service.getScopeItem().getResourceServiceNames().isEmpty()) {
                continue;
            }
            if (!service.getScopeItem().isLocal()
                    || unit.getType() != IConstance.CompilationUnitType.atomicservice
                    || service.getServiceType() != IConstance.ServiceType.update) {
                errors.add("TCC resource " + service.getTransactionResourceIdLiteral()
                        + " on service " + service.getFuncName()
                        + " must bind a local atomic update service.");
                continue;
            }
            if (service.getScopeItem().getResourceServiceNames().size() != 2) {
                errors.add("TCC resource " + service.getTransactionResourceIdLiteral()
                        + " on service " + service.getFuncName()
                        + " must declare confirm and cancel services.");
                continue;
            }
            if (!validateTccLifecycleServiceNames(service, errors)) {
                continue;
            }
            if (Objects.equals(service.getScopeItem().getTccConfirmServiceName(),
                    service.getScopeItem().getTccCancelServiceName())) {
                errors.add("TCC resource " + service.getTransactionResourceIdLiteral()
                        + " on service " + service.getFuncName()
                        + " must declare different confirm and cancel services.");
                continue;
            }
            if (!service.getScopeItem().hasArgumentObjectSnapshotRules()) {
                errors.add("TCC try service " + service.getFuncName()
                        + " must declare argument object snapshot rules for recovery evidence.");
            }
            validateTccLifecycleService(service, service.getScopeItem().getTccConfirmServiceName(),
                    "confirm", errors);
            validateTccLifecycleService(service, service.getScopeItem().getTccCancelServiceName(),
                    "cancel", errors);
        }
        return errors;
    }

    private void validateTccReservationTargets(SppService service, List<String> errors) {
        for (TccReservationRule rule : service.getScopeItem().getTccReservationRules()) {
            AppColumn targetColumn = findAppDdlColumn(rule.reservationAttributeId());
            if (targetColumn == null) {
                errors.add("TCC reservation target attribute " + rule.reservationAttributeId()
                        + " on service " + service.getFuncName()
                        + " must be declared as an APP DDL platform column, not a .spp business field.");
                continue;
            }
            if (targetColumn.isPrimaryKey()) {
                errors.add("TCC reservation target attribute " + rule.reservationAttributeId()
                        + " on service " + service.getFuncName()
                        + " must not be an APP DDL primary key column.");
            }
            if (targetColumn.isObjectBacked()) {
                errors.add("TCC reservation target attribute " + rule.reservationAttributeId()
                        + " on service " + service.getFuncName()
                        + " must be an APP DDL platform column, but it is object-backed.");
            }
        }
    }

    private AppColumn findAppDdlColumn(String qualifiedName) {
        int separator = qualifiedName == null ? -1 : qualifiedName.indexOf('.');
        if (separator <= 0 || separator == qualifiedName.length() - 1) {
            return null;
        }
        String businessType = qualifiedName.substring(0, separator);
        String columnName = qualifiedName.substring(separator + 1);
        if (columnName.contains(".")) {
            return null;
        }
        for (AppTables appTables : ruleBlock.getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (appTable.getEntity() == null
                        || !businessType.equals(appTable.getEntity().getName())) {
                    continue;
                }
                for (AppColumn appColumn : appTable.getAppColumns()) {
                    if (columnName.equals(appColumn.getName())) {
                        return appColumn;
                    }
                }
            }
        }
        return null;
    }

    private boolean validateTccLifecycleServiceNames(SppService service, List<String> errors) {
        boolean valid = true;
        for (String lifecycleServiceName : service.getScopeItem().getResourceServiceNames()) {
            if (lifecycleServiceName.contains(".")) {
                valid = false;
                errors.add("TCC resource " + service.getTransactionResourceIdLiteral()
                        + " on service " + service.getFuncName()
                        + " lifecycle service " + lifecycleServiceName
                        + " must be a service name, not a field path.");
            }
        }
        return valid;
    }

    private Set<String> collectTccLifecycleServiceNames() {
        Set<String> serviceNames = new LinkedHashSet<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || service.getScopeItem().getTransactionType() != IConstance.TransactionType.tcc
                    || !service.getScopeItem().hasTccLifecycleServices()) {
                continue;
            }
            serviceNames.add(service.getScopeItem().getTccConfirmServiceName());
            serviceNames.add(service.getScopeItem().getTccCancelServiceName());
        }
        return serviceNames;
    }

    private void validateTccLifecycleService(SppService source, String lifecycleServiceName, String role,
                                             List<String> errors) {
        SppService lifecycleService = findServiceByFuncName(lifecycleServiceName);
        if (lifecycleService == null) {
            errors.add("TCC " + role + " service " + lifecycleServiceName
                    + " referenced by " + source.getFuncName() + " not found.");
            return;
        }
        if (lifecycleService.equals(source)) {
            errors.add("TCC " + role + " service " + lifecycleServiceName
                    + " cannot be the same as " + source.getFuncName() + ".");
            return;
        }
        if (lifecycleService.getType() != IConstance.CompilationUnitType.atomicservice
                || lifecycleService.getScopeItem() == null
                || !lifecycleService.getScopeItem().isLocal()
                || lifecycleService.getServiceType() != IConstance.ServiceType.update) {
            errors.add("TCC " + role + " service " + lifecycleServiceName
                    + " referenced by " + source.getFuncName()
                    + " must be a local atomic update service declared in APP scope.");
            return;
        }
        if (lifecycleService.getScopeItem().getTransactionType() != IConstance.TransactionType.tcc
                || !Objects.equals(source.getScopeItem().getResourceId(),
                lifecycleService.getScopeItem().getResourceId())) {
            errors.add("TCC " + role + " service " + lifecycleServiceName
                    + " referenced by " + source.getFuncName()
                    + " must be declared on the same TCC resource "
                    + source.getTransactionResourceIdLiteral() + ".");
            return;
        }
        if (lifecycleService.getScopeItem().hasTccLifecycleServices()) {
            errors.add("TCC " + role + " service " + lifecycleServiceName
                    + " referenced by " + source.getFuncName()
                    + " cannot declare nested TCC lifecycle services.");
            return;
        }
        String parameterError = validateSameParameterList(source, lifecycleService,
                "TCC " + role + " service " + lifecycleService.getFuncName(), source.getFuncName());
        if (parameterError != null) {
            errors.add(parameterError);
            return;
        }
        if (!lifecycleService.getScopeItem().hasArgumentObjectSnapshotRules()) {
            errors.add("TCC " + role + " service " + lifecycleServiceName
                    + " referenced by " + source.getFuncName()
                    + " must declare argument object snapshot rules for recovery evidence.");
            return;
        }
        validateTccLifecycleSnapshotEvidence(source, lifecycleService, role, errors);
    }

    private void validateTccLifecycleSnapshotEvidence(SppService source, SppService lifecycleService, String role,
                                                      List<String> errors) {
        Set<String> lifecycleSnapshotRules = argumentSnapshotRuleKeys(lifecycleService.getScopeItem());
        for (ObjectSnapshotRule sourceRule : source.getScopeItem().getObjectSnapshotRules()) {
            if (sourceRule.source() != ObjectSnapshotRule.Source.argument) {
                continue;
            }
            if (!lifecycleSnapshotRules.contains(argumentSnapshotRuleKey(sourceRule))) {
                errors.add("TCC " + role + " service " + lifecycleService.getFuncName()
                        + " referenced by " + source.getFuncName()
                        + " must declare matching argument object snapshot rule "
                        + snapshotRuleName(sourceRule) + ".");
            }
        }
    }

    public List<String> validateCompensationDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().hasCompensationServices()) {
                continue;
            }
            if (!service.getScopeItem().isLocal()) {
                errors.add("Compensation rule on service " + service.getFuncName()
                        + " must be declared in local scope.");
                continue;
            }
            if (!validateCompensationSource(service, errors)) {
                continue;
            }
            validateCompensationDuplicates(service, errors);
            for (String compensationServiceName : service.getScopeItem().getCompensationServiceNames()) {
                SppService compensationService = findServiceByFuncName(compensationServiceName);
                if (compensationService == null) {
                    errors.add("Compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName() + " not found.");
                    continue;
                }
                if (compensationService.equals(service)) {
                    errors.add("Compensation service " + compensationServiceName
                            + " cannot compensate itself.");
                    continue;
                }
                boolean scenarioTarget = compensationService instanceof SppScenario;
                if ((!scenarioTarget && (compensationService.getType() != IConstance.CompilationUnitType.atomicservice
                        || compensationService.getServiceType() != IConstance.ServiceType.update))
                        || compensationService.getScopeItem() == null
                        || !compensationService.getScopeItem().isLocal()
                        ) {
                    errors.add("Compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName()
                            + " must be a local atomic update service.");
                    continue;
                }
                if (scenarioTarget ? !hasReachableSourceLocalTransaction(compensationService,
                        Collections.newSetFromMap(new IdentityHashMap<>()))
                        : !requiresLocalAtomicUpdateBinding(compensationService.getScopeItem())) {
                    errors.add("Compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName()
                            + (scenarioTarget ? " must declare a local transaction boundary in SPP."
                                              : " must declare a local transaction mode or resource."));
                    continue;
                }
                if (compensationService.getScopeItem().hasCompensationServices()
                        || (scenarioTarget && reachesCompensationDeclaration(compensationService,
                                Collections.newSetFromMap(new IdentityHashMap<>())))) {
                    errors.add("Compensation service " + compensationServiceName
                            + " referenced by " + service.getFuncName()
                            + " cannot declare nested compensation services.");
                    continue;
                }
                String parameterError = validateSameParameterList(service, compensationService,
                        "Compensation service " + compensationService.getFuncName(), service.getFuncName());
                if (parameterError != null) {
                    errors.add(parameterError);
                }
            }
        }
        return errors;
    }

    /** A local scenario may delegate to another source scenario that owns its complete local boundary. */
    private boolean hasReachableSourceLocalTransaction(SppService service, Set<SppService> visited) {
        if (service == null || !visited.add(service) || service.getScopeItem() == null
                || !service.getScopeItem().isLocal()) { return false; }
        return hasReachableSourceLocalTransaction(service.getServiceBody().getSppServiceCallList(), visited);
    }

    private boolean hasReachableSourceLocalTransaction(List<IServiceBodyStatement> statements, Set<SppService> visited) {
        if (statements == null) { return false; }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionBlock block && block.getDeclaredMode() != null
                    && !"SAGA".equals(block.getDeclaredMode()) && hasServiceCall(block.getTransactionCallList())) { return true; }
            if (statement instanceof SppServiceCall call && call.getCallee() instanceof SppScenario
                    && hasReachableSourceLocalTransaction(call.getCallee(), visited)) { return true; }
            if (statement instanceof ServiceBaseBody body
                    && hasReachableSourceLocalTransaction(body.getSppServiceCallList(), visited)) { return true; }
        }
        return false;
    }

    private boolean reachesCompensationDeclaration(SppService service, Set<SppService> visited) {
        if (service == null || !visited.add(service)) { return false; }
        if (service.getScopeItem() != null && service.getScopeItem().hasCompensationServices()) { return true; }
        return reachesCompensationDeclaration(service.getServiceBody().getSppServiceCallList(), visited);
    }

    private boolean reachesCompensationDeclaration(List<IServiceBodyStatement> statements, Set<SppService> visited) {
        if (statements == null) { return false; }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall call && reachesCompensationDeclaration(call.getCallee(), visited)) { return true; }
            if (statement instanceof ServiceBaseBody body && reachesCompensationDeclaration(body.getSppServiceCallList(), visited)) { return true; }
        }
        return false;
    }

    private void validateCompensationDuplicates(SppService service, List<String> errors) {
        Set<String> compensationServices = new LinkedHashSet<>();
        Set<String> duplicateServices = new LinkedHashSet<>();
        for (String compensationServiceName : service.getScopeItem().getCompensationServiceNames()) {
            if (!compensationServices.add(compensationServiceName)) {
                duplicateServices.add(compensationServiceName);
            }
        }
        for (String duplicateService : duplicateServices) {
            errors.add("Compensation rule on service " + service.getFuncName()
                    + " declares duplicate compensation service " + duplicateService + ".");
        }
    }

    private boolean validateCompensationSource(SppService service, List<String> errors) {
        if (service.getType() == IConstance.CompilationUnitType.atomicservice) {
            if (service.getServiceType() != IConstance.ServiceType.update) {
                errors.add("Compensation rule on service " + service.getFuncName()
                        + " must be declared on a local saga scenario or local atomic update transaction service.");
                return false;
            }
            if (!requiresLocalAtomicUpdateBinding(service.getScopeItem())) {
                errors.add("Compensated service " + service.getFuncName()
                        + " must declare a local transaction mode or resource for compensation recovery.");
            }
            return true;
        }
        if (service.getType() == IConstance.CompilationUnitType.scenario
                && service.getScopeItem().getTransactionType() == IConstance.TransactionType.saga) {
            return true;
        }
        errors.add("Compensation rule on service " + service.getFuncName()
                + " must be declared on a local saga scenario or local atomic update transaction service.");
        return false;
    }

    public List<String> validateCompensationSnapshotDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().hasCompensationServices()) {
                continue;
            }
            if (unit.getType() == IConstance.CompilationUnitType.atomicservice
                    && !service.getScopeItem().hasArgumentObjectSnapshotRules()) {
                errors.add("Compensated service " + service.getFuncName()
                        + " must declare argument object snapshot rules for recovery evidence.");
            }
            if (unit.getType() == IConstance.CompilationUnitType.atomicservice
                    && service.getScopeItem().hasArgumentObjectSnapshotRules()
                    && !hasAfterExecutionArgumentObjectSnapshotRules(service.getScopeItem())) {
                errors.add("Compensated service " + service.getFuncName()
                        + " must declare after-execution argument object snapshot rules for rollback evidence.");
            }
            for (String compensationServiceName : service.getScopeItem().getCompensationServiceNames()) {
                SppService compensationService = findServiceByFuncName(compensationServiceName);
                if (compensationService == null
                        || compensationService.getType() != IConstance.CompilationUnitType.atomicservice
                        || compensationService.getScopeItem() == null
                        || !compensationService.getScopeItem().isLocal()
                        || compensationService.getServiceType() != IConstance.ServiceType.update) {
                    continue;
                }
                if (!compensationService.getScopeItem().hasArgumentObjectSnapshotRules()) {
                    errors.add("Compensation service " + compensationService.getFuncName()
                            + " referenced by " + service.getFuncName()
                            + " must declare argument object snapshot rules for recovery evidence.");
                    continue;
                }
                if (!hasAfterExecutionArgumentObjectSnapshotRules(compensationService.getScopeItem())) {
                    errors.add("Compensation service " + compensationService.getFuncName()
                            + " referenced by " + service.getFuncName()
                            + " must declare after-execution argument object snapshot rules for rollback evidence.");
                    continue;
                }
                if (unit.getType() == IConstance.CompilationUnitType.atomicservice
                        && service.getScopeItem().hasArgumentObjectSnapshotRules()) {
                    validateCompensationSnapshotEvidence(service, compensationService, errors);
                } else if (unit.getType() == IConstance.CompilationUnitType.scenario
                        && service.getScopeItem().getTransactionType() == IConstance.TransactionType.saga) {
                    validateSagaCompensationSnapshotEvidence(service, compensationService, errors);
                }
            }
        }
        return errors;
    }

    private boolean hasAfterExecutionArgumentObjectSnapshotRules(ScopeItem scopeItem) {
        for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
            if (rule.source() == ObjectSnapshotRule.Source.argument
                    && snapshotSavesAfterExecution(rule.timing())) {
                return true;
            }
        }
        return false;
    }

    private boolean snapshotSavesAfterExecution(ObjectSnapshotRule.Timing timing) {
        return timing == ObjectSnapshotRule.Timing.after
                || timing == ObjectSnapshotRule.Timing.afterCommit;
    }

    private void validateCompensationSnapshotEvidence(SppService source, SppService compensationService,
                                                       List<String> errors) {
        Set<String> compensationSnapshotRules = argumentSnapshotRuleKeys(compensationService.getScopeItem());
        for (ObjectSnapshotRule sourceRule : source.getScopeItem().getObjectSnapshotRules()) {
            if (sourceRule.source() != ObjectSnapshotRule.Source.argument) {
                continue;
            }
            if (!compensationSnapshotRules.contains(argumentSnapshotRuleKey(sourceRule))) {
                errors.add("Compensation service " + compensationService.getFuncName()
                        + " referenced by " + source.getFuncName()
                        + " must declare matching argument object snapshot rule "
                        + snapshotRuleName(sourceRule) + ".");
            }
        }
    }

    private void validateSagaCompensationSnapshotEvidence(SppService saga, SppService compensationService,
                                                          List<String> errors) {
        Set<String> compensationSnapshotObjectRules =
                argumentSnapshotObjectRuleKeys(compensationService.getScopeItem());
        List<SppLocalVar> parameters = saga.getServiceBody().getSppLocalVarList();
        for (int i = 0; i < parameters.size(); i++) {
            SppLocalVar parameter = parameters.get(i);
            if (parameter.getArrayDimension() != 0 || !(parameter.getType() instanceof SppClass)) {
                continue;
            }
            String key = i + "|" + parameter.getType().getName();
            if (!compensationSnapshotObjectRules.contains(key)) {
                errors.add("Compensation service " + compensationService.getFuncName()
                        + " referenced by saga " + saga.getFuncName()
                        + " must declare argument object snapshot rule for saga parameter "
                        + i + " " + parameter.getType().getName() + ".");
            }
        }
    }

    public List<String> validateSnapshotDeclarations() {
        List<String> errors = new ArrayList<>();
        for (ScopeDefine scopeDefine : ruleBlock.getAppScope().getScopelist()) {
            for (ScopeItem scopeItem : scopeDefine.getScopeItems()) {
                SppService service = scopeItem.getService();
                if (service == null || !scopeItem.isLocal() || !scopeItem.hasObjectSnapshotRules()) {
                    continue;
                }
                if (service.getType() != IConstance.CompilationUnitType.atomicservice
                        || service.getServiceType() != IConstance.ServiceType.update) {
                    errors.add("Snapshot rule on service " + service.getFuncName()
                            + " must bind a local atomic update service.");
                    continue;
                }
                validateTransactionSnapshotTraceHeader(service, scopeItem, errors);
                for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
                    if (rule.source() == ObjectSnapshotRule.Source.argument) {
                        for (String objectIdPath : rule.objectIdPaths()) {
                            validateSnapshotArgumentObjectRule(service, rule, objectIdPath, errors);
                        }
                    } else if (rule.source() == ObjectSnapshotRule.Source.current) {
                        for (String objectIdPath : rule.objectIdPaths()) {
                            validateCurrentObjectRule("Snapshot",
                                    snapshotObjectPath(rule, objectIdPath), errors);
                        }
                    }
                }
                validateSnapshotTimingDuplicates(service, scopeItem.getObjectSnapshotRules(), errors);
            }
        }
        return errors;
    }

    private void validateTransactionSnapshotTraceHeader(SppService service, ScopeItem scopeItem,
                                                        List<String> errors) {
        if (!hasTransactionSnapshotRule(scopeItem)) {
            return;
        }
        AppHeader in = scopeItem.getScopeDefine() == null ? null : scopeItem.getScopeDefine().getIn();
        if (in == null || !in.hasUniqueIdempotencyField()) {
            errors.add("Transaction snapshot rule on service " + service.getFuncName()
                    + " must use an input header with unique globalSerialNumber or messageId.");
        }
    }

    private boolean hasTransactionSnapshotRule(ScopeItem scopeItem) {
        for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
            if (rule.source() == ObjectSnapshotRule.Source.transaction) {
                return true;
            }
        }
        return false;
    }

    private void validateSnapshotTimingDuplicates(SppService service, List<ObjectSnapshotRule> rules,
                                                  List<String> errors) {
        Map<String, ObjectSnapshotRule> snapshotPhases = new LinkedHashMap<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (ObjectSnapshotRule rule : rules) {
            for (ObjectSnapshotRule.Timing timing : effectiveSnapshotTimings(rule.timing())) {
                String key = rule.source() + "|" + rule.argumentIndex() + "|" + rule.getObjectType() + "|"
                        + String.join(",", rule.objectIdPaths()) + "|" + timing;
                ObjectSnapshotRule previous = snapshotPhases.putIfAbsent(key, rule);
                if (previous != null && emitted.add(key)) {
                    errors.add("Snapshot rule on service " + service.getFuncName()
                            + " declares duplicate " + timing + " snapshot for "
                            + snapshotRuleName(rule) + ".");
                }
            }
        }
    }

    private List<ObjectSnapshotRule.Timing> effectiveSnapshotTimings(ObjectSnapshotRule.Timing timing) {
        if (timing == ObjectSnapshotRule.Timing.afterCommit) {
            return List.of(ObjectSnapshotRule.Timing.after, ObjectSnapshotRule.Timing.commit);
        }
        return List.of(timing);
    }

    private String snapshotRuleName(ObjectSnapshotRule rule) {
        if (rule.source() == ObjectSnapshotRule.Source.argument) {
            return "argument(" + rule.argumentIndex() + ", " + snapshotObjectPathName(rule) + ")";
        }
        if (rule.source() == ObjectSnapshotRule.Source.transaction) {
            return "transaction";
        }
        return "current(" + snapshotObjectPathName(rule) + ")";
    }

    private String snapshotObjectPathName(ObjectSnapshotRule rule) {
        if (rule.objectIdPaths().size() == 1) {
            return rule.objectPath();
        }
        return rule.getObjectType() + ".(" + String.join(", ", rule.objectIdPaths()) + ")";
    }

    private String snapshotObjectPath(ObjectSnapshotRule rule, String objectIdPath) {
        return objectIdPath == null || objectIdPath.isBlank()
                ? rule.objectPath()
                : rule.getObjectType() + "." + objectIdPath;
    }

    private void validateSnapshotArgumentObjectRule(SppService service, ObjectSnapshotRule rule,
                                                    String objectIdPath, List<String> errors) {
        if (rule.argumentIndex() < 0
                || rule.argumentIndex() >= service.getServiceBody().getSppLocalVarList().size()) {
            validateArgumentObjectRule("Snapshot", service, rule.argumentIndex(),
                    snapshotObjectPath(rule, objectIdPath), errors);
            return;
        }
        SppLocalVar argument = service.getServiceBody().getLocalVar(rule.argumentIndex());
        if (argument.getType() == null || rule.getObjectType().equals(argument.getType().getName())) {
            validateArgumentObjectRule("Snapshot", service, rule.argumentIndex(),
                    snapshotObjectPath(rule, objectIdPath), errors);
            return;
        }
        if (!rule.appDdlInferred()) {
            validateArgumentObjectRule("Snapshot", service, rule.argumentIndex(),
                    snapshotObjectPath(rule, objectIdPath), errors);
            return;
        }
        validateArgumentMemberObjectRule("Snapshot", service, rule.argumentIndex(), rule.getObjectType(),
                objectIdPath, errors);
    }

    private Set<String> argumentSnapshotRuleKeys(ScopeItem scopeItem) {
        Set<String> snapshotRules = new LinkedHashSet<>();
        for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
            if (rule.source() == ObjectSnapshotRule.Source.argument) {
                snapshotRules.add(argumentSnapshotRuleKey(rule));
            }
        }
        return snapshotRules;
    }

    private Set<String> argumentSnapshotObjectRuleKeys(ScopeItem scopeItem) {
        Set<String> snapshotRules = new LinkedHashSet<>();
        for (ObjectSnapshotRule rule : scopeItem.getObjectSnapshotRules()) {
            if (rule.source() == ObjectSnapshotRule.Source.argument) {
                snapshotRules.add(rule.argumentIndex() + "|" + objectType(rule.objectPath()));
            }
        }
        return snapshotRules;
    }

    private String argumentSnapshotRuleKey(ObjectSnapshotRule rule) {
        return rule.argumentIndex() + "|" + rule.getObjectType() + "|" + String.join(",", rule.objectIdPaths());
    }

    public List<String> validateSagaIdempotencyHeaders() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().isLocal()
                    || service.getScopeItem().getTransactionType() != IConstance.TransactionType.saga) {
                continue;
            }
            AppHeader in = service.getScopeItem().getScopeDefine().getIn();
            if (in == null || !in.hasUniqueIdempotencyField()) {
                errors.add("Saga service " + service.getFuncName()
                        + " must use an input header with unique globalSerialNumber or messageId.");
            }
        }
        return errors;
    }

    public List<String> validateTransactionalIdempotencyHeaders() {
        // Local transactions do not require request idempotency headers.
        // Remote ingress establishes its technical request header through InvocationContext.
        return List.of();
    }

    private boolean requiresIdempotencyHeader(ScopeItem scopeItem) {
        return scopeItem.getTransactionType() == IConstance.TransactionType.saga
                || requiresLocalAtomicUpdateBinding(scopeItem);
    }

    public List<String> validateSagaScopeDeclarations() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || service.getScopeItem().getTransactionType() != IConstance.TransactionType.saga) {
                continue;
            }
            if (ruleBlock.getDeploymentEnvironment() != null && service.getServiceType() == IConstance.ServiceType.update
                    && service.getType() == IConstance.CompilationUnitType.atomicservice && service.getScopeItem().getResourceId() != null) continue;
            if (!service.getScopeItem().isLocal()) {
                errors.add("Saga service " + service.getFuncName()
                        + " must be declared in local scope.");
            }
            if (service.getScopeItem().getResourceId() != null) {
                errors.add("Saga service " + service.getFuncName()
                        + " must not declare a transaction resource; use saga as the scenario orchestration boundary.");
            }
            if (unit.getType() != IConstance.CompilationUnitType.scenario) {
                errors.add("Saga service " + service.getFuncName()
                        + " must be a Scenario service.");
            }
        }
        return errors;
    }

    public List<String> validateSagaTransactionCalls() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService saga)
                    || unit.getType() != IConstance.CompilationUnitType.scenario
                    || saga.getScopeItem() == null
                    || !saga.getScopeItem().isLocal()
                    || saga.getScopeItem().getTransactionType() != IConstance.TransactionType.saga) {
                continue;
            }
            if (!hasTransactionServiceCall(saga.getServiceBody().getSppServiceCallList())
                    && !hasReachableSourceLocalTransaction(saga, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                errors.add("Saga service " + saga.getFuncName()
                        + " must declare at least one service call in a transaction block.");
            }
            validateSagaNonTransactionCalls(saga, saga.getServiceBody().getSppServiceCallList(), errors);
            validateSagaTransactionCalls(saga, saga.getServiceBody().getSppServiceCallList(), errors);
        }
        return errors;
    }

    private boolean hasTransactionServiceCall(List<IServiceBodyStatement> statements) {
        if (statements == null) {
            return false;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionBlock transactionBlock
                    && hasServiceCall(transactionBlock.getTransactionCallList())) {
                return true;
            }
            if (statement instanceof ExecuteBlock executeBlock
                    && hasTransactionServiceCall(executeBlock.getExecuteCallList())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasServiceCall(List<IServiceBodyStatement> statements) {
        if (statements == null) {
            return false;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall) {
                return true;
            }
            if (statement instanceof ExecuteBlock executeBlock
                    && hasServiceCall(executeBlock.getExecuteCallList())) {
                return true;
            }
            if (statement instanceof TransactionBlock transactionBlock
                    && hasServiceCall(transactionBlock.getTransactionCallList())) {
                return true;
            }
        }
        return false;
    }

    private void validateSagaNonTransactionCalls(SppService saga, List<IServiceBodyStatement> statements,
                                                 List<String> errors) {
        if (statements == null) {
            return;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionBlock) {
                continue;
            }
            if (statement instanceof ExecuteBlock executeBlock) {
                validateSagaNonTransactionCalls(saga, executeBlock.getExecuteCallList(), errors);
                continue;
            }
            if (statement instanceof SppServiceCall serviceCall) {
                validateSagaNonTransactionCalls(saga, serviceCall.getGuardStatements(), errors);
                SppService callee = serviceCall.getCallee();
                if (callee == null) {
                    continue;
                }
                if (callee instanceof SppScenario && hasReachableSourceLocalTransaction(callee,
                        Collections.newSetFromMap(new IdentityHashMap<>()))) { continue; }
                if (callee.getScopeItem() != null
                        && callee.getScopeItem().isLocal()
                        && requiresLocalAtomicUpdateBinding(callee.getScopeItem())) {
                    errors.add("Saga service " + saga.getFuncName()
                            + " call " + callee.getFuncName()
                            + " must be placed inside a transaction block because it declares a local transaction mode or resource.");
                    continue;
                }
                SppService transactionalCallee = findReachableLocalTransactionalService(callee,
                        Collections.newSetFromMap(new IdentityHashMap<>()));
                if (transactionalCallee != null) {
                    errors.add("Saga service " + saga.getFuncName()
                            + " call " + callee.getFuncName()
                            + " reaches transactional service " + transactionalCallee.getFuncName()
                            + " outside a transaction block.");
                }
            }
        }
    }

    private SppService findReachableLocalTransactionalService(SppService service, Set<SppService> visitedServices) {
        if (service == null || !visitedServices.add(service)) {
            return null;
        }
        if (service.getScopeItem() != null
                && service.getScopeItem().isLocal()
                && requiresLocalAtomicUpdateBinding(service.getScopeItem())) {
            return service;
        }
        return findReachableLocalTransactionalService(service.getServiceBody().getSppServiceCallList(),
                visitedServices);
    }

    private SppService findReachableLocalTransactionalService(List<IServiceBodyStatement> statements,
                                                             Set<SppService> visitedServices) {
        if (statements == null) {
            return null;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall serviceCall) {
                SppService found = findReachableLocalTransactionalService(serviceCall.getCallee(), visitedServices);
                if (found != null) {
                    return found;
                }
                continue;
            }
            if (statement instanceof ExecuteBlock executeBlock) {
                SppService found = findReachableLocalTransactionalService(executeBlock.getExecuteCallList(),
                        visitedServices);
                if (found != null) {
                    return found;
                }
                continue;
            }
            if (statement instanceof TransactionBlock transactionBlock) {
                SppService found = findReachableLocalTransactionalService(transactionBlock.getTransactionCallList(),
                        visitedServices);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void validateSagaTransactionCalls(SppService saga, List<IServiceBodyStatement> statements,
                                              List<String> errors) {
        if (statements == null) {
            return;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof TransactionBlock transactionBlock) {
                validateSagaTransactionBlockCalls(saga, transactionBlock.getTransactionCallList(), errors);
                continue;
            }
            for (var children : statementChildren(statement)) validateSagaTransactionCalls(saga, children, errors);
        }
    }

    private void validateSagaTransactionBlockCalls(SppService saga, List<IServiceBodyStatement> statements,
                                                   List<String> errors) {
        if (statements == null) {
            return;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall serviceCall) {
                validateSagaTransactionalCallee(saga, serviceCall.getCallee(), errors);
                validateSagaTransactionBlockCalls(saga, serviceCall.getGuardStatements(), errors);
                continue;
            }
            if (statement instanceof ExecuteBlock executeBlock) {
                validateSagaTransactionBlockCalls(saga, executeBlock.getExecuteCallList(), errors);
                continue;
            }
            if (statement instanceof TransactionBlock transactionBlock) {
                validateSagaTransactionBlockCalls(saga, transactionBlock.getTransactionCallList(), errors);
            }
        }
    }

    private void validateSagaTransactionalCallee(SppService saga, SppService callee, List<String> errors) {
        if (callee == null) {
            return;
        }
        if (callee instanceof SppScenario && callee.getScopeItem() != null && callee.getScopeItem().isLocal()
                && hasReachableSourceLocalTransaction(callee, Collections.newSetFromMap(new IdentityHashMap<>()))) { return; }
        if (callee.getType() != IConstance.CompilationUnitType.atomicservice
                || callee.getServiceType() != IConstance.ServiceType.update) {
            errors.add("Saga service " + saga.getFuncName()
                    + " transaction block can only call local atomic update transaction services, but calls "
                    + callee.getFuncName() + ".");
            return;
        }
        if (callee.getScopeItem() != null && !callee.getScopeItem().isLocal()) {
            errors.add("Saga service " + saga.getFuncName()
                    + " transaction block can only call local atomic update transaction services, but calls remote service "
                    + callee.getFuncName() + ".");
            return;
        }
        if (callee.getScopeItem() == null || !callee.getScopeItem().isLocal()
                || !requiresLocalAtomicUpdateBinding(callee.getScopeItem())) {
            errors.add("Saga service " + saga.getFuncName()
                    + " transaction block call " + callee.getFuncName()
                    + " must declare a local transaction mode or resource.");
            return;
        }
        if (callee.getScopeItem().getTransactionType() == IConstance.TransactionType.quantum) {
            return;
        }
        if (!callee.getScopeItem().hasArgumentObjectSnapshotRules()) {
            errors.add("Saga service " + saga.getFuncName()
                    + " transaction block call " + callee.getFuncName()
                    + " must declare argument object snapshot rules for recovery evidence.");
        }
    }

    public List<String> validateCurrentSnapshotContexts() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService service) || service.getScopeItem() == null
                    || !service.getScopeItem().hasCurrentObjectSnapshotRules()) {
                continue;
            }
            for (ObjectSnapshotRule rule : service.getScopeItem().getObjectSnapshotRules()) {
                if (rule.source() != ObjectSnapshotRule.Source.current) {
                    continue;
                }
                if (!hasValidObjectIdPath(rule.objectPath())) {
                    continue;
                }
                validateCurrentSnapshotContext(service, rule, errors);
            }
        }
        return errors;
    }

    private void validateCurrentSnapshotContext(SppService targetService, ObjectSnapshotRule rule,
                                                List<String> errors) {
        boolean hasSagaCaller = false;
        boolean hasResolvableSagaCaller = false;
        Set<String> unresolvedSagaCallers = new LinkedHashSet<>();
        Map<String, List<String>> ambiguousSagaCallers = new LinkedHashMap<>();
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppService scenario)
                    || unit.getType() != IConstance.CompilationUnitType.scenario
                    || scenario.getScopeItem() == null
                    || !scenario.getScopeItem().isLocal()
                    || scenario.getScopeItem().getTransactionType() != IConstance.TransactionType.saga) {
                continue;
            }
            if (!serviceCallsTarget(scenario.getServiceBody().getSppServiceCallList(), targetService,
                    Collections.newSetFromMap(new IdentityHashMap<>()))) {
                continue;
            }
            hasSagaCaller = true;
            List<String> matchingParameters = currentSnapshotParameterNames(scenario, rule);
            if (matchingParameters.size() == 1) {
                hasResolvableSagaCaller = true;
            } else if (matchingParameters.size() > 1) {
                ambiguousSagaCallers.put(scenario.getFuncName(), matchingParameters);
            } else {
                unresolvedSagaCallers.add(scenario.getFuncName());
            }
        }
        if (!hasSagaCaller || (!hasResolvableSagaCaller && ambiguousSagaCallers.isEmpty())) {
            errors.add("Current snapshot " + rule.objectPath() + " on service "
                    + targetService.getFuncName()
                    + " must be resolvable from a local saga scenario parameter.");
            return;
        }
        for (String sagaName : unresolvedSagaCallers) {
            errors.add("Current snapshot " + rule.objectPath() + " on service "
                    + targetService.getFuncName()
                    + " is called by saga " + sagaName
                    + " but is not resolvable from that saga scenario parameter.");
        }
        for (Map.Entry<String, List<String>> entry : ambiguousSagaCallers.entrySet()) {
            errors.add("Current snapshot " + rule.objectPath() + " on service "
                    + targetService.getFuncName()
                    + " is ambiguous in saga " + entry.getKey()
                    + "; matched parameters " + String.join(", ", entry.getValue()) + ".");
        }
    }

    private List<String> currentSnapshotParameterNames(SppService service, ObjectSnapshotRule rule) {
        List<String> parameterNames = new ArrayList<>();
        for (SppLocalVar localVar : service.getServiceBody().getSppLocalVarList()) {
            if (localVar.getType() != null
                    && rule.getObjectType().equals(localVar.getType().getName())
                    && localVar.getArrayDimension() == 0
                    && hasFieldPath(localVar.getType(), rule.getObjectIdPath())) {
                parameterNames.add(localVar.getName());
            }
        }
        return parameterNames;
    }

    private boolean hasFieldPath(SppCompilationUnit objectType, String fieldPath) {
        return resolveFieldPath(objectType, fieldPath) != null;
    }

    private SppField resolveFieldPath(SppCompilationUnit objectType, String fieldPath) {
        if (!(objectType instanceof SppClass currentClass) || fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        String[] parts = fieldPath.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            SppField field = currentClass.getField(parts[i]);
            if (field == null) {
                return null;
            }
            if (i < parts.length - 1) {
                if (!(field.getType() instanceof SppClass nestedClass)) {
                    return null;
                }
                currentClass = nestedClass;
            } else {
                return field;
            }
        }
        return null;
    }

    private boolean serviceCallsTarget(List<IServiceBodyStatement> statements, SppService targetService,
                                       Set<SppService> visitedServices) {
        if (statements == null) {
            return false;
        }
        for (IServiceBodyStatement statement : statements) {
            if (statement instanceof SppServiceCall serviceCall) {
                SppService callee = serviceCall.getCallee();
                if (callee == null) {
                    continue;
                }
                if (callee.equals(targetService)) {
                    return true;
                }
                if (visitedServices.add(callee)
                        && serviceCallsTarget(callee.getServiceBody().getSppServiceCallList(), targetService,
                        visitedServices)) {
                    return true;
                }
            }
            if (statement instanceof ExecuteBlock executeBlock
                    && serviceCallsTarget(executeBlock.getExecuteCallList(), targetService, visitedServices)) {
                return true;
            }
            if (statement instanceof TransactionBlock transactionBlock
                    && serviceCallsTarget(transactionBlock.getTransactionCallList(), targetService, visitedServices)) {
                return true;
            }
        }
        return false;
    }

    private SppService findServiceByFuncName(String funcName) {
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (unit instanceof SppService service && funcName.equals(service.getFuncName())) {
                return service;
            }
        }
        return null;
    }

    private String validateSameParameterList(SppService source, SppService target, String targetLabel,
                                             String sourceServiceName) {
        List<SppLocalVar> sourceParameters = source.getServiceBody().getSppLocalVarList();
        List<SppLocalVar> targetParameters = target.getServiceBody().getSppLocalVarList();
        if (sourceParameters.size() != targetParameters.size()) {
            return targetLabel + " for " + sourceServiceName
                    + " must accept the same parameter list.";
        }
        for (int i = 0; i < sourceParameters.size(); i++) {
            if (!isSameTypeWithParameter(sourceParameters.get(i), targetParameters.get(i))) {
                return targetLabel + " parameter " + i + " must match " + sourceServiceName + ".";
            }
        }
        return null;
    }

    private void validateRepositoryResponseTargets(SppService service, List<String> errors) {
        for (RepositoryHydrationRule rule : service.getScopeItem().getRepositoryHydrationRules()) {
            SppLocalVar target = service.getServiceBody().getQualifieField(rule.responseMethodName());
            if (target == null) {
                errors.add("Repository query " + service.getFuncName() + " must hydrate a declared participant or its collection: " + rule.responseMethodName());
                continue;
            }
            if (target.getType() == null || !rule.getObjectType().equals(target.getType().getName()))
                errors.add("Repository query target " + rule.responseMethodName() + " must have participant type " + rule.getObjectType());
            if (target.getArrayDimension() > 1) errors.add("Repository queries support scalar participants or one-dimensional participant collections.");
            boolean collectionKey = rule.keyTargets().stream().anyMatch(key -> {
                int index = key.argumentIndex();
                return index >= 0 && index < service.getServiceBody().getSppLocalVarList().size()
                        && service.getServiceBody().getLocalVar(index).getArrayDimension() != 0;
            });
            if (collectionKey && !isIdentityBatchQuery(service, rule, target))
                errors.add("Repository query " + service.getFuncName()
                        + " collection keys must hydrate the same participant collection.");
            if ((service.getScopeItem().isRepositoryReadLocked() || service.getScopeItem().isOptionalRepository())
                    && target.getArrayDimension() != 0 && !isIdentityBatchQuery(service, rule, target))
                errors.add("Locked/optional repository query " + service.getFuncName() + " requires a scalar participant; collection queries accept empty results.");
        }
    }

    private boolean isIdentityBatchQuery(SppService service, RepositoryHydrationRule rule, SppLocalVar target) {
        return target.getArrayDimension() == 1 && rule.keyTargets().stream().allMatch(key -> {
            var parameter = service.getServiceBody().getLocalVar(key.argumentIndex());
            return parameter == target && parameter.getType().getName().equals(rule.getObjectType());
        });
    }

    private SppLocalVar findReturnValueByName(SppService service, String name) {
        for (SppLocalVar returnValue : service.getReturns().getSppLocalVarList()) {
            if (name.equals(returnValue.getName())) {
                return returnValue;
            }
        }
        return null;
    }

    private void validateArgumentObjectRule(String label, SppService service, int argumentIndex, String objectPath,
                                            List<String> errors) {
        if (argumentIndex < 0 || argumentIndex >= service.getServiceBody().getSppLocalVarList().size()) {
            errors.add(label + " argument index " + argumentIndex
                    + " is outside service " + service.getFuncName() + " parameter list.");
            return;
        }
        SppLocalVar argument = service.getServiceBody().getLocalVar(argumentIndex);
        if (argument.getType() == null
                || !argumentObjectRuleMatchesParameter(label, argument, objectType(objectPath))) {
            errors.add(label + " argument(" + argumentIndex + ", "
                    + objectPath + ") object type must match service parameter type "
                    + (argument.getType() == null ? "<unknown>" : argument.getType().getName()) + ".");
            return;
        }
        if (argument.getArrayDimension() != 0 && !("Repository".equals(label) && argument.getArrayDimension() == 1)) {
            errors.add(label + " argument(" + argumentIndex + ", "
                    + objectPath + ") must refer to a single object parameter, but service parameter type is "
                    + argument.getType().getName() + argument.getArrayString() + ".");
            return;
        }
        String objectIdPath = objectIdPath(objectPath);
        if ("@singleton".equals(objectIdPath)) return;
        if ("Repository".equals(label) && argument.getType() instanceof SppClass type
                && ParticipantRoleBindings.isStorageIdentityPath(this,type,objectIdPath)) return;
        if ("Repository".equals(label) && "@storageIdentity".equals(objectIdPath)
                && hasOpaqueAppPrimaryKey(argument.getType().getName())) return;
        if (objectIdPath.isBlank()) {
            errors.add(label + " " + objectPath + " must include an object id field path.");
            return;
        }
        SppCompilationUnit objectType = mapsOfClass.get(objectType(objectPath));
        if (!(objectType instanceof SppClass)) {
            errors.add(label + " object type " + objectType(objectPath) + " not defined.");
            return;
        }
        SppField objectIdField = resolveFieldPath(objectType, objectIdPath);
        if (objectIdField == null) {
            if (isAppDdlPlatformObjectPath(objectPath)) {
                return;
            }
            if (isRoleTakeIdentityObjectPath(objectPath)) {
                return;
            }
            errors.add(label + " object id path " + objectPath + " not found.");
            return;
        }
        String arrayFieldPath = firstArrayFieldPath(objectType, objectIdPath);
        if (arrayFieldPath != null) {
            errors.add(label + " object id path " + objectPath
                    + " must not traverse array field " + arrayFieldPath + ".");
            return;
        }
        if (!isObjectIdField(objectIdField)) {
            errors.add(label + " object id path " + objectPath
                    + " must resolve to a scalar field.");
        }
    }

    private boolean hasOpaqueAppPrimaryKey(String type) {
        var table = appTableByBusinessType().get(type);
        return table != null && table.getAppColumns().stream()
                .anyMatch(column -> column.isPrimaryKey() && !column.isObjectBacked());
    }

    private boolean argumentObjectRuleMatchesParameter(String label, SppLocalVar argument, String objectType) {
        if (argument == null || argument.getType() == null || objectType == null) {
            return false;
        }
        String argumentType = argument.getType().getName();
        return objectType.equals(argumentType)
                || ("Repository".equals(label) && rolePerformerNames(argumentType).contains(objectType));
    }

    private void validateArgumentMemberObjectRule(String label, SppService service, int argumentIndex,
                                                  String snapshotObjectType, String objectIdPath,
                                                  List<String> errors) {
        if (argumentIndex < 0 || argumentIndex >= service.getServiceBody().getSppLocalVarList().size()) {
            errors.add(label + " argument index " + argumentIndex
                    + " is outside service " + service.getFuncName() + " parameter list.");
            return;
        }
        SppLocalVar argument = service.getServiceBody().getLocalVar(argumentIndex);
        if (argument.getType() == null) {
            errors.add(label + " argument(" + argumentIndex + ", "
                    + snapshotObjectType + "." + objectIdPath + ") object type must match service parameter type "
                    + "<unknown>.");
            return;
        }
        if (argument.getArrayDimension() != 0) {
            errors.add(label + " argument(" + argumentIndex + ", "
                    + snapshotObjectType + "." + objectIdPath
                    + ") must refer to a single object parameter, but service parameter type is "
                    + argument.getType().getName() + argument.getArrayString() + ".");
            return;
        }
        if (objectIdPath == null || objectIdPath.isBlank()) {
            errors.add(label + " " + snapshotObjectType
                    + " must include an object id field path.");
            return;
        }
        SppCompilationUnit snapshotType = mapsOfClass.get(snapshotObjectType);
        if (!(snapshotType instanceof SppClass)) {
            errors.add(label + " object type " + snapshotObjectType + " not defined.");
            return;
        }
        SppField objectIdField = resolveFieldPath(argument.getType(), objectIdPath);
        String argumentObjectPath = argument.getType().getName() + "." + objectIdPath;
        if (objectIdField == null) {
            errors.add(label + " object id path " + argumentObjectPath + " not found.");
            return;
        }
        String arrayFieldPath = firstArrayFieldPath(argument.getType(), objectIdPath);
        if (arrayFieldPath != null) {
            errors.add(label + " object id path " + argumentObjectPath
                    + " must not traverse array field " + arrayFieldPath + ".");
            return;
        }
        if (!isObjectIdField(objectIdField)) {
            errors.add(label + " object id path " + argumentObjectPath
                    + " must resolve to a scalar field.");
        }
    }

    private void validateCurrentObjectRule(String label, String objectPath, List<String> errors) {
        if (objectIdPath(objectPath).isBlank()) {
            errors.add(label + " " + objectPath + " must include an object id field path.");
            return;
        }
        SppCompilationUnit objectType = mapsOfClass.get(objectType(objectPath));
        if (!(objectType instanceof SppClass)) {
            errors.add(label + " object type " + objectType(objectPath) + " not defined.");
            return;
        }
        SppField objectIdField = resolveFieldPath(objectType, objectIdPath(objectPath));
        if (objectIdField == null) {
            if (isAppDdlPlatformObjectPath(objectPath)) {
                return;
            }
            if (isRoleTakeIdentityObjectPath(objectPath)) {
                return;
            }
            errors.add(label + " object id path " + objectPath + " not found.");
            return;
        }
        String arrayFieldPath = firstArrayFieldPath(objectType, objectIdPath(objectPath));
        if (arrayFieldPath != null) {
            errors.add(label + " object id path " + objectPath
                    + " must not traverse array field " + arrayFieldPath + ".");
            return;
        }
        if (!isObjectIdField(objectIdField)) {
            errors.add(label + " object id path " + objectPath
                    + " must resolve to a scalar field.");
        }
    }

    private boolean hasValidObjectIdPath(String objectPath) {
        String objectIdPath = objectIdPath(objectPath);
        if (objectIdPath.isBlank()) {
            return false;
        }
        if (isAppDdlPlatformObjectPath(objectPath)) {
            return true;
        }
        if (isRoleTakeIdentityObjectPath(objectPath)) {
            return true;
        }
        SppCompilationUnit objectType = mapsOfClass.get(objectType(objectPath));
        SppField objectIdField = resolveFieldPath(objectType, objectIdPath);
        return objectIdField != null && firstArrayFieldPath(objectType, objectIdPath) == null
                && isObjectIdField(objectIdField);
    }

    private boolean isAppDdlPlatformObjectPath(String objectPath) {
        String objectType = objectType(objectPath);
        String objectIdPath = objectIdPath(objectPath);
        if (objectType == null || objectIdPath == null || objectIdPath.isBlank() || objectIdPath.contains(".")) {
            return false;
        }
        AppTable appTable = appTableByBusinessType().get(objectType);
        if (appTable == null) {
            return false;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (objectIdPath.equals(column.getName()) && !column.isObjectBacked()) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoleTakeIdentityObjectPath(String objectPath) {
        String roleType = objectType(objectPath);
        String objectIdPath = objectIdPath(objectPath);
        if (roleType == null || objectIdPath == null || objectIdPath.isBlank()) {
            return false;
        }
        Map<String, AppTable> tableByBusinessType = appTableByBusinessType();
        AppTable roleTable = tableByBusinessType.get(roleType);
        for (SppCompilationUnit unit : mapsOfClass.values()) {
            if (!(unit instanceof SppRoleMapper mapper)
                    || mapper.getRole() == null
                    || !roleType.equals(mapper.getRole().getName())) {
                continue;
            }
            for (SppClass performer : roleMapperPerformers(mapper)) {
                if (performer == null || performer.getName() == null) {
                    continue;
                }
                String performerIdPath = roleTakePerformerIdPath(objectIdPath, performer);
                if (performerIdPath == null) {
                    continue;
                }
                if (roleParticipantPlatformColumn(roleTable, performer, performerIdPath) != null
                        && roleTakePerformerIdentityPaths(performer, tableByBusinessType).contains(performerIdPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String roleTakePerformerIdPath(String objectIdPath, SppClass performer) {
        if (objectIdPath == null || objectIdPath.isBlank() || performer == null || performer.getName() == null) {
            return null;
        }
        if (!objectIdPath.contains(".")) {
            return objectIdPath;
        }
        int dot = objectIdPath.indexOf('.');
        String performerRuntimeName = objectIdPath.substring(0, dot);
        String performerIdPath = objectIdPath.substring(dot + 1);
        if (performerRuntimeName.isBlank() || performerIdPath.isBlank()) {
            return null;
        }
        String expectedRuntimeName = NameUtil.firstToLowerCase(performer.getName(), true);
        return performerRuntimeName.equals(expectedRuntimeName) ? performerIdPath : null;
    }

    private List<String> roleTakePerformerIdentityPaths(SppClass performer, Map<String, AppTable> tableByBusinessType) {
        if (performer == null || performer.getName() == null) {
            return List.of();
        }
        AppTable performerTable = tableByBusinessType.get(performer.getName());
        List<String> paths = new ArrayList<>(
                appTableObjectBackedPrimaryKeyPaths(performerTable, tableByBusinessType, new HashSet<>()));
        paths.addAll(appTablePlatformPrimaryKeyPaths(performerTable));
        if (!paths.isEmpty()) {
            return paths;
        }
        List<String> candidateNames = List.of(
                NameUtil.firstToLowerCase(performer.getName(), true) + "Id",
                "id");
        for (String candidateName : candidateNames) {
            if (performer.getField(candidateName) != null) {
                return List.of(candidateName);
            }
        }
        return performer.getSppFieldList().isEmpty()
                ? List.of()
                : List.of(performer.getSppFieldList().get(0).getName());
    }

    private List<String> appTablePlatformPrimaryKeyPaths(AppTable appTable) {
        if (appTable == null) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (AppColumn column : appTable.getAppColumns()) {
            if (column.isPrimaryKey() && !column.isObjectBacked()
                    && column.getName() != null && !column.getName().isBlank()) {
                paths.add(column.getName());
            }
        }
        return paths;
    }

    private boolean isObjectIdField(SppField field) {
        return field != null && field.getArrayDimension() == 0 && isScalarDbField(field);
    }

    private String firstArrayFieldPath(SppCompilationUnit objectType, String fieldPath) {
        if (!(objectType instanceof SppClass currentClass) || fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        String[] parts = fieldPath.split("\\.");
        StringBuilder currentPath = new StringBuilder(currentClass.getName());
        for (int i = 0; i < parts.length; i++) {
            SppField field = currentClass.getField(parts[i]);
            if (field == null) {
                return null;
            }
            currentPath.append(".").append(field.getName());
            if (field.getArrayDimension() != 0) {
                return currentPath.toString();
            }
            if (i < parts.length - 1) {
                if (!(field.getType() instanceof SppClass nestedClass)) {
                    return null;
                }
                currentClass = nestedClass;
            }
        }
        return null;
    }

    private String objectType(String objectPath) {
        int index = objectPath.indexOf('.');
        return index < 0 ? objectPath : objectPath.substring(0, index);
    }

    private String objectIdPath(String objectPath) {
        int index = objectPath.indexOf('.');
        return index < 0 ? "" : objectPath.substring(index + 1);
    }

    private String validateQuantumAttribute(String attributeId) {
        String[] parts = attributeId.split("\\.");
        if (parts.length != 2) {
            return "expected Class.field";
        }
        SppCompilationUnit unit = mapsOfClass.get(parts[0]);
        if (!(unit instanceof SppClass sppClass) || !unit.isReal()) {
            return "class not found";
        }
        SppField field = sppClass.getField(parts[1]);
        if (field == null) {
            return "field not found";
        }
        if (!field.isQuantum()) {
            return "field is not quantum";
        }
        return null;
    }

    private void checkUnknowServiceCall(ISppErrorLogger listener) {
        for(Object[] parameterChecker : unknownServiceCall){
            ParserRuleContext ctx = (ParserRuleContext) parameterChecker[0];
            SppClass currentCLass = (SppClass) parameterChecker[1];
            SppLocalVar localVar = (SppLocalVar) parameterChecker[2];
            SppService callee = (SppService) parameterChecker[3];
            // Collection overloads are resolved per call and validated by bindParticipantCalls.
            if (callee.isImplicitParticipantCrud()) continue;
            int paramIndex = (int) parameterChecker[4];
            if(callee.getServiceBody().getSppLocalVarList().size() <= paramIndex){
                listener.logSppError(ctx,"Scenario " + currentCLass.getName() + ": Parameter " +
                        paramIndex + " not exist in service " + callee.getName());
            }else {
                SppLocalVar indexVar = callee.getServiceBody().getLocalVar(paramIndex);
                if (!isSameTypeWithParameter(indexVar, localVar))
                    listener.logSppError(ctx, "Parameter " + name + " is  excepted " + indexVar.getType().getName() + (indexVar.getArrayString()));
            }
        }
    }

    public boolean isSameTypeWithParameter(SppLocalVar indexVar, SppLocalVar nameVar) {
        boolean isSame = indexVar.getType().getName().equals(nameVar.getType().getName());
        isSame = isSame && indexVar.getArrayDimension() == nameVar.getArrayDimension();
        return isSame;
    }

    private void checkUnfoundClass(ISppErrorLogger listener) {
        ArrayList<ErrorContent> ret = new ArrayList<>();
        for(ErrorContent cont: unFoundClass){
            SppCompilationUnit sppClass = mapsOfClass.get(cont.getName());
            if(sppClass == null || !sppClass.isReal()){
                ret.add(cont);
            }
        }
        for(ErrorContent cont: ret){
            listener.logSppError(cont.getLine(), cont.getCol(), cont.getMsg());
        }
    }

    public static SppClass genObjectDecl(IConstance.CompilationUnitType type, String objName) {
        if(type == IConstance.CompilationUnitType.atomicservice)
            return new SppService(objName);
        if( type == IConstance.CompilationUnitType.scenario)
            return new SppScenario(objName);
        if(type == IConstance.CompilationUnitType.rolemapper)
            return new SppRoleMapper(objName);
        return new SppClass(objName);
    }

    public record RoleStoragePerformer(String slot, SppClass type, String member) { }
    public List<RoleStoragePerformer> roleStoragePerformers(SppClass role) {
        var types = new java.util.LinkedHashMap<String,SppClass>();
        for (var field : role.getSppFieldList()) if (field.getArrayDimension() == 0 && field.getType() instanceof SppClass type
                && type.getType() != null && Set.of(IConstance.CompilationUnitType.entity,IConstance.CompilationUnitType.role,IConstance.CompilationUnitType.reference).contains(type.getType())) types.put(type.getName(),type);
        for (var unit : mapsOfClass.values()) if (unit instanceof SppRoleMapper mapper
                && mapper.getRole() != null && role.getName().equals(mapper.getRole().getName()) && mapper.getEntities() != null) for (var type : mapper.getEntities()) types.putIfAbsent(type.getName(),type);
        types.replaceAll((name,type) -> mapsOfClass.get(name) instanceof SppClass canonical ? canonical : type);
        List<RoleStoragePerformer> result = new ArrayList<>();
        for (var type : types.values()) {
            var members = role.getSppFieldList().stream().filter(field -> field.getArrayDimension() == 0 && field.getType().getName().equals(type.getName())).toList();
            if (members.isEmpty()) result.add(new RoleStoragePerformer(type.getName(),type,""));
            else for (var member : members) result.add(new RoleStoragePerformer(members.size() == 1 ? type.getName() : type.getName() + "_" + member.getName(),type,member.getName()));
        }
        return result;
    }

    /** Each take is an alternative; intrinsic members join every alternative. */
    public List<String> prepareRoleCompositions() {
        List<String> errors = new ArrayList<>();
        for (SppCompilationUnit unit : new ArrayList<>(mapsOfClass.values())) {
            if (!(unit instanceof SppClass role) || !role.isReal()
                    || role.getType() != IConstance.CompilationUnitType.role) continue;
            var members = role.getSppFieldList().stream().filter(field -> field.getArrayDimension() == 0
                    && field.getType() instanceof SppClass && field.getType().getType() != null && java.util.Set.of(IConstance.CompilationUnitType.role,
                        IConstance.CompilationUnitType.entity, IConstance.CompilationUnitType.reference).contains(field.getType().getType())).toList();
            var mappers = mapsOfClass.values().stream().filter(candidate -> candidate instanceof SppRoleMapper mapper
                    && mapper.getRole() == role).map(candidate -> (SppRoleMapper)candidate)
                    .sorted(java.util.Comparator.comparing(SppRoleMapper::getName)).toList();
            var uniqueMembers = members.stream().filter(member -> members.stream()
                    .filter(other -> other.getType().getName().equals(member.getType().getName())).count() == 1).toList();
            if (mappers.isEmpty() && !uniqueMembers.isEmpty()) {
                SppRoleMapper mapper = new SppRoleMapper("MapperIntrinsicTo" + role.getName());
                mapper.setRole(role); mapper.setReal(true);
                mapsOfClass.put(mapper.getName(), mapper);
                mappers = List.of(mapper);
            }
            var alternatives = mappers.stream().map(mapper -> {
                var actors = new java.util.LinkedHashMap<String,SppClass>();
                if (mapper.getEntities() != null) for (var actor : mapper.getEntities()) actors.put(actor.getName(),actor);
                for (var member : uniqueMembers) {
                    String name = member.getType().getName();
                    actors.put(name,mapsOfClass.get(name) instanceof SppClass canonical ? canonical : (SppClass)member.getType());
                }
                return List.copyOf(actors.values());
            }).toList();
            for (int index = 0; index < mappers.size(); index++) {
                var mapper = mappers.get(index);
                mapper.setEntities(alternatives.get(index));
                mapper.setAlternatives(alternatives);
                errors.addAll(mapper.prepareMemberBindings());
            }
        }
        return errors;
    }

    public void dealEntityToRoleMaps() {
        prepareRoleCompositions();
        Map<String, SppCompilationUnit> sppClassMap = getMapsOfClass();
        for (SppCompilationUnit sppClass : sppClassMap.values()) {
            if (sppClass.getType() == IConstance.CompilationUnitType.rolemapper) {
//                System.out.println(sppClass);
                Map<String, String> entityToRole = new HashMap<>();
                SppRoleMapper sppRoleMapper = (SppRoleMapper) sppClass;
                for (SppLocalVar var : sppRoleMapper.getSppFieldList()) {
                    SppRoleField roleField = (SppRoleField) var;
                    entityToRole.put(roleField.getEntityName(), roleField.getName());
                }
                takeAll(sppRoleMapper, entityToRole);
            }
        }
    }

    public void generateDefaultServices(ILogable logger){
        Map<String, SppCompilationUnit> sppClassMap = getMapsOfClass();
        for (SppCompilationUnit sppCompilationUnit : sppClassMap.values().toArray(new SppCompilationUnit[sppClassMap.size()])) {
            if (sppCompilationUnit.getType() == IConstance.CompilationUnitType.entity
                || sppCompilationUnit.getType() == IConstance.CompilationUnitType.contract) {
                SppClass sppClass = (SppClass) sppCompilationUnit;
                SppDefaultService sppDefaultService = new SppDefaultService();
                sppDefaultService.setRealm(sppClass);
                //Create
                SppService sppService = genDefaultService(logger, sppClass, sppClassMap,"Create", IConstance.ServiceType.update);
                sppDefaultService.addService(sppService);

                //Update
                sppService = genDefaultService(logger, sppClass, sppClassMap,"Update", IConstance.ServiceType.update);
                sppDefaultService.addService(sppService);

                //Get
                sppService =genDefaultService(logger, sppClass, sppClassMap,"Get", IConstance.ServiceType.query);
                sppDefaultService.addService(sppService);
                sppService.getReturns().addLocalVar(new SppLocalVar(sppClass,NameUtil.firstToLowerCase(sppClass.getName(),true)));

                //Search
                sppService =genDefaultService(logger, sppClass, sppClassMap,"Search", IConstance.ServiceType.query);
                sppDefaultService.addService(sppService);
                SppLocalVar sppLocalVar = new SppLocalVar(sppClass, NameUtil.firstToLowerCase(sppClass.getName(), true));
                sppLocalVar.setArrayDimension(1);
                sppService.getReturns().addLocalVar(sppLocalVar);

                //Quantum
                for(SppField sppField : sppClass.getSppFieldList()){
                    if(sppField.isQuantum()){
                        sppService = genDefaultService(logger, sppClass, sppClassMap,"Increase", IConstance.ServiceType.update);
                        sppService.setQuantumService(true);
                        sppDefaultService.addService(sppService);
                        SppField field = new SppField(sppField.getType(), sppField.getName());
                        sppService.getServiceBody().addLocalVar(field);
                        field = new SppField(sppField.getType(), sppField.getName()+"Max");
                        sppService.getServiceBody().addLocalVar(field);

                        sppService = genDefaultService(logger, sppClass, sppClassMap,"Decrease", IConstance.ServiceType.update);
                        sppService.setQuantumService(true);
                        sppDefaultService.addService(sppService);
                        field = new SppField(sppField.getType(), sppField.getName());
                        sppService.getServiceBody().addLocalVar(field);
                        field = new SppField(sppField.getType(), sppField.getName()+"Min");
                        sppService.getServiceBody().addLocalVar(field);
                    }
                }
                sppDefaultServices.add(sppDefaultService);
            }
        }
    }

    private SppService genDefaultService(ILogable logger, SppCompilationUnit sppClass, Map<String, SppCompilationUnit> sppClassMap,
                                          String prefix, IConstance.ServiceType serviceType) {
        String serviceName = prefix + sppClass.getName();
        SppCompilationUnit sppCompilationUnit = sppClassMap.get(serviceName);
        if(sppCompilationUnit == null) {
            sppCompilationUnit = new SppService(serviceName);
            addClass(sppCompilationUnit);
        }
        if(sppCompilationUnit instanceof SppService){
            sppCompilationUnit.setReal(true);
            SppService sppService = (SppService) sppCompilationUnit;
            sppService.setFuncName(NameUtil.firstToLowerCase(prefix,true) + sppClass.getName());
            sppService.setType(IConstance.CompilationUnitType.atomicservice);
            ScopeItem scopeItem = new ScopeItem();
            scopeItem.setImplicitDefaultBinding(true);
            ScopeDefine scopeDefine = new ScopeDefine();
            scopeDefine.setLocal(true);
            scopeItem.setScopeDefine(scopeDefine);
            scopeItem.setService(sppService);
            sppService.setServiceType(serviceType);
            sppService.addLocalVar(new SppLocalVar(sppClass,NameUtil.firstToLowerCase(sppClass.getName(),true)));
            return sppService;
        }else{
            logger.getLogger().error("The unit {} conflict with default service of entity {}.", serviceName, sppClass.getName());
        }
        return null;
    }

    public void dealAnnotations(List<? extends  IComponent> annotations){
        for(IComponent component:annotations){
            AppAnnotation annotation = (AppAnnotation) component;
            for(SppCompilationUnit unit : annotation.getUnits()){
                for(AnnotationDefine annotationDefine:annotation.getAnnotationDefineList()) {
                    unit.getAnnotations().add(annotationDefine);
                }
            }
            dealAnnotations(annotation.getChildren());
        }
    }

    public static void takeAll(SppRoleMapper sppRoleMapper, Map<String, String> entityToRole) {
        if (sppRoleMapper.isTakeAll()) {
            for(SppClass entity : sppRoleMapper.getEntities()) {
                SppClass role = sppRoleMapper.getRole();
                for (SppLocalVar var : entity.getSppFieldList()) {
                    if (entityToRole.get(var.getName()) == null && role.getField(var.getName()) == null) {
                        try {
                            SppField inherited = (SppField) var.clone();
                            inherited.setInheritedThroughTakeAll(true);
                            role.addField(inherited);
                        }
                        catch (CloneNotSupportedException e) { throw new IllegalStateException(e); }
                    }
                }
            }
        }
    }
}
