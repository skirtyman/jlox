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

    // Store a hashmap of the reserved keywords in Lox.
    private static final Map<String, TokenType> keywords;

    static
    {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
    }

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
            case '"': string(); break;
            default:
                if (isDigit(c)) number();
                else if (isAlpha(c)) identifier();
                else Lox.error(line, "Unexpected character. ");
                break;
        }
    }

    // Scan an identifier within the source string. This can either be a reserved keyword or user-defined variable.
    // Each case can be determined using a hashmap which can be used to detect if a user has used a reserved keyword
    // as an identifier.
    private void identifier()
    {
        // Consume the names of the keywords / user-defined variables.
        while (isAlphaNumeric(peek())) advance();

        // Extract the text from the source string.
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);

        // If the extracted text from the source string does not match a reserved keywords then create a token of type
        // IDENTIFIER (representing a user-defined variable), otherwise create a token of the reserved keyword.
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    // Scans a number according to Lox's grammar. This is in the same way as a string.
    private void number()
    {
        // Advance the current pointer in the source string whilst we have found digits.
        while (isDigit(peek())) advance();

        // Look for the fractional part of the number.
        if (peek() == '.' && isDigit(peekNext()))
        {
            // Consume the "."
            advance();
            // Consume the rest of the digits in the fractional part of the number.
            while (isDigit(peek())) advance();
        }

        // Add the token to the list of scanned tokens.
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    // Scans a string in the source and returns a token, according to Lox's grammar.
    private void string()
    {
        while (peek() != '"' && !isAtEnd())
        {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd())
        {
            Lox.error(line, "Unterminated string. ");
            return;
        }

        // Consuming the closing ".
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
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

    private char peekNext()
    {
        if (current + 1 >= source.length()) return '\0';
        // Peek at the character 2 ahead of the scanner. This allows to peek at characters after others, such as
        // in the case of numerical values with a fractional part.
        return source.charAt(current + 1);
    }

    private boolean isAlphaNumeric(char c) { return isAlpha(c) || isDigit(c); }
    private boolean isAlpha(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
    private boolean isDigit(char c) { return c >= '0' && c <= '9'; }

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
