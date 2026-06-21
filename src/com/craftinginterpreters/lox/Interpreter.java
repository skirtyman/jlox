package com.craftinginterpreters.lox;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>
{
    // Define the environment for global variables within a Lox program.
    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter()
    {
        // Define a native function which is a function provided by Lox that is defined as a callable global variable.
        // This implements the LoxCallable interface to ensure that the global acts as a function.

        // A native function is a function that is provided by Lox that is implemented with Java/OS and not Lox itself.
        globals.define("clock", new LoxCallable()
        {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    void interpret(List<Stmt> statements)
    {
        try
        {
            for (Stmt statement : statements)
                execute(statement);
        } catch (RuntimeError error)
        {
            Lox.runtimeError(error);
        }
    }

    private void execute(Stmt stmt)
    {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth)
    {
        locals.put(expr, depth);
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt)
    {
        // Capture the current environment of the interpreter and supply it to the function being created.
        // This enables locally defined functions to retain the variables defined in the higher scope, but
        // not necessarily the global scope. This happens when the function is declared but not called.
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt)
    {
        Object value = null;
        // Work out the value of the variable declaration by evaluating the initializer.
        // We allow variables to be defined without an initializer, simply adding them to the global environment.
        if (stmt.initialiser != null) value = evaluate(stmt.initialiser);

        // Define the global variable within the global environment.
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt)
    {
        while (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt)
    {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt)
    {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt)
    {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt)
    {
        if (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.thenBranch);
        }
        else if (stmt.elseBranch != null)
        {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt)
    {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new Return(value);
    }

    void executeBlock(List<Stmt> statements, Environment environment)
    {
        // Store a local copy of the environment before executing the block.
        Environment previous = this.environment;

        // Execute the block making any necessary changes.
        try
        {
            // Change the current environment of the interpreter to the new one to be executed,
            // and execute the statements within it sequentially.
            this.environment = environment;
            for (Stmt statement : statements)
                execute(statement);
        }
        finally
        {
            // Update the current environment of the interpreter to the old state.
            this.environment = previous;
        }
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr)
    {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null)
        {
            environment.assignAt(distance, expr.name, value);
        }
        else
        {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr)
    {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR)
        {
            if (isTruthy(left)) return left;
        }
        else
        {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) { return expr.value; }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) { return evaluate(expr.expression); }

    private Object evaluate(Expr expr) { return expr.accept(this); }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr)
    {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type)
        {
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double)left + (double)right;
                if (left instanceof String && right instanceof String)
                    return (String)left + (String)right;
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings. ");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        // Unreachable
        return null;
    }

    private void checkNumberOperands(Token operator, Object left, Object right)
    {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers. ");
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr)
    {
        Object right = evaluate(expr.right);

        switch (expr.operator.type)
        {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
            case BANG:
                return !isTruthy(right);
        }

        // Unreachable
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr)
    {
        // Determine the primary to be called. This may be a literal at this point.
        Object callee = evaluate(expr.callee);

        // Create a list of the evaluated arguments being supplied to the callee.
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments)
        {
            arguments.add(evaluate(argument));
        }

        // Check that the callee is a thing that can be called. I.e. A LoxCallable instance.
        if (!(callee instanceof LoxCallable))
        {
            throw new RuntimeError(expr.paren, "Can only call functions and classes");
        }

        // Defines a unified interface for any Lox object that can be invoked like a function.
        // It standardizes how user-defined functions, native methods, and class constructors
        // accept arguments and execute code within the interpreter.
        LoxCallable function = (LoxCallable) callee;

        // Check that the number of arguments supplied to the function is the correct number. I.e. the size of
        // [arguments] == the function's arity.
        if (arguments.size() != function.arity())
        {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }

        // call acts as a generic method to call things that are callable within Lox.
        return function.call(this, arguments);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr)
    {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr)
    {
        Integer distance = locals.get(expr);
        if (distance != null)
        {
            return environment.getAt(distance, name.lexeme);
        }
        else
        {
            return globals.get(name);
        }
    }

    private void checkNumberOperand(Token operator, Object operand)
    {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number. ");
    }

    // Determine if an object is a truthful value and if so which one is it.
    private boolean isTruthy(Object object)
    {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b)
    {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private String stringify(Object object)
    {
        if (object == null) return "nil";
        if (object instanceof Double)
        {
            String text = object.toString();
            if (text.endsWith(".0"))
                text = text.substring(0, text.length() - 2);
            return text;
        }
        return object.toString();
    }
}