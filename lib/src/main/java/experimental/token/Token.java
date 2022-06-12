package experimental.token;

/** A representation of a token. */
public record Token(TokenType type,int line,String value)
{
    /** A dummy token. */
    public static final Token DUMMY = new Token(TokenType.ERR,0,null);
}