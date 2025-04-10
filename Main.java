
/* Scope example syntax
<keyword*> <identifier*> <(<parameters*>)*> {
    <body>
}

Scopes are defined by the curly braces.
Scopes can have a keyword which defines the type of scope, or names if it isn't a reserved keyword
Scopes can have parameters, which are passed to the scope when it is reached or called.
Scopes can have scoped variables, which are only accessible within the scope they are defined in, and are destroyed when the scope ends.
Scopes can access variables within higher scopes and the parameters passed to them.
Scopes can be nested, and can be called from within other scopes.

Reserved scope keywords: function, while, for, if, else, elif, finally, struct, implement

Reserved type keyword: int

*/

import java.util.ArrayList;
import java.util.Optional;

public class Main {

    private static final SimpleDebug logger = new SimpleDebug(2);

    private static final String[] reservedScopeKeywords = {"function", "while", "for", "if", "else", "elif", "finally", "struct", "implement"};
    private static final String[] reservedTypeKeywords = {"int"};
    private static final String[] reservedOperationKeywords = {"out"};

    public enum TokenType {
        KEYWORD, IDENTIFIER, PARAMETER_START, PARAMETER_END, END_STATEMENT, SCOPE, END_SCOPE, ERROR, EOF, TYPE, ASSIGNMENT, INT, OUT
    }

    static class Token {
        TokenType type;
        String indentifier;
        int value;

        Token(TokenType type, String identifier) { // for everything but numbers and errors
            this.type = type;
            this.indentifier = identifier;
        }

        Token(TokenType type, int value) { // for numbers
            this.type = type;
            this.value = value;
        }

        Token(TokenType type, String errorMsg, int index) { // for errors
            this.type = type;
            this.indentifier = errorMsg + " at point: " + index;
        }

        static final Token SCOPE = new Token(TokenType.SCOPE, "{");
        static final Token END_SCOPE = new Token(TokenType.END_SCOPE, "}");
        static final Token PARAMETER_START = new Token(TokenType.PARAMETER_START, "(");
        static final Token PARAMETER_END = new Token(TokenType.PARAMETER_END, ")");
        static final Token END_STATEMENT = new Token(TokenType.END_STATEMENT, ";");
        static final Token ASSIGNMENT = new Token(TokenType.ASSIGNMENT, "=");
        static final Token EOF = new Token(TokenType.EOF, "\0");
    }

    static class Node {

        enum NodeType {
            SCOPE, PARAMETER_SCOPE, ERROR, ASSIGNMENT
        }

        private final NodeType type;
        private final String[] identifier; // for variables, functions, structs, named scopes, etc. // hard limit at size 2
        private final Node[] children; // for scopes, this will be the statements and parameter scope // for statements, this will be the variable identifier and the value

        Node(NodeType type, String identifier, Node[] children) { // for variables, named scopes, loops, finally, if/elif/else
            this.type = type;
            this.identifier = new String[]{identifier};
            this.children = children;
        }

        Node(NodeType type, String keyword, String identifier, Node[] children) { // for functions, structs, implements
            this.type = type;
            this.identifier = new String[]{keyword, identifier};
            this.children = children;
        }

        public Node[] getChildren() { return children; }
        public String getIdentifier() { if(identifier.length == 1) return identifier[0]; else return identifier[1]; }
        public Optional<String> getKeyword() { if(identifier.length == 2) return Optional.of(identifier[0]); else return Optional.empty(); }
        public NodeType getType() { return type; }

        static final Node ERROR = new Node(NodeType.ERROR, "Error", null);
    }

    public static void main(String [] args) {
        SimpleTesting<String, Boolean> test = new SimpleTesting<String, Boolean>(2);

        String[] cases = {"for(3){ out(1); }", "foo{}", "foo(int i){ int a = i; }", "{}", "{{}}"};
        Boolean[] results = {true, true, true, true, true};

        // 1. test for loops
        // 2. test named scopes
        // 3. test function scopes
        // 4. test barebones scopes
        // 5. test nested scopes

        test.evaulateCases(cases, results, x -> evaluateScope(x));
    }

    private static Token[] lexScope(String scope) {
        char[] chars = scope.toCharArray();
        ArrayList<Token> tokens = new ArrayList<Token>();

        int characterIndex = 0;
        for(char c : chars) {
            if(c == '{') {
                tokens.add(Token.SCOPE);
            } else if(c == '}') {
                tokens.add(Token.END_SCOPE);
            } else if(c == '(') {
                tokens.add(Token.PARAMETER_START);
            } else if(c == ')') {
                tokens.add(Token.PARAMETER_END);
            } else if(c == ';') {
                tokens.add(Token.END_STATEMENT);
            } else if(Character.isWhitespace(c)) {
                continue;
            } else if(isValidIdentifier(c)) {
                // build a string, then check if its a reserved keyword
                StringBuilder sb = new StringBuilder();
                sb.append(c);
                while(characterIndex < chars.length - 1 && isValidIdentifier(chars[characterIndex + 1])) {
                    sb.append(chars[characterIndex + 1]);
                    characterIndex++;
                }
                String identifier = sb.toString();
                switch(identifier) {
                    case "function" -> tokens.add(new Token(TokenType.KEYWORD, identifier)); // contains parameter scope
                    case "while" -> tokens.add(new Token(TokenType.KEYWORD, identifier));    // contains parameter scope
                    case "for" -> tokens.add(new Token(TokenType.KEYWORD, identifier));      // contains parameter scope
                    case "if" -> tokens.add(new Token(TokenType.KEYWORD, identifier));       // contains parameter scope
                    case "else" -> tokens.add(new Token(TokenType.KEYWORD, identifier));
                    case "elif" -> tokens.add(new Token(TokenType.KEYWORD, identifier));     // contains parameter scope
                    case "finally" -> tokens.add(new Token(TokenType.KEYWORD, identifier));
                    case "struct" -> tokens.add(new Token(TokenType.KEYWORD, identifier));
                    case "implement" -> tokens.add(new Token(TokenType.KEYWORD, identifier));
                    case "int" -> tokens.add(new Token(TokenType.TYPE, identifier));
                    case "out" -> tokens.add(new Token(TokenType.OUT, identifier));          // contains parameter scope
                    // add more keywords here
                    default -> tokens.add(new Token(TokenType.IDENTIFIER, identifier));
                }
            } else if(isNumber(c)) {
                // build a number
                StringBuilder sb = new StringBuilder();
                sb.append(c);
                while(characterIndex < chars.length - 1 && Character.isDigit(chars[characterIndex + 1])) {
                    sb.append(chars[characterIndex + 1]);
                    characterIndex++;
                }
                tokens.add(new Token(TokenType.INT, Integer.parseInt(sb.toString())));
            } else if(c == '=') {
                tokens.add(Token.ASSIGNMENT);
            } else {
                tokens.add(new Token(TokenType.ERROR, "Invalid character: " + c, characterIndex));
            }
            characterIndex++;
        }

        return tokens.toArray(new Token[tokens.size()]);
    }

    private static boolean isValidIdentifier(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNumber(char c) {
        return Character.isDigit(c) || c == '-';
    }

    private static Node[] parseScope(Token[] tokens) {
        return null;
    }

    private static boolean evaluateScope(String scope) {
        Token[] tokens = lexScope(scope);
        for(Token token : tokens) {
            if(token.type == TokenType.ERROR) {
                logger.error(scope, 1);
                return false;
            }
        }

        Node[] topLevelNodes = parseScope(tokens);
        for(Node node : topLevelNodes) {
            if(node.getType() == Node.NodeType.PARAMETER_SCOPE) {
                logger.error("Parameter scopes can not be top level scopes", 1);
                return false;
            }
        }

        return run(topLevelNodes);
    }
}