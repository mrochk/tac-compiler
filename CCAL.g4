/* Maxime Rochkoulets (23101510) */

grammar CCAL ;

/* Lexer Rules ****************************************************************/

// Reserved Keywords

IF      : I F           ;
VAR     : V A R         ;
VOID    : V O I D       ;
MAIN    : M A I N       ;
ELSE    : E L S E       ;
TRUE    : T R U E       ;
SKP     : S K I P       ;
FALSE   : F A L S E     ;
WHILE   : W H I L E     ;
CONST   : C O N S T     ;
RETURN  : R E T U R N   ;
INTEGER : I N T E G E R ;
BOOL    : B O O L E A N ;

// Arithmetic Operators

PLUS  : '+' ;
MINUS : '-' ;

// Logical Operators

NOT : '~'  ;
AND : '&&' ;
OR  : '||' ;

// Comparison Operators

EQUAL     : '==' ;
DIFFERENT : '!=' ;
GREATER   : '>'  ;
GREATEREQ : '>=' ;
LESS      : '<'  ;
LESSEQ    : '<=' ;

// Numbers & Identifiers

NUM : [1-9][0-9]* ;
ID  : [_a-zA-Z] [_a-zA-Z0-9]* ; 
// Rejecting an ID when id is a keyword is done by
// giving a higher priority to keywords.

// Other Tokens

COMMA   : ',' ;
SEMICOL : ';' ;
COLON   : ':' ;
LBRA    : '{' ;
RBRA    : '}' ;
LPAREN  : '(' ;
RPAREN  : ')' ;
ASSIGN  : '=' ;
ZERO    : '0' ;

// Language is case insensitive

fragment A : [aA] ;
fragment B : [bB] ;
fragment C : [cC] ;
fragment D : [dD] ;
fragment E : [eE] ;
fragment F : [fF] ;
fragment G : [gG] ;
fragment H : [hH] ;
fragment I : [iI] ;
fragment K : [kK] ;
fragment L : [lL] ;
fragment M : [mM] ;
fragment N : [nN] ;
fragment O : [oO] ;
fragment P : [pP] ;
fragment R : [rR] ;
fragment S : [sS] ;
fragment T : [tT] ;
fragment U : [uU] ;
fragment V : [vV] ;
fragment W : [wW] ;

// Skipping Comments

COMMENT    : '/*' (COMMENT | ~[/*])* '*/' -> skip;
INLINECOMM : '//' .*? ('\n' | EOF)        -> skip;

// Skipping Whitespaces

WS : [ \t\n\r] -> skip ;

/* Parser Rules ***************************************************************/

program: decl_list func_list main EOF;

decl_list : (decl SEMICOL decl_list)* ;

decl : var_decl    
     | const_decl 
     ;

var_decl : VAR ID COLON type ;

const_decl : CONST ID COLON type ASSIGN expr ;

func_list : (func func_list)* ;

func : type ID LPAREN param_list RPAREN LBRA decl_list stmt_block RETURN LPAREN (expr?) RPAREN SEMICOL RBRA ;

type : INTEGER | BOOL | VOID ;

param_list : parameter_list* ;

parameter_list : param                      # SingleParam
               | param COMMA parameter_list # NotSingleParam 
               ;

param : ID COLON type ;

main : MAIN LBRA decl_list stmt_block RBRA ;

stmt_block : (stmt stmt_block)* ;

stmt : assignment SEMICOL   # AssignmentStmt
     | func_call  SEMICOL   # FuncCallStmt
     | SKP        SEMICOL   # SKPStmt
     | LBRA stmt_block RBRA # BracketsStmt
     | if_else              # IfElseStmt
     | loop                 # LoopStmt
     ;

assignment : ID ASSIGN expr ;

expr : LPAREN expr RPAREN       # ParenExpr
     | func_call                # FuncCallExpr
     | frag                     # FragExpr
     | expr arith_op expr       # ArithOpExpr
     | expr bin_logical_op expr # LogOpExpr
     ;

frag : (MINUS?) (ID | NUM) # IdNumFrag
     | NOT ID              # NotIdFrag
     | bool_value          # BoolValueFrag
     | ZERO                # ZeroFrag
     ;

func_call : ID LPAREN arg_list RPAREN ;

if_else : IF condition LBRA stmt_block RBRA ELSE LBRA stmt_block RBRA ;

loop : WHILE condition LBRA stmt_block RBRA ;

condition : NOT condition                      # NegCond
          | LPAREN condition RPAREN            # ParenCond
          | expr comp_op_ expr                 # EqualDifOpCond
          | expr comp_op expr                  # CompOpCond
          | condition bin_logical_op condition # BinOpCond
          | bool_value                         # BoolValCond
          ;

arg_list : argument_list* ;

argument_list : (ID | NUM | bool_value) | (ID | NUM | bool_value) COMMA argument_list ; 

bool_value : TRUE | FALSE ;

// Grouping Operators

arith_op : PLUS | MINUS ;

bin_logical_op : AND | OR ;
logical_op : NOT | bin_logical_op ;

comp_op_ : EQUAL | DIFFERENT;

comp_op : GREATER | GREATEREQ 
        | LESS    | LESSEQ 
        ;
