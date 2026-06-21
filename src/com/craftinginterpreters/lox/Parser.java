package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser
{
    private static class ParseError extends RuntimeException {}

    // The list of parsed tokens.
    private final List<Token> tokens;
    // Points to the next token to be parsed in the `tokens` list.
    private int current = 0;

    Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    // Parse a series of statements. Currently, statements are either expression statements or print statements.
    List<Stmt> parse()
    {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd())
        {
            statements.add(declaration());
        }
        return statements;
    }

    private Stmt declaration()
    {
        try
        {
            if (match(CLASS)) return classDeclaration();
            if (match(FUN)) return function("function");
            if (match(VAR)) return varDeclaration();
            return statement();
        }
        catch (ParseError error)
        {
            synchronize();
            return null;
        }
    }

    // Parse a class declaration.
    private Stmt classDeclaration()
    {
        Token name = consume(IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(LESS))
        {
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd())
        {
            methods.add(function("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");
        return new Stmt.Class(name, superclass, methods);
    }

    // Parse a function declaration.
    private Stmt.Function function(String kind)
    {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name. ");

        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        // Parse the list of the arguments in the function declaration.
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN))
        {
            do
            {
                if (parameters.size() >= 255)
                {
                    error(peek(), "Can't have more than 255 parameters.");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while(match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        // Parse the body of the function declaration.
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    // Parse a given statement looking at the produced tokens from the scanner.
    private Stmt statement()
    {
        // Print token has been found.
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(WHILE)) return whileStatement();
        if (match(IF)) return ifStatement();
        if (match(FOR)) return forStatement();
        if (match(RETURN)) return returnStatement();

        // Default to parsing an expression statement if no other match can be found.
        return expressionStatement();
    }

    private Stmt varDeclaration()
    {
        // Get the name and store the token, the parser can move on and detect if an initializer is present
        // in the variable declaration.
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) initializer = expression();

        consume(SEMICOLON, "Expect ';' after variable declaration. ");
        return new Stmt.Var(name, initializer);
    }

    private Stmt forStatement()
    {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // Parsing loop initializer.
        Stmt initializer;
        if (match(SEMICOLON))
        {
            initializer = null;
        }
        else if (match(VAR))
        {
            initializer = varDeclaration();
        }
        else
        {
            initializer = expressionStatement();
        }

        // Parsing the loop condition
        Expr condition = null;
        // Check if a condition has been supplied and it is not just a semicolon.
        if (!check(SEMICOLON))
        {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition. ");

        // Parsing the loop increment.
        Expr increment = null;
        if (!check(RIGHT_PAREN))
        {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses. ");

        // Parse the loop body
        Stmt body = statement();

        // De-sugar the for loop into while loop syntax.
        if (increment != null)
        {
            // If the increment is not null, then create a block which stores a list of statements of the following form.
            // ```lox: { <loop body>; <increment>; } ```
            body = new Stmt.Block(
                    Arrays.asList(body, new Stmt.Expression(increment))
            );
        }

        // Parse the condition. We assume that if a condition in the for loop has not been specified then this results
        // in an infinite while loop.
        if (condition == null) condition = new Expr.Literal(true);
        // The de-sugared For loop, represented as a while loop.
        body = new Stmt.While(condition, body);

        // Parse the initalizer.
        if (initializer != null)
        {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt whileStatement()
    {
        consume(LEFT_PAREN, "Expect '(' after 'while'. ");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'while'. ");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt printStatement()
    {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt ifStatement()
    {
        consume(LEFT_PAREN, "Expect '(' after 'if'. ");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'if'. ");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE))
        {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);

    }

    private Stmt returnStatement()
    {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON))
        {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt expressionStatement()
    {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(value);
    }

    private List<Stmt> block()
    {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd())
            statements.add(declaration());

        consume(RIGHT_BRACE, "Expect '}' after block. ");
        return statements;
    }

    // Parse an expression.
    private Expr expression() { return assignment(); }

    private Expr assignment()
    {
        Expr expr = or();

        if (match(EQUAL))
        {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable)
            {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            else if (expr instanceof Expr.Get)
            {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or()
    {
        Expr expr = and();

        while (match(OR))
        {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and()
    {
        Expr expr = equality();

        while (match(AND))
        {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // Parse equality expressions such as == and !=.
    private Expr equality()
    {
        // equality -> comparison ( ("!=" | "==" ) comparison )*;
        // This means equality according to lox's grammar is given by:
        // A comparative logical subexpression such as (a < b) which importantly must be evaluated before
        // considering the == or != operator. The ( ("!=" | "==" ) comparison )* term maps 0 or more != and ==
        // operations to the original comparative value.

        // Descend into the first comparison and produce the expression tree from this subtree.
        Expr expr = comparison();

        // Consume the arbitrary number of == and != operators.
        while (match(BANG_EQUAL, EQUAL_EQUAL))
        {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison()
    {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL))
        {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term()
    {
        Expr expr = factor();

        while (match(PLUS, MINUS))
        {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor()
    {
        Expr expr = unary();

        while (match(SLASH, STAR))
        {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary()
    {
        if (match(BANG, MINUS))
        {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    // Parse a function call.
    private Expr call()
    {
        // Parse the function callee using recursive descent, this is usually a name but can be a literal,
        // based off of the grammar. This is not an issue as we can catch and throw an error at runtime
        // within the interpreter.
        Expr expr = primary();

        // Parse the arbitrary number of parameters, such as fn(1)(2)(3).
        while (true)
        {
            // A set of arguments has been found.
            if (match(LEFT_PAREN))
            {
                // Parse the rest of the arguments.
                expr = finishCall(expr);
            }
            else if (match(DOT))
            {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            }
            else
            {
                // No more sets of arguments have been found and therefore terminate the loop.
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee)
    {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN))
        {
            do
            {
                // Limit the total number of arguments a function can have to ensure compatibility with Clox.
                if (arguments.size() >= 255)
                {
                    // We do not throw an error because the parser is not in an invalid state so does not
                    // need to go into panic mode and resynchronise.
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments. ");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary()
    {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal);

        if (match(SUPER))
        {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }

        if (match(THIS)) return new Expr.This(previous());
        if (match(IDENTIFIER)) return new Expr.Variable(previous());

        if (match(LEFT_PAREN))
        {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression. ");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression");
    }

    // Check that a particular token has a given type. If it does than it is consumed and returns true.
    // Otherwise, the token is left unmodified and false is returned.
    private boolean match(TokenType... types)
    {
        for (TokenType type : types)
        {
            if (check(type))
            {
                advance();
                return true;
            }
        }
        return false;
    }

    // Attempt to advance the parser, if this is not possible then report in an error => AST is malformed.
    private Token consume(TokenType type, String message)
    {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    // Check that a given token has a particular type. Return false if all tokens from the scanner are consumed by the
    // parser.
    private boolean check(TokenType type)
    {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // If not at the end of the scanners token list then increment the parser.
    // Otherwise, return the last consumed token.
    private Token advance()
    {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() { return peek().type == EOF; }
    // Returns the current token to be parsed.
    private Token peek() { return tokens.get(current); }
    // Returns the most recently parsed token.
    private Token previous () { return tokens.get(current - 1); }

    private ParseError error(Token token, String message)
    {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize()
    {
        advance();
        while (!isAtEnd())
        {
            if (previous().type == SEMICOLON) return;
            // Discard tokens until the parser thinks a statement boundary has been found.
            // We can assume that the parser has consumed all erroneous tokens from the scanner and reported the
            // errors and is therefore in a safe state to keep consuming tokens to report any more errors within the
            // scanner output.
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}