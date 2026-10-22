grammar JavaExpr;

import Base;

expression
    :   primaryExpr # primExpr
    |   '(' expression ')' # parenthesesExpr
    |   expression '[' expression ']' # arrayExpr
    |   expression ('++' | '--') # rightIncDecExpr
    |   ('+'|'-'|'++'|'--') expression # leftIncDecAndSignExpr
    |   ('~'|'!') expression # bitAndLogicNotExpr
    |   expression ('*'|'/'|'%') expression # devMultModeExpr
    |   expression ('+'|'-') expression # plusMinusExpr
    |   expression ('<' '<' | '>' '>' '>' | '>' '>') expression # shiftExpr
    |   expression ('<=' | '>=' | '>' | '<') expression # logicGreatLessExpr
    |   expression ('==' | '!=') expression # logicEqualsExpr
    |   expression '&' expression # bitAndExpr
    |   expression '^' expression # bitXorExpr
    |   expression '|' expression # bitOrExpr
    |   expression '&&' expression # logicAndExpr
    |   expression '||' expression # logicOrExpr
    |   expression '?' expression ':' expression # questionExpr
    |   <assoc=right> expression
        (   '='
        |   '+='
        |   '-='
        |   '*='
        |   '/='
        |   '&='
        |   '|='
        |   '^='
        |   '>>='
        |   '>>>='
        |   '<<='
        |   '%='
        )
        expression # assignExpr
    ;

primaryExpr
    :   primaryExprLiteral
    |   primaryExprQualified
    ;

primaryExprQualified
    : qualifiedName
    ;


primaryExprLiteral
    : literal
    ;

literal
    :   IntegerLiteral
    |   FloatingPointLiteral
    |   CharacterLiteral
    |   StringLiteral
    |   BooleanLiteral
    |   NullLiteral
    ;
