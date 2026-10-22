grammar App;
import JavaExpr;

program
    : serviceDomain ruleBlock* EOF
    ;
serviceDomain
    : 'domain' domainName ';'
    ;

domainName
    : Identifier
    ;

ruleBlock
    : annotateDefine* annotatableRule
    | annotate
    ;

annotatableRule
    : header
    | primary
    | scope
    | phy
    | quantumStore
    | map
    | services
    | tables
    | examples
    | web
    | applicationDeployment
    | localServiceBinding
    | internationalization
    | requestPolicy
    | transactionPolicy
    | propertyTrackingPolicy
    | systemServices
    | technicalServices
    | serviceInterception
    ;

technicalServices
    : {getCurrentToken().getText().equals("technical")}? Identifier 'services' '{' (Identifier ';')+ '}'
    ;

systemServices
    : 'system' 'services' '{' systemServiceSwitch+ '}'
    ;

systemServiceSwitch
    : (Identifier | 'idempotency') ':' BooleanLiteral ';'
    ;

serviceInterception
    : {getCurrentToken().getText().equals("intercept")}? Identifier interceptionTargets '{' interceptionHook+ '}'
    ;

interceptionTargets : 'services' | Identifier (',' Identifier)*;
interceptionHook : interceptionPhase ':' Identifier (',' Identifier)* ';';
interceptionPhase : 'before' | 'after' | Identifier;

phyTechnicalService
    : {getCurrentToken().getText().equals("technical")}? Identifier
      {getCurrentToken().getText().equals("service")}? Identifier Identifier
      '{' phyTechnicalImplementation phyConfig* '}'
    ;
phyTechnicalImplementation
    : {getCurrentToken().getText().equals("implementation")}? Identifier ':' StringLiteral ';'
    ;

transactionPolicy
    : 'transactions' '{' ('default' ('at' | 'saga') 'resource' Identifier ';'
      | 'mode' ':' transactionMode ';' 'resource' ':' Identifier ';') ordinaryUpdateScheduling? '}'
    | 'transactions' '{' ordinaryUpdateScheduling '}'
    ;

propertyTrackingPolicy
    : {getCurrentToken().getText().equals("propertyTracking")}? Identifier ':' BooleanLiteral ';'
    ;

ordinaryUpdateScheduling : 'ordinaryUpdates' ':' Identifier ';';

requestPolicy
    : 'requests' '{' ('idempotency' 'retention' requestDuration ';' | 'duplicateWindow' requestDuration ';')* '}'
    ;

requestDuration : StringLiteral | IntegerLiteral Identifier;

internationalization
    : 'i18n' '{' 'defaultLocale' StringLiteral ';' 'supportedLocales' StringLiteral (',' StringLiteral)* ';' '}'
    ;

applicationDeployment
    : 'application' Identifier '{' (deployDomain | deploymentEnvironment)+ '}'
    ;

localServiceBinding
    : 'bind' Identifier 'to' qualifiedName (';' | '{' localParticipantBinding+ '}')
    ;

localParticipantBinding
    : 'participant' IntegerLiteral 'facts' '(' Identifier (',' Identifier)* ')' ('identity' '(' qualifiedName (',' qualifiedName)* ')')? 'existing'? ';'
    ;

web
    : 'web' '{' webEntry* '}'
    ;
webEntry : Identifier Identifier ';';

examples
    : 'examples' '{' exampleRun* '}'
    ;
exampleRun
    : exampleAction Identifier exampleFault? exampleBarrier? exampleRestart? exampleJdbcFault? ';'
    ;

exampleJdbcFault
    : jdbcFaultAction jdbcFaultPoint Identifier
    ;
jdbcFaultAction : Identifier;
jdbcFaultPoint : Identifier;

exampleRestart
    : restartAction Identifier 'using' (qualifiedName | StringLiteral)
    ;
restartAction
    : Identifier
    ;

exampleBarrier
    : barrierAction 'before' Identifier IntegerLiteral
    ;
barrierAction
    : Identifier
    ;

exampleFault
    : {!"jdbcFault".equals(_input.LT(1).getText())}? faultAction faultTiming Identifier
      ('after' Identifier)? faultRepetition?
    ;
faultRepetition : Identifier IntegerLiteral;
faultAction
    : Identifier
    ;
faultTiming
    : 'before'
    | 'after'
    | Identifier
    ;

exampleAction
    : Identifier
    ;

tables
    : 'tables' Identifier '{' annotateTableDefine* '}'
    ;

annotateTableDefine
    : annotateDefine* tableDefine
    ;

tableDefine
    : 'table' Identifier  '(' identifyList ')' '{' (columnDefine | keyDefine | autoNumberDefine | roleStorageDefine | roleIdentityDefine | performerStorageDefine)* '}'
    ;

roleIdentityDefine : 'identity' 'independent' ';';

performerStorageDefine : 'performers' ('inline' | 'in' Identifier) ';';

roleStorageDefine : ('current' | 'snapshot') '(' qualifiedName (',' qualifiedName)* ')' ';';

autoNumberDefine : 'auto' qualifiedName 'prefix' StringLiteral ';';

keyDefine
    : primaryKeyDefine
    | foreignKeyDefine
    | indexDefine
    ;

foreignKeyDefine
    : 'foreignkey' '(' qualifiedItemList ')' foreignKeyReferences? ';'
    ;

foreignKeyReferences
    :'references' Identifier
    ;

primaryKeyDefine
    : 'primarykey' '(' qualifiedItemList ')' ';'
    ;

indexDefine
    : unique='unique'? 'index' Identifier? '(' qualifiedItemList ')' ';'
    ;

qualifiedItemList
    : qualifiedItem (',' qualifiedItem)*
    ;

qualifiedItem
    : qualifiedName
    ;

identifyList
    : identifyItem (',' identifyItem )*
    ;

identifyItem
    : Identifier
    ;

columnDefine
    : columnModifier+ columnType columnName ';'
    ;

columnName
    : Identifier
    ;

columnModifier
    : 'primarykey' #columnPrimaryKey
    | NullLiteral #columnNullalbe
    | 'notnull' #columnNotnull
    ;

columnType
    : columnTypePrimitiveType  columnTypePrecisionScale?
    ;

columnTypePrecisionScale
    : '(' columnTypePrecision (',' columnTypeScale )?')'
    ;

columnTypePrecision
    : IntegerLiteral
    ;

columnTypeScale
    : IntegerLiteral
    ;

columnTypePrimitiveType
    : primitiveType
    ;

annotate
    : annotateDefine+ '{' annotateBody '}'
    ;

annotateDefine
    : '@' Identifier annotateParameterDefine?
    ;

annotateParameterDefine
    : '(' (annotateParameter (',' annotateParameter)*)? ')'
    ;

annotateParameter
    : Identifier '=' StringLiteral
    ;

annotateBody
    : (annotateBodyIdentifier ';')* annotate*
    ;

annotateBodyIdentifier
    : Identifier
    ;
header
    : 'header' Identifier ('extends' extendsHeaders)? '{' (headerFieldDefine ';')* '}'
    ;

extendsHeaders
    : Identifier (',' Identifier)*
    ;
primary
    : 'primary' '{' (primaryQualified ';')* '}'
    ;

primaryQualified
    : qualifiedName
    ;
scope
    : 'scope' '{' scopeDefine '}'
    ;

map
    : 'map' Identifier mapParameters? '{' mapItem* '}'
    ;

mapParameters
    : '(' mapParameterIdentifier (',' mapParameterIdentifier)* ')'
    ;

mapParameterIdentifier
    : Identifier
    ;
mapItem
    : Identifier '=' sqlType Identifier? ';'
    ;

quantumStore
    : 'quantumStore' Identifier '{' quantumStoreItem* '}'
    ;

quantumStoreItem
    : 'attribute' qualifiedName ';'
    ;

phy
    : 'phy' '{' phyItem* '}'
    ;

phyItem
    : phyBackend
    | phyTechnicalService
    | phyRemoteAdapter
    | phyRepositoryAdapter
    | phyQuantumStoreAdapter
    | phyGlobalConfig
    | phyNumericBinding
    | phyTranslations
    | phyNumericRules
    | phyI18nResources
    | phyEnvironment
    | phyDomainRoute
    ;

phyTranslations
    : 'constants' StringLiteral '{' (Identifier '=' phyConfigValue ';')* '}'
    ;

phyNumericBinding
    : 'numeric' numericTypeName 'as' numericTypeName ('using' qualifiedName)? ';'
    ;

phyGlobalConfig
    : 'config' phyConfigName '=' phyConfigValue ';'
    ;

phyBackend
    : 'backend' phyBackendName ('{' phyBackendItem* '}')? ';'?
    ;

phyBackendName
    : Identifier
    | StringLiteral
    ;

phyBackendItem
    : phyBackendAdapter
    | phyBackendQuantumStore
    | phyBinding
    | phyConfig
    ;

phyBackendAdapter
    : 'adapter' phyAdapterName ';'
    ;

phyBinding
    : phyBindingKind Identifier? ';'
    ;

phyBindingKind
    : 'at'
    | 'xa'
    | 'tcc'
    | 'quantum'
    | 'saga'
    | 'repository'
    ;

phyBackendQuantumStore
    : 'quantumStore' Identifier ';'
    ;

phyConfig
    : 'config' phyConfigName '=' phyConfigValue ';'
    ;

phyConfigName
    : 'default'
    | 'endpoint'
    | 'timeoutMillis'
    | Identifier
    | 'table'
    | 'quantumStore'
    | 'repository'
    ;

phyConfigValue
    : StringLiteral
    | Identifier
    | IntegerLiteral
    | FloatingPointLiteral
    | BooleanLiteral
    ;

phyRemoteAdapter
    : 'remote' Identifier '{' phyRemoteAdapterName phyRemoteConfig* '}' ';'?
    ;

phyRemoteAdapterName
    : 'adapter' phyAdapterName ';'
    ;

phyAdapterName
    : Identifier
    | StringLiteral
    ;

phyRemoteConfig
    : 'config' phyConfigName '=' phyConfigValue ';'
    ;

phyRepositoryAdapter
    : 'repository' Identifier '{' phyRepositoryAdapterName phyRepositoryConfig* '}' ';'?
    ;

phyRepositoryAdapterName
    : 'adapter' phyAdapterName ';'
    ;

phyRepositoryConfig
    : 'config' phyConfigName '=' phyConfigValue ';'
    ;

phyQuantumStoreAdapter
    : 'quantumStore' Identifier '{' phyQuantumStoreAdapterName phyQuantumStoreItem* '}' ';'?
    ;

phyQuantumStoreAdapterName
    : 'adapter' phyAdapterName ';'
    ;

phyQuantumStoreItem
    : phyQuantumStoreConfig
    | phyQuantumDefinition
    ;

phyQuantumStoreConfig
    : 'config' phyConfigName '=' phyConfigValue ';'
    ;

phyQuantumDefinition
    : 'quantum' StringLiteral '{' phyQuantumDefinitionItem* '}'
    ;

phyQuantumDefinitionItem
    : phyQuantumNumberType
    | phyQuantumMinValue
    | phyQuantumMaxValue
    | phyQuantumScale
    ;

phyQuantumNumberType
    : 'numberType' Identifier ';'
    ;

phyQuantumMinValue
    : 'minValue' StringLiteral ';'
    ;

phyQuantumMaxValue
    : 'maxValue' StringLiteral ';'
    ;

phyQuantumScale
    : 'scale' IntegerLiteral ';'
    ;

sqlType
    : sqlTypeNotnull? sqlTypePrimitiveType sqlTypeLength?
    ;

sqlTypePrimitiveType
    : primitiveType
    ;

sqlTypeNotnull
    : 'notnull'
    ;

sqlTypeLength
    : '(' precision (',' scale)?')'
    ;

precision
    : IntegerLiteral
    ;

scale
    : IntegerLiteral
    ;

headerFieldDefine
    : headerFieldModifier* headerFieldDefinePrimitiveType headerFieldDefineIdentifier defaultValue? override?
    ;

headerFieldDefinePrimitiveType
    : primitiveType
    ;

headerFieldDefineIdentifier
    : Identifier
    ;

headerFieldModifier
    : 'notnull' # HeaderFieldModifierNotNull
    | 'unique' # HeaderFieldModifierUnique
    ;

defaultValue
    : '(' 'Default' '=' defaultValueliteral ')'
    ;

defaultValueliteral
    : literal
    ;
override
    : '=' Identifier
    ;
scopeDefine
    : (localBody | remoteBody)*
    ;

localBody
    : 'local' scopeParameter? '{' scopeItem* '}'
    ;

remoteBody
    : 'remote' scopeParameter? '{' scopeItem* '}'
    ;
scopeParameter
    : '(' inParameter ',' outParameter ')'
    ;

inParameter
    : 'in' Identifier
    ;

outParameter
    : 'out' Identifier
    ;

scopeItem
    : scopeModifier* scopeItemIdentifier ';'
    | tccResourceBlock scopeItemIdentifier ';'
    ;

scopeItemIdentifier
    : Identifier
    | 'commit'
    ;

scopeModifier
    : ('async' | 'asycn') # asycnModifier
    | transactionModifier # transModifier
    | 'resource' '(' Identifier (',' qualifiedName (',' qualifiedName)*)? ')' # resourceModifier
    | 'reservation' '(' qualifiedName '->' qualifiedName ')' # tccReservationModifier
    | 'snapshot' '(' (snapshotTiming ',')? snapshotTarget (',' snapshotTarget)* ')' snapshotUsing? # snapshotModifier
    | 'repository' '(' repositoryTarget (',' repositoryTarget)* ',' qualifiedName ')' repositoryUsing? ('for' 'update')? # repositoryModifier
    | 'repository' '(' Identifier 'by' 'index' Identifier ')' repositoryUsing? # repositoryByIndexModifier
    | 'repository' '(' Identifier 'by' Identifier ')' repositoryUsing? # repositoryByForeignTypeModifier
    | 'repository' '(' Identifier ')' repositoryUsing? # repositoryByTypeModifier
    | 'compensate' '(' Identifier (',' Identifier)* ')' # compensateModifier
    | 'remoteCompensate' '(' Identifier (',' Identifier)* ')' # remoteCompensateModifier
    | 'idempotency' 'business' # businessIdempotencyModifier
    | ('idempotency' | 'duplicate') 'key' '(' keyPaths+=qualifiedName (',' keyPaths+=qualifiedName)* ')' 'facts' '(' factPaths+=qualifiedName (',' factPaths+=qualifiedName)* ')' ('window' requestDuration)? # declaredIdempotencyModifier
    | 'optional' # optionalRepositoryModifier
    | 'persist' '(' persistenceOperation ',' repositoryTarget (',' repositoryTarget)* ')' # persistenceModifier
    | 'number' '(' repositoryTarget ',' StringLiteral ')' # businessNumberModifier
    | 'compareVersion' '(' repositoryTarget (',' repositoryTarget)? ')' 'advanceVersion'? # persistenceVersionModifier
    ;

persistenceOperation
    : 'insertIfAbsent'
    | 'insert'
    | 'update'
    | 'upsert'
    | 'delete'
    ;

tccResourceBlock
    : 'tcc' 'resource' '(' Identifier ')' '{' tccResourceBlockItem* '}'
    ;

tccResourceBlockItem
    : 'reserve' qualifiedName '->' qualifiedName ';' # tccResourceBlockReserve
    | 'confirm' Identifier ';' # tccResourceBlockConfirm
    | 'cancel' Identifier ';' # tccResourceBlockCancel
    | 'snapshot' '(' (snapshotTiming ',')? snapshotTarget (',' snapshotTarget)* ')' snapshotUsing? ';'? # tccResourceBlockSnapshot
    ;

snapshotTarget
    : 'argument' '(' IntegerLiteral ',' qualifiedName ')' # argumentSnapshotTarget
    | 'current' '(' qualifiedName ')' # currentSnapshotTarget
    | 'transaction' # transactionSnapshotTarget
    | Identifier 'by' 'index' Identifier # inferredSnapshotByIndexTarget
    | Identifier 'by' Identifier # inferredSnapshotByTypeTarget
    | Identifier # inferredSnapshotTarget
    ;

snapshotTiming
    : 'before'
    | 'after'
    | 'commit'
    ;

snapshotUsing
    : 'using' Identifier
    ;

repositoryTarget
    : 'argument' '(' IntegerLiteral ',' qualifiedName ')' # argumentRepositoryTarget
    | 'platform' '(' IntegerLiteral ',' qualifiedName ')' # platformRepositoryTarget
    | 'singleton' '(' IntegerLiteral ',' Identifier ')' # singletonRepositoryTarget
    ;

repositoryUsing
    : 'using' Identifier
    ;

transactionModifier
    : 'tcc'
    | 'saga'
    | 'at'
    | 'quantum'
    | 'db'
    ;

services
    : 'services' '{' service* '}'
    ;

service
    : Identifier '{' serviceBody '}'
    ;

serviceBody
    : serviceStatement *
    ;

serviceStatement
    : requireStatement
    | expressionStatement
    ;

requireStatement
    : 'require' '(' expression ',' StringLiteral ')' ';'
    ;

expressionStatement
    : expression ';'
    ;

phyNumericRules : 'numeric' '{' phyNumericRule* '}';
phyNumericRule
    : 'replace' numericSpecification 'with' numericSpecification ('using' qualifiedName)? ';'
    | 'override' qualifiedName 'with' numericSpecification ('using' qualifiedName)? ';'
    ;
numericTypeName : numberType | qualifiedName | Identifier ('.' Identifier)* '.' numberType;
numericSpecification : numericTypeName ('(' IntegerLiteral ',' IntegerLiteral ')')?;
phyI18nResources : 'i18n' '{' 'resources' ':' StringLiteral ';' ('defaultLocale' ':' StringLiteral ';')? '}';

transactionMode : Identifier | StringLiteral | 'at' | 'saga';
deployDomain : 'deploy' Identifier ';';
deploymentEnvironment : 'environment' Identifier '{' (deployDomain | transactionPolicy | deploymentQuantum)+ '}';
deploymentQuantum : 'quantum' '{' 'system' ':' Identifier ';' 'resource' ':' Identifier ';' '}';
phyEnvironment : 'environment' Identifier '{' phyItem* '}';

phyDomainRoute : 'domainRoute' Identifier '{' 'endpoint' ':' StringLiteral ';' ('timeoutMillis' ':' IntegerLiteral ';')? '}';
