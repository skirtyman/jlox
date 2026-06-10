package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner
{
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // Store the start of the current lexeme being scanned.
    private int start = 0;
    // This points to the character in the string that is currently being considered by the scanner.
    private int current = 0;
    // The line within the source file that is currently being scanned. This is used for error detection / reporting.
    private int line = 1;

    Scanner(String source)
    {
        this.source = source;
    }

    List<Token> scanTokens()
    {
        while (!isAtEnd())
        {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken()
    {
        char c = advance();
        switch (c)
        {
            case ')': addToken(LEFT_PAREN); break;
            case '(': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            case '/':
                if (match('/'))
                {
                    // A comments goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                }
                else
                {
                    addToken(SLASH);
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;
            case '\n':
                line++;
                break;
            default:
                Lox.error(line, "Unexpected character. ");
                break;
        }
    }

    // Check if the next character in the source string is a certain character.
    // If it is then consume it and move on. This allows to character long lexemes to be scanned in one pass.
    private boolean match(char expected)
    {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    // Lookahead function, that allows the next character to be observed but not consumed by the scanner.
    // This is used to scan longer lexemes such as comments and is the general principle for scanning
    // variables, literals, etc.
    private char peek()
    {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    // Check if the scanner has consumed the entire source string.
    private boolean isAtEnd() { return current >= source.length(); }

    // Advance the scanner through the source string and get the scanned character.
    private char advance() { return source.charAt(current++); }

    // Add token to the scanned token list.
    private void addToken(TokenType type) { addToken(type, null); }
    private void addToken(TokenType type, Object literal)
    {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
