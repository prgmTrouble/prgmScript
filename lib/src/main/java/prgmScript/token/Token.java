package prgmScript.token;

public record Token(TokenType type,int line,String value)
{
    public static final Token DUMMY = new Token(TokenType.ERR,0,null);
}