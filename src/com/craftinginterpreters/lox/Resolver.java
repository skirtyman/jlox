package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void>
{
    private enum FunctionType { NONE, FUNCTION, INITIALIZER, METHOD }
    private enum ClassType {NONE, CLASS}
    private final Interpreter interpreter;
    // The Boolean represents if a variable is finished being resolved.
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;

    Resolver(Interpreter interpreter)
    {
        this.interpreter = interpreter;
    }

    void resolve(List<Stmt> statements)
    {
        for (Stmt statement : statements)
        {
            resolve(statement);
        }
    }

    private void resolveFunction(Stmt.Function function, FunctionType type)
    {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param : function.params)
        {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    private void beginScope()
    {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope()
    {
        scopes.pop();
    }

    private void declare(Token name)
    {
        if (scopes.isEmpty()) return;
        Map<String, Boolean> scope = scopes.peek(); // Get the current scope.
        if (scope.containsKey(name.lexeme))
        {
            Lox.error(name, "Already a variable with this name in this scope.");
        }

        scope.put(name.lexeme,false);

    }

    private void define(Token name)
    {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme, true);
    }

    private void resolveLocal(Expr expr, Token name)
    {
        // Determine the number of "hops" to a higher scope required for a variable to be defined in a
        // given expression. If we have found the variable in a given scope then the interpreter can resolve it.
        // If the variable cannot be found then we assume that it is within the global scope.
        for (int i = scopes.size() - 1; i >= 0; i--)
        {
            if(scopes.get(i).containsKey(name.lexeme))
            {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt)
    {
        // A block defines a new scope level within the statements/expressions, therefore we must resolve all of the
        // variables within the block with an increased scope level.
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt)
    {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);

        if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme))
        {
            Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
        }

        define(stmt.name);

        if (stmt.superclass != null)
        {
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null)
        {
            beginScope();
            scopes.peek().put("super", true);
        }

        beginScope();
        scopes.peek().put("this", true);

        for (Stmt.Function method : stmt.methods)
        {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init"))
            {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }
        endScope();

        if (stmt.superclass != null) endScope();
        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt)
    {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt)
    {
        // We define and declare the function within the current scope, this is to enable functions to call themselves.
        declare(stmt.name);
        define(stmt.name);

        // We recursively resolve all the statements within the function body.
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt)
    {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt)
    {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt)
    {
        if (currentFunction == FunctionType.NONE)
        {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if(stmt.value != null)
        {
            if (currentFunction == FunctionType.INITIALIZER)
            {
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt)
    {
        declare(stmt.name);
        if (stmt.initialiser != null)
        {
            resolve(stmt.initialiser);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt)
    {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr)
    {
        // This resolves the scope of any variables within the expression being assigned to the variable.
        resolve(expr.value);
        // We then count the "hops" until the variable being assigned to is declared. Therefore, resolving it.
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr)
    {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr)
    {
        resolve(expr.callee);
        for(Expr argument : expr.arguments)
        {
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr)
    {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr)
    {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr)
    {
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr)
    {
        if (currentClass == ClassType.NONE)
        {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr)
    {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) { return null; }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr)
    {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr)
    {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr)
    {
        // This checks that a variable is declared and initialised to a value, returning an error if not.
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE)
        {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    private void resolve(Stmt stmt)
    {
        stmt.accept(this);
    }

    private void resolve(Expr expr)
    {
        expr.accept(this);
    }
}
