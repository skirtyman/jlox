package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable
{
    private final Stmt.Function declaration;
    private final boolean isInitializer;
    // This is the surrounding environment of the function. This enables functions to be defined
    // locally within other functions as the closure represents the surrounding function's environment and not
    // default to the global environment.
    private final Environment closure;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer)
    {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
    }

    LoxFunction bind(LoxInstance instance)
    {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
    }

    @Override
    public int arity()
    {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments)
    {
        Environment environment = new Environment(closure);
        for (int i = 0 ; i < declaration.params.size(); i++)
        {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try
        {
            interpreter.executeBlock(declaration.body, environment);
        }
        catch (Return returnValue)
        {
            if (isInitializer) return closure.getAt(0, "this");
            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");
        return null;
    }

    @Override
    public String toString()
    {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
