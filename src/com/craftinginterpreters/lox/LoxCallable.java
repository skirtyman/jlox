package com.craftinginterpreters.lox;

import java.util.List;

// Used to define the callable types within Lox.
interface LoxCallable
{
    int arity();
    // To call a function a given interpreter and list of arguments is supplied.
    Object call(Interpreter interpreter, List<Object> arguments);
}
