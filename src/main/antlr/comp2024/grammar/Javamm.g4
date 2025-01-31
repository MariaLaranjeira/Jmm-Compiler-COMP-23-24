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
    : (importDecl)* classDecl EOF
    ;

importDecl
    : 'import' value+=ID ('.' value+=ID)* ';' #ImportStmt
    ;

classDecl
    : 'class' name=ID ('extends' extend=ID)? '{' (varDecl)* (methodDecl)* '}' #ClassStmt
    ;

returnStmt
    : 'return' expr ';'
    ;

methodDecl
    : ('public')? type name=ID '(' params ')' '{' (varDecl)* (stmt)* returnStmt'}' #RegularMethodDecl
    | ('public')? 'static' 'void' 'main' '(' 'String' '['']' args=ID ')' '{' (varDecl)* (stmt)* '}' #MainMethodDecl
    ;

type
    : type '[' ']' #ArrayType
    | value = 'boolean' #BooleanType
    | value = 'int' #IntegerType
    | value = 'float' #FloatType
    | value = 'double' #DoubleType
    | value = 'String' #StringType
    | value = 'void' #VoidType
    | value = ID #IdType
    ;

varDecl
    : type name=ID ';' #VarStmt
    ;

param
    : type name=ID
    ;


varargsParam
    : type '...' name=ID
    ;

params
    : param (',' param)* (',' varargsParam)?
    | varargsParam
    | //empty
    ;

stmt
    : '{' ( stmt )* '}' #BracketsStmt
    | ifStmt (elseStmt)? #ConditionalStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    | expr ';' #ExprStmt
    | expr '=' expr ';' #AssignStmt
    ;

ifStmt
    : 'if' '(' expr ')' stmt
    ;


elseStmt
    : 'else' stmt
    ;

expr
    : '(' expr ')' #ParenExpr
    | 'new' 'int' '[' expr ']' #NewArray
    | 'new' value=ID '(' (expr (',' expr) *)? ')' #NewObject
    | '[' ( expr ( ',' expr )* )? ']' #ArrayInitializer
    | expr '[' expr ']' #ArrayAccess
    | expr '.' value=ID '(' (expr (',' expr)*)? ')' #FunctionCall
    | expr '.' 'length' #Length
    | expr '.' expr  #GetValue
    | value = '!' expr #NotExpr
    | expr op = ('*' | '/') expr #BinaryOp
    | expr op = ('+' | '-') expr #BinaryOp
    | expr op = ('<' | '>' | '==') expr #BinaryOp
    | expr op = ('&&' | '||') expr #BinaryOp
    | value = INTEGER #IntegerLiteral
    | value = ('true' | 'false') #BooleanLiteral
    | name = 'this' #ThisExpr
    | name = ID #VarRefExpr
    ;



