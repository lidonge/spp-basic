grammar Spp;

import JavaExpr;

program
    : servicedomain compilationUnits
    ;

servicedomain
    : 'domain' domainname ';'
    ;

domainname
    : Identifier
    ;

compilationUnits
    : (compilationUnit)*
    ;

compilationUnit
    : entity
    | enumeration
    | role
    | actas
    | atomicservice
    | scenario
    | constantDeclaration
    ;
constantDeclaration
    : 'const' primitiveType Identifier '=' literal ';'
    ;
enumeration
    : 'Enum' Identifier '{' enumBody '}'
    ;

enumBody
    : enumBodyIdentifier (',' enumBodyIdentifier)*
    ;

enumBodyIdentifier
    : Identifier
    | Identifier '(' StringLiteral ')'
    ;

entity
    : ('Entity' | 'Reference' ) Identifier ('from' qualifiedName)? entityBody
    ;

entityBody
    :   '{' entityBodyDeclaration* '}'
    ;

entityBodyDeclaration
    : fieldDeclaration
    ;

fieldDeclaration
    : ('business' '(' StringLiteral ')')? (type variableDeclaratorId | entitytype variableDeclaratorId?) ';'
    ;

variableDeclaratorId
    :   Identifier ('[' ']')*
    ;

type
    :   primitiveType numericPrecision? ('[' ']')*
    |   quantumNumber
    ;

quantumNumber
    :   'quantum' numberType numericPrecision?
    ;

role
    : 'Role' Identifier (roleBody | ';')
    | 'Contract' Identifier roleBody
    ;

roleBody
    : entityBody
    ;

actas
    : 'take' Identifier (',' Identifier)* 'as' Identifier (mapBody | ';')
    ;

mapBody
    : '{' mapDeclaration* '}'
    ;

mapDeclaration
    : mapfield
    | fieldDeclaration
    | takeall ';'
    | mapsame ';'
    ;

mapfield
    : (primitiveType | entitytype) Identifier '=' Identifier ';'
    ;

takeall
    : 'takeall'
    ;

mapsame
    : 'mapsame'
    ;

scenario
    :  'Scenario' autosort? serviceDefinition '{' scenarioDeclaration '}'
    ;

autosort
    : 'serial'
    | 'parallel'
    ;

atomicservice
    : (quantumService 'atomic'? | 'atomic') servicetype serviceDefinition (';' | calculationBody)
    ;

calculationBody
    : '{' calculationStatement* '}'
    ;

calculationStatement
    : calculationBody
    | calculationAssignment ';'
    | calculationLocal ';'
    | 'require' '(' expression ',' StringLiteral ')' ';'
    | 'check' '(' expression ')' ';'
    | atomiccall
    | 'if' '(' expression ')' calculationStatement ('else' calculationStatement)?
    | 'while' '(' expression ')' calculationStatement
    | 'for' '(' calculationLocal ':' expression ')' calculationStatement
    | 'for' '(' (calculationLocal | calculationUpdates)? ';' expression? ';' calculationUpdates? ')' calculationStatement
    | 'switch' '(' expression ')' '{' calculationCase* '}'
    | ('break' | 'continue') ';'
    | ';'
    ;

calculationCase
    : ('case' expression | 'default') ':' calculationStatement*
    ;

calculationLocal
    : (primitiveType | Identifier) ('[' ']')? Identifier ('=' expression)? (',' Identifier ('=' expression)?)*
    ;

calculationUpdates
    : calculationAssignment (',' calculationAssignment)*
    ;

calculationAssignment
    : qualifiedName ('=' | '+=' | '-=' | '*=' | '/=' | '%=') expression
    | qualifiedName ('++' | '--')
    | ('++' | '--') qualifiedName
    ;

primaryExpr
    : primaryExprLiteral
    | primaryExprQualified
    | calculationFunction
    | participantConstruction
    ;

participantConstruction
    : 'new' Identifier ('(' ')' | '[' ']' '{' (expression (',' expression)*)? '}')
    ;

calculationFunction
    : Identifier '(' expression (',' expression)* ')'
    ;

quantumService
    : 'quantum'
    ;

serviceDefinition
    : Identifier '(' parameterDeclarations*')'
    ;

servicetype
    : 'query'
    | 'check'
    | 'calculate'
    | 'update'
    | 'insert'
    | 'delete'
    ;

entitytype
    : Identifier  ('[' ']')*
    ;

parameterDeclarations
    : parameterDeclaration (',' parameterDeclaration)*
    ;

parameterDeclaration
    : queryKey roletype Identifier?
    | type Identifier
    | roletype Identifier
    | roletype
    ;

queryKey : {getCurrentToken().getText().equals("key")}? Identifier;

roletype
    : Identifier ('[' ']')*
    ;
scenarioDeclaration
    : executegroup
    ;
executegroup
    : (atomiccall | serialgroup | parallelgroup | scenarioIf | transactionStatements)*
    ;
serialgroup
    : 'serial' '{' (atomiccall | serialgroup | parallelgroup | scenarioIf | transactionStatements)* '}'
    ;
parallelgroup
    : 'parallel' '{' (atomiccall | serialgroup | parallelgroup | scenarioIf | transactionStatements)* '}'
    ;

scenarioIf
    : 'if' '(' expression ')' scenarioBranch ('else' (scenarioBranch | scenarioIf))?
    ;

scenarioBranch
    : '{' (atomiccall | serialgroup | parallelgroup | transactionStatements | scenarioIf)* '}'
    ;

transactionStatements
    : 'transaction' transactionPropagation? '{' (atomiccall | serialgroup | transactionStatements | scenarioIf)* '}' transactionFailure?
    ;

transactionFailure
    : {getCurrentToken().getText().equals("fail")}? Identifier ('(' qualifiedName ')')? '{' (atomiccall | serialgroup | parallelgroup | transactionStatements | scenarioIf)* '}'
    ;

transactionPropagation
    : 'REQUIRED'
    | 'REQUIRES_NEW'
    | 'NESTED'
    ;

atomiccall
    : Identifier ('(' parameters ')')? (',' 'breakTransction')? quantumBounds? (';' | scenarioBranch ';')
    | 'identify' '(' qualifiedName ')' (',' 'breakTransction')? (';' | scenarioBranch ';')
    ;

quantumBounds
    : 'bounds' '{' quantumBound+ '}'
    ;
quantumBound
    : qualifiedName ':' '[' expression ',' expression ']' ';'
    ;

parameters
    :
    | parameter (',' parameter)*
    ;

parameter
    : qualifiedName
    ;

numericPrecision : '(' IntegerLiteral ',' IntegerLiteral ')';
