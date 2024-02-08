import org.antlr.v4.runtime.tree.ParseTree;

import java.util.*;

public class IRCodeGenerator {
    final Visitor visitor;
    final ParseTree tree;
    StringBuilder result; // The intermediate code is stored here.

    IRCodeGenerator(ParseTree tree) {
        this.tree = tree;
        visitor   = new Visitor();
        result    = new StringBuilder();
    }

    public String generate() { 
        visitor.visit(tree); 
        return result.toString(); 
    }

    private class Visitor extends CCALBaseVisitor<String> {
        int labelIndex, condIndex, tempVarIndex, funcCount;
        Stack<String> globalConstants, functionParams;
        Stack<HashMap<String, String>> funcVariables;
        ArrayList<String> args;
        boolean funcCtx;

        Visitor() {
            labelIndex = condIndex = tempVarIndex = funcCount = 1;
            globalConstants = new Stack<>();
            functionParams = new Stack<>();
            funcVariables = new Stack<>();
            args = new ArrayList<>();
            funcCtx = false;
        }

        // Make an id unique in a function.
        String makeUnique(String id) {
            String result = id;
            for (int i = 0; i < funcCount; i++) { result += "_"; }
            return result;
        }

        // Make a label unique.
        String makeUniqueLabel(int i) {
            String label = "L";
            for (int und = labelIndex; und > 0; und--) { label += "_"; und--; }
            return label + i;
        }

        // Convert an arithmetic expression to TAC. 
        String convertArithOpExpr(final String expr) {
            String[] split = expr
                .replaceAll("\\(|\\)", "")
                .replaceAll("\\+", " \\+ ")
                .replaceAll("-", " - ")
                .split(" ");

            // For some reasons this case must be handled.
            if (split.length == 1) { 
                String a1 = split[0];
                if (funcCtx && funcVariables.peek().containsKey(split[0])) {
                        a1 = funcVariables.peek().get(split[0]);
                }
                return a1; 
            }

            // Expression is in the form "A op B".
            if (split.length == 3) { 
                String a1 = split[0], a2 = split[2];
                if (funcCtx) {
                    if (funcVariables.peek().containsKey(split[0])) {
                        a1 = funcVariables.peek().get(split[0]);
                    }
                    if (funcVariables.peek().containsKey(split[2])) {
                        a2 = funcVariables.peek().get(split[2]);
                    }
                }
                return a1 + " " + split[1] + " " + a2; 
            }

            Stack<String> s = new Stack<>();

            for (int i = split.length-1; i >= 0; i--) { 
                if (funcCtx && funcVariables.peek().containsKey(split[i])) {
                        s.push(funcVariables.peek().get(split[i])); 
                } else { s.push(split[i]); }
            }

            String a1 = s.pop();
            String op = s.pop();
            String a2 = s.pop();

            String ti = "t" + tempVarIndex;
            result.append(ti + " = " + a1 + " " + op + " " + a2 + "\n");
            s.push(ti);

            while (s.size() > 2) {
                tempVarIndex++;
                ti  = "t" + tempVarIndex;
                a1  = s.pop();
                op  = s.pop();
                a2  = s.pop();
                result.append(ti + " = " + a1 + " " + op + " " + a2 + "\n");
                s.push(ti);
            }

            tempVarIndex++;
            return s.peek();
        }

        @Override
        public String visitProgram(CCALParser.ProgramContext ctx) {
            funcCtx = true;
            visit(ctx.func_list());
            /* We want our global variables to be after the main label,
               so we visit the function declarations first. */
            result.append("\nmain:\n");
            funcCtx = false;
            visit(ctx.decl_list());
            funcCtx = true;
            return visit(ctx.main());
        }

        @Override
        public String visitDecl_list(CCALParser.Decl_listContext ctx) {
            if (ctx.decl(0) != null) { 
                String decl = visit(ctx.decl(0));
                if (decl != null) { globalConstants.add(decl); }
            }
            if (ctx.decl_list(0) != null) { visit(ctx.decl_list(0)); }
            return null;
        }

        @Override
        public String visitVar_decl(CCALParser.Var_declContext ctx) { 
            /* If in func context, make its label unique and save it
               else, do nothing, the used TAC interpreter is dynamically typed. */
            if (funcCtx) {
                String id = ctx.ID().getText();
                String og = id;
                id = makeUnique(id);
                funcVariables.peek().put(og, id);
            }
            return null; 
        }

        @Override
        public String visitConst_decl(CCALParser.Const_declContext ctx) {
            String id = ctx.ID().getText();
            // If in func context, must be made unique.
            if (funcCtx) {
                String og = id;
                id = makeUnique(id);
                funcVariables.peek().put(og, id);
            }
            String value = convertArithOpExpr(ctx.expr().getText());
            return id + " = " + value;
        }

        @Override
        public String visitFunc_list(CCALParser.Func_listContext ctx) {
            if (ctx.func(0) != null)      { visit(ctx.func(0)); }
            if (ctx.func_list(0) != null) { visit(ctx.func_list(0)); }
            return null;
        }

        @Override
        public String visitFunc(CCALParser.FuncContext ctx) {
            String funcID = ctx.ID().getText();
            result.append("\n" + funcID + ":\n");
            funcVariables.push(new HashMap<String, String>());
            visit(ctx.param_list());
            funcCtx = true;
            visit(ctx.decl_list());
            int i = 1;
            while (!functionParams.isEmpty()) {
                String param = functionParams.pop();
                result.append(param + " = getparam " + i + "\n");
                i++;
            }
            visit(ctx.stmt_block());
            String ret = visit(ctx.expr());
            result.append("return " + ret + "\n");
            funcVariables.pop();
            funcCount++;
            return null;
        }

        @Override
        public String visitStmt_block(CCALParser.Stmt_blockContext ctx) {
            if (ctx.stmt(0) != null)       { visit(ctx.stmt(0));       }
            if (ctx.stmt_block(0) != null) { visit(ctx.stmt_block(0)); }
            return null;
        }

        @Override
        public String visitAssignmentStmt(CCALParser.AssignmentStmtContext ctx) {
            return visit(ctx.assignment());
        }

        @Override
        public String visitAssignment(CCALParser.AssignmentContext ctx) {
            String expr = visit(ctx.expr());
            if (expr != null) {
                String id = ctx.ID().getText();
                if (funcCtx && funcVariables.peek().containsKey(id)) {
                    String _id = funcVariables.peek().get(id);
                    result.append(_id + " = "  + expr + "\n");
                } else { result.append(id + " = "  + expr + "\n"); }
            }
            return null;
        }

        @Override
        public String visitParam_list(CCALParser.Param_listContext ctx) {
            if (ctx.parameter_list(0) != null) { visit(ctx.parameter_list(0)); }
            return null;
        }

        @Override
        public String visitSingleParam(CCALParser.SingleParamContext ctx) {
            return visit(ctx.param());
        }

        @Override
        public String visitLoopStmt(CCALParser.LoopStmtContext ctx) {
            return visit(ctx.loop());
        }

        @Override
        public String visitLoop(CCALParser.LoopContext ctx) {
            String label1 = makeUniqueLabel(1);
            String label2 = makeUniqueLabel(2);
            result.append(label1 + ": \n");
            labelIndex += 2;
            String condition = getCondition(ctx.condition());
            result.append("ifz " + condition + " goto " + label2 + "\n");
            labelIndex += 2;
            visit(ctx.stmt_block());
            result.append("goto " + label1 + "\n");
            result.append(label2 + ":\n");
            return null;
        }

        @Override
        public String visitIfElseStmt(CCALParser.IfElseStmtContext ctx) {
            return visit(ctx.if_else());
        }


        @Override
        public String visitArithOpExpr(CCALParser.ArithOpExprContext ctx) {
            return convertArithOpExpr(ctx.getText());
        }

        @Override
        public String visitFragExpr(CCALParser.FragExprContext ctx) {
            return visit(ctx.frag());
        }

        @Override
        public String visitNotIdFrag(CCALParser.NotIdFragContext ctx) {
            return "!" + ctx.ID().getText();
        }

        @Override
        public String visitZeroFrag(CCALParser.ZeroFragContext ctx) {
            return "0";
        }

        @Override
        public String visitIdNumFrag(CCALParser.IdNumFragContext ctx) {
            if (ctx.NUM() != null) { 
                if (ctx.MINUS() != null) { return "0 - " + ctx.NUM().getText(); }
                return ctx.NUM().getText(); 
            }
            String id = ctx.ID().getText();
            if (funcCtx) { id = funcVariables.peek().get(id); }
            if (ctx.MINUS() != null) { return "0 - " + id; }
            return id;
        }

        String replaceConditionVars(String cond) {
            String condition = cond.replaceAll("[_a-zA-Z0-9\\(\\)]*", "");
            String[] split = cond.split(condition);
            String res = "";
            if (funcVariables.peek().containsKey(split[0])) {
                res += funcVariables.peek().get(split[0]);
            } else { res += split[0]; }
            res += condition;

            if (funcVariables.peek().containsKey(split[1])) {
                res += funcVariables.peek().get(split[1]);
            } else { res += split[1]; }

            return res;
        }

        String getCondition(CCALParser.ConditionContext ctx) {
            labelIndex++;
            String[] split = ctx.getText()
                .replaceAll("\\(", "")
                .replaceAll("\\)", "")
                .replaceAll("&&", " && ")
                .replaceAll("\\|\\|", " \\|\\| ")
                .split(" ")
            ;

            Stack<String> conditions = new Stack<>();
            Stack<String> newConditions = new Stack<>();
            Stack<String> operators = new Stack<>();

            for (String s : split) {
                if (s.equals("&&") || s.equals("||")) { operators.push(s); } 
                else { conditions.push(s); }
            }

            int i = 1;
            while (!conditions.empty()) {
                String label = makeUniqueLabel(i);

                String cond = conditions.pop();
                cond = replaceConditionVars(cond);
                String c = "c" + condIndex;
                newConditions.push(c);

                result.append(c + " = false\n");
                result.append("ifz " + cond + " goto " + label + "\n");
                result.append(c + " = true\n");
                result.append(label + ":\n");
                condIndex++;
                i++;
            }

            String c = "c" + condIndex;
            while (newConditions.size() > 1) {
                c = "c" + condIndex;
                String op = operators.pop();
                String cA = newConditions.pop();
                String cB = newConditions.pop();
                replaceConditionVars(cA);
                result.append(c + " = " + cA + " " + op + " " + cB + "\n");
                newConditions.push(c);
                condIndex++;
            }

            return newConditions.peek() + " == true";
        }

        @Override
        public String visitIf_else(CCALParser.If_elseContext ctx) {
            String label1 = makeUniqueLabel(1);
            String label2 = makeUniqueLabel(2);

            labelIndex += 2;
            String condition = getCondition(ctx.condition());
            result.append("ifz " + condition + " goto " + label1 + "\n");

            labelIndex += 2;
            visit(ctx.stmt_block(0));

            result.append("goto "  + label2 + "\n");
            result.append(label1 + ":\n");

            labelIndex += 2;
            visit(ctx.stmt_block(1));

            result.append(label2 + ":\n");
            labelIndex += 2;

            return null;
        }

        @Override
        public String visitNotSingleParam(CCALParser.NotSingleParamContext ctx) {
            visit(ctx.param());
            return visit(ctx.parameter_list());
        }

        @Override
        public String visitArg_list(CCALParser.Arg_listContext ctx) {
            if (ctx.argument_list(0) != null) { visit(ctx.argument_list(0)); }
            return null;
        }

        @Override
        public String visitBoolValueFrag(CCALParser.BoolValueFragContext ctx) {
            return ctx.bool_value().getText();
        }

        @Override
        public String visitArgument_list(CCALParser.Argument_listContext ctx) {
            if (ctx.ID() != null) { 
                String id = ctx.ID().getText();
                if (funcCtx && funcVariables.peek().containsKey(id)) {
                    args.add(funcVariables.peek().get(id));
                } else { args.add(id); }
            }
            if (ctx.NUM() != null) { args.add(ctx.NUM().getText()); }
            if (ctx.argument_list() != null) { return visit(ctx.argument_list()); }
            return null;
        }

        @Override
        public String visitFunc_call(CCALParser.Func_callContext ctx) {
            String id = ctx.ID().getText();
            visit(ctx.arg_list());
            for (String arg : args) { 
                result.append("param " + arg + "\n");
            }
            String call = "call " + id + ", " + args.size();
            if (ctx.parent.parent.getClass() != CCALParser.AssignmentContext.class) {
                result.append(call + "\n");
            }
            args.clear();
            return call;
        }
        
        @Override
        public String visitParam(CCALParser.ParamContext ctx) {
            String id = ctx.ID().getText();
            String og = id;
            id = makeUnique(id);
            functionParams.push(id); 
            funcVariables.peek().put(og, id);
            return id; 
        }

        @Override
        public String visitMain(CCALParser.MainContext ctx) {
            funcVariables.push(new HashMap<>());
            funcCtx = true;
            visit(ctx.decl_list());
            while (!globalConstants.empty()) {
                String constant = globalConstants.pop();
                result.append(constant + "\n");
            }
            visit(ctx.stmt_block());
            result.append("call _exit, 0\n");
            return null;
        }
    }
}