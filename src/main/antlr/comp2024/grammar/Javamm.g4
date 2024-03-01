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

varDecl
    : type name=ID ';'
    ;

param
    : type name=ID
    ;

type
    : type '[' ']'
    | type '...'
    | value = 'boolean'
    | value = 'int'
    | value = ID
    ;

methodDecl
    : ('public')? type ID '(' (type ID (',' type ID)*)? ')' '{' (varDecl)* (stmt)* 'return' expr ';' '}'
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
    : expr op = ('&&' | '<' | '+' | '-' | '*' | '/') expr #BinaryOp
    | expr '[' expr ']' #ArrayAccess
    | expr '.' 'length' #Length
    | expr '.' value=ID '(' (expr (',' expr) *)? ')' #FunctionCall
    | 'new' 'int' '[' expr ']' #NewIntArray
    | 'new' ID '(' ')' #NewObject
    | value = '!' expr #NotExpr
    | '(' expr ')' #ParenExpr
    | '[' ( expr ( ',' expr )* )? ']' #ArrayLiteral
    | value = INTEGER #IntegerLiteral
    | value = ('true' | 'false') #BooleanLiteral
    | name = ID #VarRefExpr
    | value = 'this' #ThisExpr
    ;



