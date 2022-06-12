package experimental.token;

import experimental.util.ContainerUtil;

import java.util.HashMap;
import java.util.Map;

/** An enumeration of different types of tokens. */
public enum TokenType
{
    EOF,ERR,
    IMPORT("import"),
    
    CONST("const"),
    VOID("void"),
    BOOL("bool"),
    INT("int"),
    FLOAT("float"),
    STR("str"),
    FUNC("func"),
    STRUCT("struct"),
    TYPESET("typeset"),
    ID,
    
    IF("if"),ELSE("else"),
    DO("do"),WHILE("while"),
    FOR("for"),
    CONTINUE("continue"), //TODO 'switch' '(' Expr ')' '{' { 'case' '(' Expr ')' '{' {Statement} ['continue' ';'] } '}'
    BREAK("break"),
    RETURN("return"),
    THROW("throw"),
    
    TRUE("true"),FALSE("false"),
    LIT_INT,LIT_FLOAT,LIT_STR,
    
    LPAREN("("),RPAREN(")"),
    LBRACKET("["),RBRACKET("]"),
    LBRACE("{"),RBRACE("}"),
    
    SEMICOLON(";"),
    COMMA(","),
    DOT("."),
    
    NOT("!"),BITNOT("~"),
    CONDITION("?"),COLON(":"),
    INC("++"),DEC("--"),
    ADD("+"),SUB("-"),MUL("*"),DIV("/"),MOD("%"),
    BITAND("&"),BITOR("|"),BITXOR("^"),
    GT(">"),LT("<"),
    LSH("<<"),RSH(">>"),LRSH(">>>"),
    LEQ("<="),GEQ(">="),
    AND("&&"),OR("||"),
    EQ("=="),NEQ("!="),
    ASSIGN("="),
    ADDEQ("+="),SUBEQ("-="),MULEQ("*="),DIVEQ("/="),MODEQ("%="),
    ANDEQ("&="),OREQ("|="),XOREQ("^="),
    LSHEQ("<<="),RSHEQ(">>="),LRSHEQ(">>>=");
    
    private final String name;
    TokenType(final String name) {this.name = name;}
    TokenType() {this(null);}
    
    @Override
    public String toString()
    {
        return switch(this)
        {
            case EOF -> "end-of-file";
            case ERR -> "error-token";
            case ID -> "identifier";
            case LIT_INT -> "integer";
            case LIT_FLOAT -> "floating-point";
            case LIT_STR -> "string";
            case LPAREN,RPAREN,LBRACE,
                 RBRACE,LBRACKET,RBRACKET,
                 SEMICOLON,COMMA,DOT,
                 NOT,BITNOT,CONDITION,
                 COLON,INC,DEC,ADD,SUB,
                 MUL,DIV,MOD,BITAND,
                 BITOR,BITXOR,GT,LT,
                 LSH,RSH,LRSH,LEQ,GEQ,
                 AND,OR,EQ,NEQ,ASSIGN,
                 ADDEQ,SUBEQ,MULEQ,
                 DIVEQ,MODEQ,ANDEQ,
                 OREQ,XOREQ,LSHEQ,
                 RSHEQ,LRSHEQ
                 -> '\''+name+'\'';
            default -> name;
        };
    }
    /** @return {@code true} if this token represents a keyword. */
    public boolean isKeyword() {return name != null && ordinal() <= FALSE.ordinal() && ordinal() >= IMPORT.ordinal();}
    
    /** A map containing all keywords. */
    public static final Map<String,TokenType> Keywords;
    static
    {
        final Map<String,TokenType> m = new HashMap<>(values().length - 5);
        for(final TokenType t : values()) if(t.isKeyword()) m.put(t.name,t);
        Keywords = ContainerUtil.makeImmutable(m);
    }
}