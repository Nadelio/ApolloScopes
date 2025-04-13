import java.util.*;
import java.util.regex.*;

public class Lexer {
    private static final SimpleDebug debugger = new SimpleDebug(Main.DEBUG_LEVEL);

    public enum TokenType {
        RESERVED_SCOPE_KEYWORD("function|for|struct|implement"),
        RESERVED_TYPE_KEYWORD("int"),
        RESERVED_OPERATOR_KEYWORD("out|inc|dec|jump"),
        IDENTIFIER("[a-zA-Z_][a-zA-Z0-9_]*"),
        LITERAL("\\d+"),
        SINGLE_LINE_COMMENT("//.*"),
        MULTI_LINE_COMMENT("/\\*.*?\\*/"),
        SYMBOL("[{}();,:=<>\\+\\-\\*/]"),
        WHITESPACE("\\s+");

        public final String pattern;

        TokenType(String pattern) {
            this.pattern = pattern;
        }
    }

    public static class Token {
        public final TokenType type;
        public final String value;

        public Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("Token(%s, '%s')", type.name(), value);
        }
    }

    public List<Token> tokenize(String input) {
        debugger.debug("Starting tokenization...", 1);
        List<Token> tokens = new ArrayList<>();
        StringBuilder regexBuilder = new StringBuilder();

        // Build the combined regex pattern for all token types
        for (TokenType type : TokenType.values()) {
            regexBuilder.append(String.format("(%s)|", type.pattern));
        }
        Pattern tokenPatterns = Pattern.compile(regexBuilder.substring(0, regexBuilder.length() - 1));
        Matcher matcher = tokenPatterns.matcher(input);

        while (matcher.find()) {
            for (int i = 0; i < TokenType.values().length; i++) {
                if (matcher.group(i + 1) != null) {
                    TokenType type = TokenType.values()[i];

                    // Ignore whitespace and comments
                    if (type == TokenType.WHITESPACE || type == TokenType.SINGLE_LINE_COMMENT || type == TokenType.MULTI_LINE_COMMENT) {
                        debugger.debug("Ignored token: " + type.name(), 3);
                        break;
                    }

                    // Add valid tokens to the list
                    Token token = new Token(type, matcher.group(i + 1));
                    tokens.add(token);
                    debugger.debug("Generated token: " + token, 2);
                    break;
                }
            }
        }

        debugger.debug("Tokenization completed. Total tokens: " + tokens.size(), 1);
        return tokens;
    }
}