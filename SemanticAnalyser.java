import org.antlr.v4.runtime.tree.*;

import java.util.*;

class CCALSemanticAnalyser {
    private boolean analysisSucceeded = true;
    private final Visitor visitor;
    private final ParseTree tree;

    CCALSemanticAnalyser(ParseTree tree) { 
        this.tree = tree; 
        visitor   = new Visitor();
    }

    private static void failWith(final String errorMsg) {
        System.err.println(errorMsg);
        System.exit(1);
    }
    
    public void performAnalysis() { 
        visitor.visit(tree);     
        if (getErrors().size() == 0) { analysisSucceeded = true; } 
        else { analysisSucceeded = false; }
    }

    public ArrayList<String> getWarnings() { return visitor.warnings;  }
    public ArrayList<String> getErrors()   { return visitor.errors;    }
    public boolean analysisSucceeded()     { return analysisSucceeded; }

    public void outputResult() {
        final String red    = "\u001B[31m";
        final String yellow = "\u001B[33m";
        final String reset  = "\u001B[0m";

        ArrayList<String> errors   = getErrors();
        ArrayList<String> warnings = getWarnings();

        if (errors.size() == 0) { System.out.println("No errors occured."); } 
        else {
            int nErrors = errors.size();
            System.out.println("Semantic analysis reported the following "+ red + 
                nErrors + reset + " errors:");
            for (int i = 1; i <= nErrors; i++) {
                System.out.println(red + " - Error " + i + ": " + 
                    errors.get(i-1) + reset);
            }
        }

        if (warnings.size() == 0) { System.out.println("\nNo warnings."); } 
        else {
            int nWarnings = warnings.size();
            System.out.println("\nAdditionally, semantic analysis reported the" + 
                " following " + yellow + nWarnings + reset + " warnings:");
            for (int i = 1; i <= nWarnings; i++) {
                System.out.println(yellow + " - Warning " + i + ": " + 
                    warnings.get(i-1) + reset);
            }
        }
    }

    private enum Type  { FUNCTION, INTEGER, BOOLEAN, VOID, UNDEF }
    private enum State { DECLARED, ASSIGNED, USED, CONSTANT, PARAMETER, UNDEF }

    /* Class used in our visitor to store & get informations. */
    private class Entity {
        public final Type   type, returnType;
        public final String identifier;
        public final int    arguments;
        public final State  state;

        /* Variable or Constant. */
        Entity(String i, Type t, State s) {
            returnType = Type.UNDEF;
            identifier = i;
            arguments = 0;
            state = s;
            type = t;
        }

        /* Function. */
        Entity(String i, Type rt, int n) {
            state = State.DECLARED;
            type = Type.FUNCTION;
            returnType = rt;
            identifier = i;
            arguments = n;
        }

        /* Function. */
        Entity(String i, Type rt, int n, State s) {
            type = Type.FUNCTION;
            returnType = rt;
            identifier = i;
            arguments = n;
            state = s;
        }

        @Override
        public String toString() {
            String s = "";

            if (type == Type.FUNCTION) {
                s += "[FUNCTION " + this.identifier;
                s += " (" + this.returnType;
                s += ", args:" + this.arguments;
                s += ", " + this.state;
                s += ")]";
                return s;
            }

            if (state == State.CONSTANT) {
                s += "[CONSTANT " + this.identifier;
                s += " (" + this.type;
                s += ")]";
                return s;
            }

            s += "[VARIABLE " + this.identifier;
            s += " (" + this.type;
            s += ", " + this.state;
            s += ")]";

            return s;
        }
    }

    private class Visitor extends CCALBaseVisitor<Entity> {
        private Stack<HashMap<String, Entity>> memory;
        public ArrayList<String> errors;
        public ArrayList<String> warnings;

        Visitor() {
            memory   = new Stack<HashMap<String, Entity>>();
            errors   = new ArrayList<String>();
            warnings = new ArrayList<String>();
        }

        private void error(String id, String why, String where) {
            errors.add("<" + id + "> " + why + " ("+where+").");
        }

        private void warning(String id, String why) {
            warnings.add("<" + id + "> " + why + ".");
        }

        private void alreadyDeclaredError(String id, String where) {
            error(id, "already declared", where);
        } 

        private void alreadyDefinedError(String id, String where) {
            error(id, "already defined", where);
        } 

        private void neverDefinedError(String id, String where) {
            error(id, "never defined", where);
        } 

        private void neverAssignedWarning(String id) {
            warning(id, "is never assigned");
        } 

        private void neverUsedWarning(String id) {
            warning(id, "is never used");
        } 

        private void addAllNeverAssignedWarnings() {
            for (Map.Entry<String, Entity> entry : memory.peek().entrySet()) {
                String id    = entry.getKey();
                Entity value =  entry.getValue();
                if (value.state == State.DECLARED) {
                    if (value.type == Type.FUNCTION) { neverUsedWarning(id); } 
                    else { neverAssignedWarning(id); }
                }
            }
        }

        Type getType(CCALParser.TypeContext ctx) {
            if (ctx.BOOL() != null)    { return Type.BOOLEAN; }
            if (ctx.INTEGER() != null) { return Type.INTEGER; }
            if (ctx.VOID() != null)    { return Type.VOID; }

            // Should be impossible.
            failWith("unexpected behavior");

            return Type.VOID;
        }

        private int getNumberArgs(CCALParser.Arg_listContext ctx) {
            if (ctx.getText() == "") { return 0; }
            return ctx.getText().split(",").length;
        }

        private Type getReturnType(CCALParser.FuncContext ctx) {
            CCALParser.TypeContext tCtx = ctx.type();

            if (tCtx.INTEGER() != null) { return Type.INTEGER; }
            if (tCtx.BOOL()    != null) { return Type.BOOLEAN; }
            if (tCtx.VOID()    != null) { return Type.VOID;    }

            // Should be impossible.
            failWith("unexpected behavior");

            return Type.UNDEF;
        }

        private int getNumberParams(CCALParser.Param_listContext ctx) {
            int colons = 0;
            for (char c : ctx.getText().toCharArray()) {
                if (c == ':') { colons++; }
            }
            return colons;
        }

        @Override
        public Entity visitProgram(CCALParser.ProgramContext ctx) {
            // Visit Decl List
            memory.push(new HashMap<String, Entity>()); // Global Scope
            if (ctx.decl_list() != null) { visit(ctx.decl_list()); }

            // Visit Func List
            if (ctx.func_list() != null) { visit(ctx.func_list()); }

            // Visit Main
            memory.push(new HashMap<String, Entity>()); // Main Scope
            visit(ctx.main());
            memory.pop();

            addAllNeverAssignedWarnings();
            return null;
        }

        @Override
        public Entity visitDecl_list(CCALParser.Decl_listContext ctx) {
            if (ctx.decl(0) != null) { visit(ctx.decl(0)); }
            if (ctx.decl_list(0) != null) { visit(ctx.decl_list(0)); }

            return null;
        }

        @Override
        public Entity visitVar_decl(CCALParser.Var_declContext ctx) {
            String id = ctx.ID().getText();

            // Error if the variable was already declared in this scope.
            if (memory.peek().containsKey(id)) { 
                alreadyDeclaredError(id, ctx.getText()); 
            }

            Type type = getType(ctx.type());

            Entity variable = new Entity(id, type, State.DECLARED);
            memory.peek().put(id, variable);
            return variable;
        }

        @Override
        public Entity visitConst_decl(CCALParser.Const_declContext ctx) {
            String id = ctx.ID().getText();

            Entity expression = visit(ctx.expr());

            // Error if the constant was already declared in this scope.
            if (memory.peek().containsKey(id)) { 
                alreadyDeclaredError(id, ctx.getText()); 
            }

            Type type = getType(ctx.type());

            // Type Checking
            if (type != expression.type) {
                error(id, "type error", ctx.getText());
            }

            Entity constant = new Entity(id, type, State.CONSTANT); 
            memory.peek().put(id, constant);
            return constant;
        }

        @Override
        public Entity visitFunc_list(CCALParser.Func_listContext ctx) {
            if (ctx.func(0) != null) { 
                // Each function has its own scope.
                memory.push(new HashMap<String, Entity>());
                visit(ctx.func(0)); 
                memory.pop();
            }

            if (ctx.func_list(0) != null) { visit(ctx.func_list(0)); }

            return null;
        }


        @Override
        public Entity visitFunc(CCALParser.FuncContext ctx) {
            String id = ctx.ID().getText();
            Entity function = null;

            // Error if func is defined more than once.
            if (memory.get(0).containsKey(id)) {
                alreadyDefinedError(id, ctx.getText());
                return function;
            } else {
                Type returnType = getReturnType(ctx);
                int  args = getNumberParams(ctx.param_list());

                function = new Entity(id, returnType, args);
                memory.get(0).put(id, function);
            }

            if (ctx.param_list() != null) { visit(ctx.param_list()); }
            if (ctx.decl_list()  != null) { visit(ctx.decl_list());  }
            if (ctx.stmt_block() != null) { visit(ctx.stmt_block()); }

            if (ctx.expr() != null) { 
                Entity expr = visit(ctx.expr());
                if (expr.type != function.returnType) {
                    error(id, "function return type and expr returned do not match", ctx.getText());
                }
            }

            if (ctx.expr() == null && function.returnType != Type.VOID) {
                error(id, "function return type and expr returned do not match", ctx.getText());
            }

            // Add a warning for each unused variable.
            addAllNeverAssignedWarnings();

            return function;
        }

        @Override
        public Entity visitFuncCallStmt(CCALParser.FuncCallStmtContext ctx) {
            return visit(ctx.func_call());
        }
    
        @Override
        public Entity visitFunc_call(CCALParser.Func_callContext ctx) {
            String id = ctx.ID().getText();

            Entity funcCall = null;

            // Error if the function called was never defined.
            if (!memory.get(0).containsKey(id)) {
                neverDefinedError(id, ctx.getText());
                return new Entity("undefined", Type.UNDEF, -1);
            } else { funcCall = memory.get(0).get(id); }

            int numberArgs = getNumberArgs(ctx.arg_list());

            if (numberArgs < funcCall.arguments) {
                error(id, "requires more arguments", ctx.getText());
            } else if (numberArgs > funcCall.arguments) {
                error(id, "requires less arguments", ctx.getText());
            }

            Entity stateChanged = new Entity(
                funcCall.identifier, funcCall.returnType, 
                funcCall.arguments, State.USED
            );

            memory.get(0).replace(funcCall.identifier, stateChanged);

            visit(ctx.arg_list());
            return stateChanged;
        }

        @Override
        public Entity visitArg_list(CCALParser.Arg_listContext ctx) {
            if (ctx.argument_list(0) != null) { return visit(ctx.argument_list(0)); }
            return null;
        }

        @Override
        public Entity visitArgument_list(CCALParser.Argument_listContext ctx) {
            if (ctx.ID() != null) {
                String  id = ctx.ID().getText();
                boolean declared = false;

                for (int i = memory.size()-1; i >= 0; i--) {
                    if (memory.get(i).containsKey(id)) {
                        declared = true;

                        if (memory.get(i).get(id).type == Type.FUNCTION) {
                            error(id, "passed as argument but is a function", 
                                ctx.parent.parent.getText());
                        } else if (memory.get(i).get(id).state == State.DECLARED) {
                            error(id, "passed as argument but was never assigned", 
                                ctx.parent.parent.getText());
                        }

                        break;
                    }
                }
                if (!declared) {
                    error(id, "passed as argument but was never declared", 
                        ctx.parent.parent.getText());
                }
            }
            if (ctx.argument_list() != null) { return visit(ctx.argument_list()); }
            return null;
        }

        @Override
        public Entity visitFragExpr(CCALParser.FragExprContext ctx) {
            return visit(ctx.frag());
        }

        @Override
        public Entity visitIdNumFrag(CCALParser.IdNumFragContext ctx) {
            if (ctx.ID() != null) {
                String id = ctx.ID().getText();
                for (int i = memory.size()-1; i >= 0; i--) {
                    if (memory.get(i).containsKey(id)) {
                        if (memory.get(i).get(id).type == Type.FUNCTION) {
                            error(id, "cannot be used, it is a function", 
                                ctx.getText()); 
                        }  else if (memory.get(i).get(id).state == State.DECLARED) {
                            error(id, "cannot be used, it was never assigned a value", 
                                ctx.getText());
                        }
                        return memory.get(i).get(id);
                    }
                }
                error(id, "cannot be used, it was never declared", ctx.parent.parent.getText());
            }
            return new Entity("_", Type.INTEGER, State.UNDEF);
        }

        @Override
        public Entity visitBoolValueFrag(CCALParser.BoolValueFragContext ctx) {
            return new Entity("_", Type.BOOLEAN, State.UNDEF);
        }

        @Override
        public Entity visitNotIdFrag(CCALParser.NotIdFragContext ctx) {
            String id = ctx.ID().getText();
            for (int i = memory.size()-1; i >= 0; i--) {
                if (memory.get(i).containsKey(id)) {
                    if (memory.get(i).get(id).type == Type.FUNCTION) {
                        error(id, "cannot be used, it is a function", 
                            ctx.getText()); 
                    }  else if (memory.get(i).get(id).state == State.DECLARED) {
                        error(id, "cannot be used, it was never assigned a value", 
                            ctx.getText());
                    }
                    return memory.get(i).get(id);
                }
            }
            error(id, "cannot be used, it was never declared", ctx.parent.parent.getText());
            return null;
        }

        @Override
        public Entity visitZeroFrag(CCALParser.ZeroFragContext ctx) {
            return new Entity("_", Type.INTEGER, State.UNDEF);
        }

        @Override
        public Entity visitArithOpExpr(CCALParser.ArithOpExprContext ctx) {
            Entity left  = visit(ctx.expr(0));
            Entity right = visit(ctx.expr(1));
            if (left.type != Type.INTEGER && left.returnType != Type.INTEGER) {
                error(left.identifier, "cannot use non integer type with arithmetic operator", ctx.getText());
            }
            if (right.type != Type.INTEGER && right.returnType != Type.INTEGER) {
                error(right.identifier, "cannot use non integer type with arithmetic operator", ctx.getText());
            }
            return new Entity("_", Type.INTEGER, State.UNDEF);
        }

        @Override
        public Entity visitLogOpExpr(CCALParser.LogOpExprContext ctx) {
            Entity left  = visit(ctx.expr(0));
            Entity right = visit(ctx.expr(1));
            if (left.type != Type.BOOLEAN && left.returnType != Type.BOOLEAN) {
                error(left.identifier, "cannot use non boolean type with logical operator", ctx.getText());
            }
            if (right.type != Type.BOOLEAN && right.returnType != Type.BOOLEAN) {
                error(right.identifier, "cannot use non boolean type with logical operator", ctx.getText());
            }
            return new Entity("_", Type.BOOLEAN, State.UNDEF);
        }

        @Override
        public Entity visitStmt_block(CCALParser.Stmt_blockContext ctx) {
            if (ctx.stmt(0) != null) { visit(ctx.stmt(0)); }
            if (ctx.stmt_block(0) != null) { return visit(ctx.stmt_block(0)); }
            return null;
        }

        @Override
        public Entity visitAssignmentStmt(CCALParser.AssignmentStmtContext ctx) {
            return visit(ctx.assignment());
        }

        @Override
        public Entity visitIfElseStmt(CCALParser.IfElseStmtContext ctx) {
            return visit(ctx.if_else());
        }

        @Override
        public Entity visitIf_else(CCALParser.If_elseContext ctx) {
            visit(ctx.condition());
            visit(ctx.stmt_block(0));
            return visit(ctx.stmt_block(1));
        }

        @Override
        public Entity visitParenExpr(CCALParser.ParenExprContext ctx) {
            return visit(ctx.expr());
        }

        @Override
        public Entity visitBracketsStmt(CCALParser.BracketsStmtContext ctx) {
            return visit(ctx.stmt_block());
        }

        @Override
        public Entity visitSKPStmt(CCALParser.SKPStmtContext ctx) {
            return null;
        }

        @Override
        public Entity visitCompOpCond(CCALParser.CompOpCondContext ctx) {
            Entity left = visit(ctx.expr(0));
            Entity right = visit(ctx.expr(1));
            if (left.type != Type.INTEGER && left.returnType != Type.INTEGER) {
                error(left.identifier, "can not use comparison operator on non-integer", ctx.getText());
            }
            if (right.type != Type.INTEGER && right.returnType != Type.INTEGER) {
                error(right.identifier, "can not use comparison operator on non-integer", ctx.getText());
            }

            return null;
        }

        @Override
        public Entity visitEqualDifOpCond(CCALParser.EqualDifOpCondContext ctx) {
            Entity left  = visit(ctx.expr(0));
            Entity right = visit(ctx.expr(1));

            if (left.type != right.type || left.returnType != right.returnType) {
                error(left.identifier, "can not use comparison operator " + 
                    "on different types", ctx.getText());
            }

            return null;
        }

        @Override
        public Entity visitNegCond(CCALParser.NegCondContext ctx) {
            return visit(ctx.condition());
        }

        @Override
        public Entity visitParenCond(CCALParser.ParenCondContext ctx) {
            return visit(ctx.condition());
        }

        @Override
        public Entity visitBinOpCond(CCALParser.BinOpCondContext ctx) {
            visit(ctx.condition(0));
            visit(ctx.condition(1));
            return null;
        }

        @Override
        public Entity visitBoolValCond(CCALParser.BoolValCondContext ctx) {
            return null;
        }

        @Override
        public Entity visitLoopStmt(CCALParser.LoopStmtContext ctx) {
            return visit(ctx.loop());
        }

        @Override
        public Entity visitLoop(CCALParser.LoopContext ctx) {
            visit(ctx.condition());
            return visit(ctx.stmt_block());
        }

        @Override
        public Entity visitAssignment(CCALParser.AssignmentContext ctx) {
            final String id = ctx.ID().getText();
            Entity expr = visit(ctx.expr());
            if (expr.identifier != "_") {
                for (int i = memory.size()-1; i >= 0; i--) {
                    if (memory.get(i).containsKey(id)) {
                        if (expr.type == Type.FUNCTION) {
                            Entity stateChanged = new Entity(expr.identifier, 
                                expr.returnType, expr.arguments, State.USED);
                            memory.get(0).replace(expr.identifier, stateChanged);
                        } else {
                            Entity stateChanged = new Entity(expr.identifier, 
                                expr.type, State.USED);
                            memory.get(i).replace(expr.identifier, stateChanged);
                        }
                    }
                }
            }
            boolean declared = false;
            for (int i = memory.size()-1; i >= 0; i--) {
                if (memory.get(i).containsKey(id)) {
                    Entity var = memory.get(i).get(id);
                    if (var.type != expr.type && var.type != expr.returnType) {
                        error(id, "type do not match", ctx.getText());
                    }
                    Entity stateChanged = new Entity(var.identifier, var.type, State.ASSIGNED);
                    memory.get(i).replace(id, stateChanged);
                    declared = true;
                    break;
                }
            }
            if (!declared) {
                error(id, "assigned but was not previously declared", ctx.getText());
            }
            return expr;
        }

        @Override
        public Entity visitFuncCallExpr(CCALParser.FuncCallExprContext ctx) {
            return visit(ctx.func_call());
        }

        @Override
        public Entity visitParam_list(CCALParser.Param_listContext ctx) {
            if (ctx.parameter_list(0) != null) { 
                return visit(ctx.parameter_list(0)); 
            }
            return null;
        }

        @Override
        public Entity visitSingleParam(CCALParser.SingleParamContext ctx) {
            return visit(ctx.param());
        }

        @Override
        public Entity visitNotSingleParam(CCALParser.NotSingleParamContext ctx) {
            visit(ctx.param());
            return visit(ctx.parameter_list());
        }

        @Override
        public Entity visitParam(CCALParser.ParamContext ctx) {
            String id    = ctx.ID().getText();
            Type   type  = getType(ctx.type());
            Entity param = new Entity(id, type, State.PARAMETER);
            memory.peek().put(id, param);

            return param;
        }

        @Override
        public Entity visitMain(CCALParser.MainContext ctx) {
            if (ctx.decl_list() != null) { visit(ctx.decl_list()); }
            if (ctx.stmt_block() != null) { visit(ctx.stmt_block()); }
            addAllNeverAssignedWarnings();
            return null;
        }
    }
}
