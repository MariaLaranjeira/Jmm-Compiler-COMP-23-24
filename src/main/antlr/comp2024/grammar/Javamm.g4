grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_$]([a-zA-Z0-9_$])* ;

ENDOFLINE_COMMENT : '//' .*? '\n' -> skip ;
MULTILINE_COMMENT : '/*' .*? '*/' -> skip ;
WS : [ \t\n\r\f]+ -> skip ;

program
    : stmt + EOF
    | (importDecl)* classDecl EOF
    ;

importDecl
    : 'import' value+=ID ('.' value+=ID)* ';'
    ;

classDecl
    : 'class' ID ('extends' ID)? '{' (varDecl)* (methodDecl)* '}'
    ;

type
    : type '[' ']'
    | value = 'boolean'
    | value = 'int'
    | value = ID
    ;

varDecl
    : type name=ID ';'
    ;

param
    : type name=ID
    ;

params
    : param (',' param)* (',' varargsParam)?
    | varargsParam
    |
    ;

varargsParam
    : type '...' name=ID
    ;

methodDecl
    : ('public')? type name=ID '(' params ')' '{' (varDecl)* (stmt)* 'return' expr ';' '}'
    | ('public')? 'static' 'void' 'main' '(' 'String' '['']' ID ')' '{' (varDecl)* (stmt)* '}'
    ;

stmt
    : '{' ( stmt )* '}'
    | 'if' '(' expr ')' stmt 'else' stmt
    | 'while' '(' expr ')' stmt
    | expr ';'
    | var = ID '=' expr ';'
    | var = ID '[' expr ']' '=' expr ';'
    ;

expr
    : value = '!' expr #NotExpr
    | value = INTEGER #IntegerLiteral
    | value = ('true' | 'false') #BooleanLiteral
    | value = 'this' #ThisExpr
    | name = ID #VarRefExpr
    | '(' expr ')' #ParenExpr
    | 'new' 'int' '[' expr ']' #NewArray
    | 'new' ID '(' ')' #NewObject
    | '[' ( expr ( ',' expr )* )? ']' #ArrayInitializer
    | expr '[' expr ']' #ArrayAccess
    | expr '.' 'length' #Length
    | expr '.' value=ID '(' (expr (',' expr)*)? ')' #FunctionCall
    | expr op = ('*' | '/') expr #BinaryOp
    | expr op = ('+' | '-') expr #BinaryOp
    | expr op = ('<' | '>') expr #BinaryOp
    | expr op = '&&' expr #BinaryOp
    ;



