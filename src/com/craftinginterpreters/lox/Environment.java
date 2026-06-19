package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment
{
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    // Used for the global environment, which does not have an enclosing environment.
    Environment() { enclosing = null; }
    // Used for locally scoped blocks which are bounded by an enclosing environment.
    Environment(Environment enclosing) { this.enclosing = enclosing; }


    // Create a new variable in the environment.
    void define(String name, Object value) { values.put(name, value); }

    // Retrieve a value of a variable stored within the environment.
    Object get(Token name)
    {
        if (values.containsKey(name.lexeme))
            return values.get(name.lexeme);
        // Recusively lookup values in an enclosing environment.
        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    // Assign a new value to the
    void assign(Token name, Object value)
    {
        if (values.containsKey(name.lexeme))
            values.put(name.lexeme, value);
        if (enclosing != null) enclosing.assign(name, value);
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }
}
