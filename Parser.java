import java.util.*;

public class Parser {
    private static final SimpleDebug debugger = new SimpleDebug(Main.DEBUG_LEVEL);

    public static class Node {
        public final String type;
        public final String value;
        public final List<Node> children;

        public Node(String type, String value) {
            this.type = type;
            this.value = value;
            this.children = new ArrayList<>();
        }

        public void addChild(Node child) {
            children.add(child);
        }

        @Override
        public String toString() {
            return String.format("Node(type='%s', value='%s', children=%d)", type, value, children.size());
        }
    }

    private final List<Lexer.Token> tokens;
    private int position = 0;

    public Parser(List<Lexer.Token> tokens) {
        this.tokens = tokens;
    }

    private Lexer.Token peek() {
        return position < tokens.size() ? tokens.get(position) : null;
    }

    private Lexer.Token consume() {
        return position < tokens.size() ? tokens.get(position++) : null;
    }

    public Node[] parse() throws Exception {
        debugger.debug("Starting parsing process...", 1);
        List<Node> nodes = new ArrayList<>();
        while (peek() != null) {
            Lexer.Token token = peek();
            if (token.type == Lexer.TokenType.RESERVED_SCOPE_KEYWORD || token.type == Lexer.TokenType.IDENTIFIER) {
                // Parse as a scope
                Node node = parseScope();
                if (node != null) {
                    nodes.add(node);
                    debugger.debug("Added node: " + node, 2);
                }
            } else {
                // Parse as a statement
                Node node = parseStatementOrScope();
                if (node != null) {
                    nodes.add(node);
                    debugger.debug("Added top-level statement node: " + node, 2);
                }
            }
        }
        debugger.debug("Parsing process completed. Total nodes: " + nodes.size(), 1);
        return nodes.toArray(new Node[0]);
    }

    private Node parseScope() throws Exception {
        Lexer.Token token = consume();
        debugger.debug("Parsing scope: " + token.value, 2);

        if (token.type == Lexer.TokenType.RESERVED_SCOPE_KEYWORD) {
            Node scopeNode = new Node("Scope", token.value);

            // Special handling for "for" loops
            if (token.value.equals("for")) {
                if (peek() != null && peek().value.equals("(")) {
                    consume(); // Consume '('
                    Node loopCondition = parseExpression(); // Parse the loop condition
                    scopeNode.addChild(new Node("Condition", loopCondition.value));
                    if (peek() != null && peek().value.equals(")")) {
                        consume(); // Consume ')'
                    } else {
                        debugger.fatal("Expected ')' to close 'for' loop condition, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                    }
                } else {
                    debugger.fatal("Expected '(' to start 'for' loop condition, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            } else {
                // Expect an IDENTIFIER after the RESERVED_SCOPE_KEYWORD (for other scopes)
                if (peek() != null && peek().type == Lexer.TokenType.IDENTIFIER) {
                    Lexer.Token identifierToken = consume(); // Consume IDENTIFIER
                    scopeNode.addChild(new Node("Identifier", identifierToken.value));
                    debugger.debug("Parsed identifier: " + identifierToken.value, 2);
                } else if (!token.value.equals("for")) {
                    debugger.fatal("Expected identifier after scope keyword, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            }

            // Handle struct member declarations
            if (token.value.equals("struct")) {
                if (peek() != null && peek().value.equals("{")) {
                    consume(); // Consume '{'
                    while (peek() != null && !peek().value.equals("}")) {
                        Lexer.Token typeToken = consume(); // Consume type keyword
                        if (typeToken == null || typeToken.type != Lexer.TokenType.RESERVED_TYPE_KEYWORD) {
                            debugger.fatal("Expected a type keyword in struct, but found: " + (typeToken != null ? typeToken.value : "null"), 1);
                        }
                        Lexer.Token identifierToken = consume(); // Consume identifier
                        if (identifierToken == null || identifierToken.type != Lexer.TokenType.IDENTIFIER) {
                            debugger.fatal("Expected an identifier in struct, but found: " + (identifierToken != null ? identifierToken.value : "null"), 1);
                        }
                        if (peek() != null && peek().value.equals(";")) {
                            consume(); // Consume ';'
                            Node memberNode = new Node("Member", identifierToken.value);
                            memberNode.addChild(new Node("Type", typeToken.value));
                            scopeNode.addChild(memberNode);
                            debugger.debug("Parsed struct member: " + identifierToken.value + " with type: " + typeToken.value, 2);
                        } else {
                            debugger.fatal("Expected ';' after struct member declaration, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                        }
                    }
                    if (peek() != null && peek().value.equals("}")) {
                        consume(); // Consume '}'
                    } else {
                        debugger.fatal("Expected '}' to close struct, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                    }
                } else {
                    debugger.fatal("Expected '{' to start struct body, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
                return scopeNode;
            }

            // Handle implement scope
            if (token.value.equals("implement")) {
                if (peek() != null && peek().value.equals("{")) {
                    consume(); // Consume '{'
                    while (peek() != null && !peek().value.equals("}")) {
                        if (peek().type == Lexer.TokenType.RESERVED_SCOPE_KEYWORD && peek().value.equals("function")) {
                            scopeNode.addChild(parseScope()); // Parse the function scope
                        } else {
                            debugger.fatal("Expected 'function' inside implement scope, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                        }
                    }
                    if (peek() != null && peek().value.equals("}")) {
                        consume(); // Consume '}'
                    } else {
                        debugger.fatal("Expected '}' to close implement scope, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                    }
                } else {
                    debugger.fatal("Expected '{' to start implement body, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
                return scopeNode;
            }

            // Check for parameters
            if (peek() != null && peek().value.equals("(")) {
                consume(); // Consume '('
                scopeNode.addChild(parseParameters());
            }

            // Check for return type
            if (peek() != null && peek().value.equals(":")) {
                consume(); // Consume ':'
                Lexer.Token returnTypeToken = consume(); // Consume return type
                scopeNode.addChild(new Node("ReturnType", returnTypeToken.value));
                debugger.debug("Parsed return type: " + returnTypeToken.value, 2);
            }

            // Parse the body of the scope
            if (peek() != null && peek().value.equals("{")) {
                consume(); // Consume '{'
                while (peek() != null && !peek().value.equals("}")) {
                    scopeNode.addChild(parseStatementOrScope());
                }
                if (peek() != null && peek().value.equals("}")) {
                    consume(); // Consume '}'
                } else {
                    debugger.fatal("Expected '}' but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            } else {
                debugger.fatal("Expected '{' to start scope body, but found: " + (peek() != null ? peek().value : "EOF"), 1);
            }

            return scopeNode;
        } else if (token.type == Lexer.TokenType.IDENTIFIER) {
            Node namedScopeNode = new Node("NamedScope", token.value);
            if (peek() != null && peek().value.equals("{")) {
                consume(); // Consume '{'
                while (peek() != null && !peek().value.equals("}")) {
                    namedScopeNode.addChild(parseStatementOrScope());
                }
                if (peek() != null && peek().value.equals("}")) {
                    consume(); // Consume '}'
                } else {
                    debugger.fatal("Expected '}' to close named scope, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            }
            return namedScopeNode;
        }
        debugger.fatal("Unexpected token: " + token, 1);
        return null; // This line will never be reached due to fatal()
    }

    private Node parseParameters() throws Exception {
        Node parametersNode = new Node("Parameters", "");
        boolean firstParameter = true; // Track if it's the first parameter
        while (!peek().value.equals(")")) {
            if (!firstParameter) {
                if (peek().value.equals(",")) {
                    consume(); // Consume ',' for subsequent parameters
                } else {
                    debugger.fatal("Expected ',' between parameters, but found: " + peek().value, 1);
                }
            }

            // Ensure the next token is a type keyword
            Lexer.Token typeToken = peek();
            if (typeToken == null || typeToken.type != Lexer.TokenType.RESERVED_TYPE_KEYWORD) {
                debugger.fatal("Expected a type keyword, but found: " + (typeToken != null ? typeToken.value : "null"), 1);
            }
            consume(); // Consume the type keyword

            // Ensure the next token is an identifier
            Lexer.Token identifierToken = peek();
            if (identifierToken == null || identifierToken.type != Lexer.TokenType.IDENTIFIER) {
                debugger.fatal("Expected an identifier after type keyword, but found: " + (identifierToken != null ? identifierToken.value : "null"), 1);
            }
            consume(); // Consume the identifier

            // Create a parameter node with a child node for the type
            Node parameterNode = new Node("Parameter", identifierToken.value);
            parameterNode.addChild(new Node("Type", typeToken.value));
            parametersNode.addChild(parameterNode);

            debugger.debug("Parsed parameter: " + identifierToken.value + " with type: " + typeToken.value, 2);

            firstParameter = false; // After the first parameter, expect commas
        }
        consume(); // Consume ')'
        return parametersNode;
    }

    private Node parseStatementOrScope() throws Exception {
        Lexer.Token token = peek();
        debugger.debug("Parsing statement or scope: " + (token != null ? token.value : "EOF"), 2);

        // Handle return statements
        if (token != null && token.type == Lexer.TokenType.IDENTIFIER && token.value.equals("return")) {
            consume(); // Consume 'return'
            Node returnNode = new Node("ReturnStatement", "return");

            // Check if there is an expression or just a semicolon
            if (peek() != null && !peek().value.equals(";")) {
                returnNode.addChild(parseExpression()); // Parse the return value
            }

            if (peek() != null && peek().value.equals(";")) {
                consume(); // Consume ';'
                debugger.debug("Parsed return statement.", 2);
                return returnNode;
            } else {
                throw new Exception("[ FATAL: Expected ';' after return statement, but found: " + (peek() != null ? peek().value : "EOF") + " ]");
            }
        }

        // Handle variable assignments
        if (token != null && token.type == Lexer.TokenType.IDENTIFIER) {
            Lexer.Token identifierToken = consume(); // Consume identifier
            if (peek() != null && peek().value.equals("=")) {
                consume(); // Consume '='
                Node assignmentNode = new Node("Assignment", identifierToken.value);
                assignmentNode.addChild(parseExpression()); // Parse the assigned value
                if (peek() != null && peek().value.equals(";")) {
                    consume(); // Consume ';'
                    debugger.debug("Parsed variable assignment: " + identifierToken.value, 2);
                    return assignmentNode;
                } else {
                    throw new Exception("[ FATAL: Expected ';' after variable assignment, but found: " + (peek() != null ? peek().value : "EOF") + " ]");
                }
            } else if (peek() != null && peek().value.equals("{")) {
                // Handle named scopes
                consume(); // Consume '{'
                Node namedScopeNode = new Node("NamedScope", identifierToken.value);
                while (peek() != null && !peek().value.equals("}")) {
                    namedScopeNode.addChild(parseStatementOrScope());
                }
                if (peek() != null && peek().value.equals("}")) {
                    consume(); // Consume '}'
                    debugger.debug("Parsed named scope: " + identifierToken.value, 2);
                    return namedScopeNode;
                } else {
                    debugger.fatal("Expected '}' to close named scope, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            } else {
                debugger.fatal("Expected '{' after named scope identifier, but found: " + (peek() != null ? peek().value : "EOF"), 1);
            }
        }

        // Handle unnamed scopes
        if (token != null && token.value.equals("{")) {
            consume(); // Consume '{'
            Node unnamedScopeNode = new Node("UnnamedScope", "");
            while (peek() != null && !peek().value.equals("}")) {
                unnamedScopeNode.addChild(parseStatementOrScope());
            }
            if (peek() != null && peek().value.equals("}")) {
                consume(); // Consume '}'
                debugger.debug("Parsed unnamed scope.", 2);
                return unnamedScopeNode;
            } else {
                debugger.fatal("Expected '}' to close unnamed scope, but found: " + (peek() != null ? peek().value : "EOF"), 1);
            }
        }

        // Handle reserved scope keywords (function, struct, implement, for)
        if (token != null && token.type == Lexer.TokenType.RESERVED_SCOPE_KEYWORD) {
            return parseScope(); // Delegate to parseScope for handling these constructs
        }

        // Handle variable declarations
        if (token != null && token.type == Lexer.TokenType.RESERVED_TYPE_KEYWORD) {
            Lexer.Token typeToken = consume(); // Consume type keyword
            Lexer.Token identifierToken = consume(); // Consume identifier
            if (identifierToken == null || identifierToken.type != Lexer.TokenType.IDENTIFIER) {
                debugger.fatal("Expected identifier after type keyword, but found: " + (identifierToken != null ? identifierToken.value : "EOF"), 1);
            }
            if (peek() != null && peek().value.equals("=")) {
                consume(); // Consume '='
                Node variableNode = new Node("VariableDeclaration", identifierToken.value);
                variableNode.addChild(new Node("Type", typeToken.value)); // Add Type node
                variableNode.addChild(parseExpression()); // Parse the expression
                if (peek() != null && peek().value.equals(";")) {
                    consume(); // Consume ';'
                    debugger.debug("Parsed variable declaration: " + identifierToken.value + " with type: " + typeToken.value, 2);
                    return variableNode;
                } else {
                    debugger.fatal("Expected ';' after variable declaration, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            } else {
                debugger.fatal("Expected '=' after identifier in variable declaration, but found: " + (peek() != null ? peek().value : "EOF"), 1);
            }
        }

        // Handle return statements
        if (token != null && token.type == Lexer.TokenType.IDENTIFIER && token.value.equals("return")) {
            consume(); // Consume 'return'
            Node returnNode = new Node("ReturnStatement", "return");

            // Check if there is an expression or just a semicolon
            if (peek() != null && !peek().value.equals(";")) {
                returnNode.addChild(parseExpression()); // Parse the return value
            }

            if (peek() != null && peek().value.equals(";")) {
                consume(); // Consume ';'
                debugger.debug("Parsed return statement.", 2);
                return returnNode;
            } else {
                debugger.fatal("Expected ';' after return statement, but found: " + (peek() != null ? peek().value : "EOF"), 1);
            }
        }

        // Handle operator keywords
        if (token != null && token.type == Lexer.TokenType.RESERVED_OPERATOR_KEYWORD) {
            Lexer.Token operatorToken = consume(); // Consume operator keyword
            Node operatorNode = new Node("Operator", operatorToken.value);
            if (peek() != null && peek().value.equals("(")) {
                consume(); // Consume '('
                while (peek() != null && !peek().value.equals(")")) {
                    operatorNode.addChild(parseExpression()); // Parse arguments
                    if (peek() != null && peek().value.equals(",")) {
                        consume(); // Consume ',' between arguments
                    }
                }
                if (peek() != null && peek().value.equals(")")) {
                    consume(); // Consume ')'
                } else {
                    debugger.fatal("Expected ')' after operator arguments, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
                if (peek() != null && peek().value.equals(";")) {
                    consume(); // Consume ';'
                    debugger.debug("Parsed operator statement: " + operatorToken.value, 2);
                    return operatorNode;
                } else {
                    debugger.fatal("Expected ';' after operator statement, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            } else {
                debugger.fatal("Expected '(' after operator keyword, but found: " + (peek() != null ? peek().value : "EOF"), 1);
            }
        }

        // Handle function calls or other statements
        if (token != null && token.type == Lexer.TokenType.IDENTIFIER) {
            Lexer.Token identifierToken = consume(); // Consume identifier
            Node functionCallNode = new Node("FunctionCall", identifierToken.value);
            if (peek() != null && peek().value.equals("(")) {
                consume(); // Consume '('
                while (peek() != null && !peek().value.equals(")")) {
                    functionCallNode.addChild(parseExpression());
                    if (peek() != null && peek().value.equals(",")) {
                        consume(); // Consume ','
                    }
                }
                if (peek() != null && peek().value.equals(")")) {
                    consume(); // Consume ')'
                } else {
                    debugger.fatal("Expected ')' after function call, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
                if (peek() != null && peek().value.equals(";")) {
                    consume(); // Consume ';'
                    debugger.debug("Parsed function call: " + identifierToken.value, 2);
                    return functionCallNode;
                } else {
                    debugger.fatal("Expected ';' after function call, but found: " + (peek() != null ? peek().value : "EOF"), 1);
                }
            }
        }

        debugger.fatal("Unexpected token: " + (token != null ? token.value : "EOF"), 1);
        return null; // This line will never be reached due to fatal()
    }

    private Node parseExpression() throws Exception {
        Node left = parsePrimaryExpression(); // Parse the left-hand side of the expression

        // Handle binary operators
        while (peek() != null && isBinaryOperator(peek().value)) {
            Lexer.Token operatorToken = consume(); // Consume the operator
            Node operatorNode = new Node("Operator", operatorToken.value);
            operatorNode.addChild(left); // Add the left-hand side as a child
            operatorNode.addChild(parsePrimaryExpression()); // Parse the right-hand side and add it as a child
            left = operatorNode; // The operator node becomes the new left-hand side
        }

        return left;
    }

    private Node parsePrimaryExpression() throws Exception {
        Lexer.Token token = peek();

        // Handle expressions enclosed in parentheses
        if (token != null && token.value.equals("(")) {
            consume(); // Consume '('
            Node expressionNode = parseExpression(); // Parse the expression inside the parentheses
            if (peek() != null && peek().value.equals(")")) {
                consume(); // Consume ')'
                return expressionNode; // Return the parsed expression
            } else {
                throw new Exception("[ FATAL: Expected ')' to close parenthesis, but found: " + (peek() != null ? peek().value : "EOF") + " ]");
            }
        }

        // Handle identifiers and literals
        if (token != null && (token.type == Lexer.TokenType.IDENTIFIER || token.type == Lexer.TokenType.LITERAL)) {
            consume(); // Consume the token
            debugger.debug("Parsed primary expression: " + token.value, 2);
            return new Node("Expression", token.value);
        }
debugger.fatal("Expected primary expression, but found: " + (token != null ? token.value : "EOF"), 1);
return null; // This line will never be reached due to fatal()
    }

    private boolean isBinaryOperator(String value) {
        return "+-*/".contains(value); // Supported binary operators
    }
}