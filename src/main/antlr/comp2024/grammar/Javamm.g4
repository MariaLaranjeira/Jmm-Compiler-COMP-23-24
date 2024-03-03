grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

INTEGER : [0] | ([1-9][0-9]*);
ID : [a-zA-Z_$]([a-zA-Z0-9_$])* ;

ENDOFLINE_COMMENT : '//' .*? '\n' -> skip ;
MULTILINE_COMMENT : '/*' .*? '*/' -> skip ;
WS : [ \t\n\r\f]+ -> skip ;

program
    : stmt + EOF
    | (importDecl)* classDecl EOF
    ;

importDecl
    : 'import' value+=ID ('.' value+=ID)* ';' #ImportStmt
    ;

classDecl
    : 'class' name=ID ('extends' extend=ID)? '{' (varDecl)* (methodDecl)* '}' #ClassStmt
    ;

type
    : type '[' ']' #ArrayType
    | value = 'boolean' #BooleanType
    | value = 'int' #IntegerType
    | value = 'float' #FloatType
    | value = 'double' #DoubleType
    | value = 'String' #StringType
    | value = ID #IdType
    ;

varDecl
    : type name=ID ';' #VarStmt
    ;

param
    : type name=ID
    ;

params
    : param (',' param)* (',' varargsParam)?
    | varargsParam
    | //empty
    ;

varargsParam
    : type '...' name=ID
    ;

methodDecl
    : ('public')? type name=ID '(' params ')' '{' (varDecl)* (stmt)* 'return' expr ';' '}' #MethodStmt
    | ('public')? 'static' 'void' 'main' '(' 'String' '['']' ID ')' '{' (varDecl)* (stmt)* '}' #MethodStmt
    ;


stmt
    : '{' ( stmt )* '}' #BracketsStmt
    | 'if' '(' expr ')' stmt ('else if' '(' expr ')' stmt)* ('else' stmt)?  #IfStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    | expr ';' #ExprStmt
    | var = ID '=' expr ';' #AssignStmt
    | var = ID '[' expr ']' '=' expr ';' #ArrayAssignStmt
    ;

expr
    : value = '!' expr #NotExpr
    | value = INTEGER #IntegerLiteral
    | value = ('true' | 'false') #BooleanLiteral
    | value = 'this' #ThisExpr
    | name = ID #VarRefExpr
    | '(' expr ')' #ParenExpr
    | 'new' 'int' '[' expr ']' #NewArray
    | 'new' ID '(' (expr (',' expr) *)? ')' #NewObject
    | '[' ( expr ( ',' expr )* )? ']' #ArrayInitializer
    | expr '[' expr ']' #ArrayAccess
    | expr '.' 'length' #Length
    | expr '.' value=ID '(' (expr (',' expr)*)? ')' #FunctionCall
    | expr op = ('*' | '/') expr #BinaryOp
    | expr op = ('+' | '-') expr #BinaryOp
    | expr op = ('<' | '>' | '==') expr #BinaryOp
    | expr op=('!=' | '+=' | '<=' | '>=' | '-=' | '*=' | '/=') expr #BinaryOp
    | expr op = ('&&' | '||') expr #BinaryOp
    ;



