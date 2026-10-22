// MathExpr.g4
grammar MultiExpr;

multiexpr : expr (SEMICOLON expr)*;

expr
    :   stringExpr
    |   mathExpr
    |   condExpr
    |   logicExpr
    |   assignment
    |   functionCall
    ;

stringExpr
    :   stringTerm ((PLUS) stringTerm)*
    ;

mathExpr
    :   addExpr
    ;

addExpr
    :   mulExpr ((PLUS | MINUS) mulExpr)*
    ;

mulExpr
    :   atom ((MUL | DIV) atom)*
    ;

atom
    :   NUMBER
    |   STRING
    |   qualifiedName
    |   functionCall
    |   '(' expr ')'
    ;

condExpr
    :   logicExpr '?' expr ':' expr
    ;

logicExpr
    :   comparisonExpr(logicOp  comparisonExpr)*
    ;

logicOp
    : AND | OR
    ;

comparisonExpr
    : yesComparisonExpr | notComparisonExpr
    ;

notComparisonExpr
    : NOT yesComparisonExpr
    ;

yesComparisonExpr
    :   addExpr ( (EQ | NEQ | GT | LT | GTE | LTE) addExpr )?
    ;

assignment
    :   qualifiedName ASSIGN expr
    ;

functionCall
    :   qualifiedName '(' (expr (',' expr)*)? ')'
    ;

qualifiedName
    :   ID ('.' ID)*
    ;

stringTerm : STRING;

// Lexer
NUMBER : [-]?[0-9]+ ('.' [0-9]+)?;
STRING
    : '"' (ESC_SEQ | ~["\\\r\n])* '"'
    ;

fragment ESC_SEQ
    : '\\' [btnfr"\\]  // 处理常见的转义序列，如 \b, \t, \n, \f, \r, \", \\, 等
    ;
ID     : [a-zA-Z_] [a-zA-Z_0-9]*;
PLUS   : '+';
MINUS  : '-';
MUL    : '*';
DIV    : '/';
EQ     : '==';
NEQ    : '!=';
GT     : '>';
LT     : '<';
GTE    : '>=';
LTE    : '<=';
AND    : '&&';
OR     : '||';
NOT    : '!';
ASSIGN : '=';
QUESTION: '?';
COLON  : ':';
COMMA  : ',';
LPAREN : '(';
RPAREN : ')';
SEMICOLON: ';';
WS     : [ \t\r\n]+ -> skip;
LINE_COMMENT
    : '//' ~[\r\n]* '\r'? '\n' -> channel(HIDDEN)
    ;
