package com.craftinginterpreters.lox;

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

    Expr parse()
    {
        try
        {
            return expression();
        }
        catch (ParseError error)
        {
            return null;
        }
    }

    // Parse an expression.
    private Expr expression() { return equality(); }

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

        return primary();
    }

    private Expr primary()
    {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal);

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