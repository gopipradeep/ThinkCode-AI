import java.io.IOException;
import java.io.StringReader;

// Enum for Token Types (required for the parser)
enum TokenType {
    NUMBER, PLUS, MINUS, MULTIPLY, DIVIDE, LPAREN, RPAREN, EOF
}

// Token class (required for the parser)
class Token {
    TokenType type;
    String value;

    public Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Token(" + type + ", \"" + value + "\")";
    }
}

// AST Node classes
interface ASTNode {}

class NumberNode implements ASTNode {
    String value;
    public NumberNode(String value) { this.value = value; }
}

class BinaryOpNode implements ASTNode {
    ASTNode left, right;
    Token operator;
    public BinaryOpNode(ASTNode left, Token operator, ASTNode right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}

// Lexer class (a simple version needed for the parser to work)
class Lexer {
    private StringReader reader;
    private int currentChar;

    public Lexer(String sourceCode) throws IOException {
        this.reader = new StringReader(sourceCode);
        advance();
    }

    private void advance() throws IOException {
        currentChar = reader.read();
    }

    public Token getNextToken() throws IOException {
        while (Character.isWhitespace(currentChar)) {
            advance();
        }

        if (currentChar == -1) {
            return new Token(TokenType.EOF, "");
        }

        if (Character.isDigit(currentChar)) {
            StringBuilder sb = new StringBuilder();
            while (Character.isDigit(currentChar)) {
                sb.append((char) currentChar);
                advance();
            }
            return new Token(TokenType.NUMBER, sb.toString());
        }

        switch (currentChar) {
            case '+': advance(); return new Token(TokenType.PLUS, "+");
            case '-': advance(); return new Token(TokenType.MINUS, "-");
            case '*': advance(); return new Token(TokenType.MULTIPLY, "*");
            case '/': advance(); return new Token(TokenType.DIVIDE, "/");
            case '(': advance(); return new Token(TokenType.LPAREN, "(");
            case ')': advance(); return new Token(TokenType.RPAREN, ")");
        }

        throw new RuntimeException("Unrecognized character: " + (char) currentChar);
    }
}

public class Parser {
    private Lexer lexer;
    private Token currentToken;

    public Parser(Lexer lexer) throws IOException {
        this.lexer = lexer;
        this.currentToken = lexer.getNextToken();
    }

    private void consume(TokenType expectedType) throws IOException {
        if (currentToken.type == expectedType) {
            currentToken = lexer.getNextToken();
        } else {
            throw new RuntimeException("Syntax error: Expected " + expectedType + ", got " + currentToken.type);
        }
    }

    public ASTNode parse() throws IOException {
        return parseExpression();
    }

    private ASTNode parseExpression() throws IOException {
        ASTNode node = parseTerm();
        while (currentToken.type == TokenType.PLUS || currentToken.type == TokenType.MINUS) {
            Token op = currentToken;
            consume(currentToken.type);
            node = new BinaryOpNode(node, op, parseTerm());
        }
        return node;
    }

    private ASTNode parseTerm() throws IOException {
        ASTNode node = parseFactor();
        while (currentToken.type == TokenType.MULTIPLY || currentoken.type == TokenType.DIVIDE) {
            Token op = currentToken;
            consume(currentToken.type);
            node = new BinaryOpNode(node, op, parseFactor());
        }
        return node;
    }

    private ASTNode parseFactor() throws IOException {
        Token token = currentToken;
        if (token.type == TokenType.NUMBER) {
            consume(TokenType.NUMBER);
            return new NumberNode(token.value);
        } else if (token.type == TokenType.LPAREN) {
            consume(TokenType.LPAREN);
            ASTNode node = parseExpression();
            consume(TokenType.RPAREN);
            return node;
        } else {
            throw new RuntimeException("Invalid syntax");
        }
    }
}