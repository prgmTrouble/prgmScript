package prgmScript.token;

import prgmScript.util.ErrReporter;
import prgmScript.util.Reader;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import static prgmScript.token.Token.DUMMY;
import static prgmScript.token.TokenType.*;

/**
 * This class contains functions which convert text into prgmScript tokens.
 * <br>
 * Tokens:
 * <pre>
 *     LiteralBool := ('true'|'false')
 *      LiteralNum := (Integer|Float)
 *      LiteralStr := '"' {StrChr} '"'
 *            Name := NameChr {NameChr}
 *
 *         Integer := '0'('x'|'X') HexDigits
 *                  | '0'('b'|'B') BinDigits
 *                  | DecDigits
 *           Float := HexSig [HexExp]
 *                  | DecSig [DecExp]
 *
 *       BinDigits := Bin {Bin}
 *       HexDigits := Hex {Hex}
 *       DecDigits := Dec {Dec}
 *             Bin := ('0'|'1')
 *             Dec := (Bin|'2'|'3'|'4'|'5'|'6'|'7'|'8'|'9')
 *             Hex := (Dec|'A'|'B'|'C'|'D'|'E'|'F'|'a'|'b'|'c'|'d'|'e'|'f')
 *          HexSig := '0'('x'|'X')(['.']|([HexDigits] '.' HexDigits))
 *          DecSig := DecDigits ['.'] {Dec}
 *                  | {Dec} '.' DecDigits
 *          HexExp := ('p'|'P')['+'|'-'] DecDigits
 *          DecExp := ('e'|'E')['+'|'-'] DecDigits
 *
 *          StrChr := (Escape|NotQuoteOrEsc)
 *          Escape := '\'(('u' Hex Hex Hex Hex)|'f'|'n'|'r'|'t'|'0'|'1'|'2'|'3'|'4'|'5'|'6'|'7')
 *   NotQuoteOrEsc := any unicode character except 'CR', 'LF', '"', or '\'
 *
 *         NameChr := 'a'|'b'|'c'|'d'|'e'|'f'|'g'|'h'|'i'|'j'|'k'|'l'|'m'|'n'|'o'|'p'|'q'|'r'|'s'|'t'|'u'|'v'|'w'|'x'|'y'|'z'
 *                  | 'A'|'B'|'C'|'D'|'E'|'F'|'G'|'H'|'I'|'J'|'K'|'L'|'M'|'N'|'O'|'P'|'Q'|'R'|'S'|'T'|'U'|'V'|'W'|'X'|'Y'|'Z'
 *                  | '0'|'1'|'2'|'3'|'4'|'5'|'6'|'7'|'8'|'9'|'$'|'_'
 * </pre>
 *
 * @see prgmScript.Script
 */
public final class Tokenize
{
    private static String word(final Reader r,int first) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        while(('a' <= first && first <= 'z') ||
              ('A' <= first && first <= 'Z') ||
              ('0' <= first && first <= '9') ||
               first == '_' || first == '$')
        {
            sb.append(Character.toString(first));
            first = r.read();
        }
        r.unread(first);
        return sb.toString();
    }
    private enum Base {hex,dec,bin}
    private static boolean isNumeric(final int c,final Base b)
    {
        return switch(b)
        {
            case hex -> ('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F');
            case bin -> c == '0' || c == '1';
            default  -> '0' <= c && c <= '9'; // dec
        };
    }
    private static int integer(final Reader r,final Base b,final StringBuilder sb) throws IOException
    {
        int c = r.read();
        while(isNumeric(c,b))
        {
            sb.append(Character.toString(c));
            c = r.read();
        }
        return c;
    }
    private static String frac(final Reader r,final ErrReporter reporter) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        int c = integer(r,Base.dec,sb);
        if(c == 'e' || c == 'E')
        {
            sb.append(Character.toString(c));
            c = r.read();
            if(c == '+' || c == '-')
            {
                sb.append(Character.toString(c));
                c = r.read();
            }
            if(isNumeric(c,Base.dec))
            {
                sb.append(Character.toString(c));
                r.unread(integer(r,Base.dec,sb));
                return sb.toString();
            }
            r.unread(c);
            reporter.report
            (
                r.line(),
                "Invalid character in float literal: '"+Character.toString(c)+'\''
            );
            return null;
        }
        r.unread(c);
        return sb.toString();
    }
    private static String hexExp(final Reader r,final ErrReporter reporter,int start) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        if(start == 'p' || start == 'P')
        {
            sb.append(Character.toString(start));
            if((start = r.read()) == '-' || start == '+')
            {
                sb.append(Character.toString(start));
                start = r.read();
            }
            if(isNumeric(start,Base.dec))
            {
                r.unread(integer(r,Base.dec,sb.append(Character.toString(start))));
                return sb.toString();
            }
            reporter.report(r.line(),"Invalid character in exponent of hexadecimal float");
            return null;
        }
        reporter.report(r.line(),"Missing exponent part of hexadecimal float");
        return null;
    }
    private static String hexFrac(final Reader r,final ErrReporter reporter,boolean hasDigit) throws IOException
    {
        int c = r.read();
        final StringBuilder sb = new StringBuilder();
        if(isNumeric(c,Base.hex))
        {
            hasDigit = true;
            c = integer(r,Base.hex,sb.append(Character.toString(c)));
        }
        if(!hasDigit)
        {
            reporter.report(r.line(),"Missing digits in fractional part of hexadecimal float");
            return null;
        }
        return sb.append(hexExp(r,reporter,c)).toString();
    }
    private static Token number(final Reader r,final ErrReporter reporter,final Base b) throws IOException
    {
        int c = r.read();
        final boolean hasDigit = isNumeric(c,b);
        final StringBuilder sb = new StringBuilder();
        TokenType tt = LIT_INT;
        if(hasDigit)
        {
            sb.append(Character.toString(c));
            c = integer(r,b,sb);
        }
        switch(b)
        {
            case hex ->
            {
                if(c == '.')
                {
                    final String s = hexFrac(r,reporter,hasDigit);
                    if(s == null) return null;
                    sb.append('.')
                      .append(s);
                    tt = LIT_FLOAT;
                }
                else if(hasDigit && (c == 'p' || c == 'P'))
                {
                    final String s = hexExp(r,reporter,c);
                    if(s == null) return null;
                    sb.append(s);
                    tt = LIT_FLOAT;
                }
                else r.unread(c);
            }
            case dec ->
            {
                if(c == '.')
                {
                    final String f = frac(r,reporter);
                    if(f == null) return null;
                    sb.append('.')
                      .append(f);
                    tt = LIT_FLOAT;
                }
                else if(c == 'e' || c == 'E')
                {
                    r.unread(c);
                    final String f = frac(r,reporter);
                    if(f == null) return null;
                    sb.append(f);
                    tt = LIT_FLOAT;
                }
                else r.unread(c);
            }
            default ->
            {
                if(!hasDigit)
                {
                    reporter.report(r.line(),"Missing digits while parsing number");
                    return null;
                }
                r.unread(c);
            }
        }
        return new Token(tt,-1,sb.toString());
    }
    private static String quote(final ErrReporter reporter,final Reader r) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        while(true)
        {
            final int c = r.read();
            if(escaped)
            {
                final int esc = switch(c)
                {
                    case 'u' ->
                    {
                        // Parse unicode escape sequence (\\u[a-fA-F0-9]{4}).
                        int unicode = 0;
                        for(int i = 0;i < 4;++i)
                        {
                            final int c1 = r.read();
                            final byte x = (byte)switch(c1)
                            {
                                case '0','1','2','3','4',
                                     '5','6','7','8','9'     -> '0';
                                case 'a','b','c','d','e','f' -> 'a'-10;
                                case 'A','B','C','D','E','F' -> 'A'-10;
                                default                      ->
                                {
                                    reporter.report
                                    (
                                        r.line(),
                                        "Unexpected "+
                                        (
                                            c1 != -1
                                                ? "character '"+Character.toString(c1)+'\''
                                                : "end-of-file"
                                        )+
                                        " while parsing unicode escape sequence"
                                    );
                                    yield c1;
                                }
                            };
                            if(c1 == -1) yield -1;
                            unicode = (unicode << 4) | (c1 - x);
                        }
                        yield unicode;
                    }
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '0' -> '\0';
                    case '1' -> '\1';
                    case '2' -> '\2';
                    case '3' -> '\3';
                    case '4' -> '\4';
                    case '5' -> '\5';
                    case '6' -> '\6';
                    case '7' -> '\7';
                    default  ->
                    {
                        reporter.report
                        (
                            r.line(),
                            "Invalid escape sequence: '\\"+Character.toString(c)+"'"
                        );
                        yield c;
                    }
                };
                if(esc == -1) break;
                sb.append(Character.toString(esc));
                escaped = false;
            }
            else if(!(escaped = c == '\\'))
            {
                if(c == '"') break;
                if(c == -1)
                {
                    reporter.report(r.line(),"Unexpected end-of-file while parsing quote.");
                    break;
                }
                sb.append(Character.toString(c));
            }
        }
        return sb.toString();
    }
    private static void skipLineComment(final Reader r) throws IOException
    {
        //noinspection StatementWithEmptyBody
        for(int c = r.read();c != -1 && c != '\n';c = r.read());
    }
    private static void skipBlockComment(final Reader r) throws IOException
    {
        // Read until c is an asterisk, then check to see if the
        // next character is a slash.
        // This function allows the comment to end on an EOF.
        for(int c = r.read();c != -1 && c != '/';c = r.read())
            while(c != '*' && c != -1)
                c = r.read();
    }
    private static Token next(final ErrReporter reporter,final Reader r) throws IOException
    {
        int c = r.skipWS();
        final int line = r.line();
        if(c == -1) return new Token(EOF,line,null);
        String text = Character.toString(c);
        final TokenType tt = switch(c)
        {
            case 'a','b','c','d','e','f',
                 'g','h','i','j','k','l',
                 'm','n','o','p','q','r',
                 's','t','u','v','w','x',
                 'y','z',
                 'A','B','C','D','E','F',
                 'G','H','I','J','K','L',
                 'M','N','O','P','Q','R',
                 'S','T','U','V','W','X',
                 'Y','Z',
                 '_','$'
                 -> Keywords.getOrDefault(text = word(r,c),ID);
            case ',' -> COMMA;
            case ';' -> SEMICOLON;
            case ':' -> COLON;
            case '?' -> CONDITION;
            case '~' -> BITNOT;
            case '(' -> LPAREN;
            case ')' -> RPAREN;
            case '[' -> LBRACKET;
            case ']' -> RBRACKET;
            case '{' -> LBRACE;
            case '}' -> RBRACE;
            case '.' ->
            {
                final int c2 = r.read();
                if(isNumeric(c2,Base.dec))
                {
                    text = '.'+Character.toString(c2)+frac(r,reporter);
                    yield LIT_FLOAT;
                }
                r.unread(c2);
                yield DOT;
            }
            case '0' ->
            {
                final int c2 = r.read();
                final Base b = switch(c2)
                {
                    case 'x','X' -> Base.hex;
                    case 'b','B' -> Base.bin;
                    default      -> {r.unread(c2); yield Base.dec;}
                };
                final Token t = number(r,reporter,b);
                if(t == null) yield null;
                text = '0'+(b == Base.dec? "" : Character.toString(c2))+t.value();
                yield t.type();
            }
            case '1','2','3','4','5',
                 '6','7','8','9' ->
            {
                final Token t = number(r,reporter,Base.dec);
                if(t == null) yield null;
                text = Character.toString(c)+t.value();
                yield t.type();
            }
            case '"' -> {text = quote(reporter,r); yield LIT_STR;}
            default  ->
            {
                final int c1 = r.read();
                if(c == c1)
                    switch(c)
                    {
                        case '/' -> {skipLineComment(r); yield null;}
                        case '+' -> {text = "++"; yield INC;}
                        case '-' -> {text = "--"; yield DEC;}
                        case '>' ->
                        {
                            final int c2 = r.read();
                            yield switch(c2)
                            {
                                case '>' ->
                                {
                                    final int c3 = r.read();
                                    if(c3 == '=') {text = ">>>="; yield LRSHEQ;}
                                    r.unread(c3);
                                    text = ">>>";
                                    yield LRSH;
                                }
                                case '=' -> {text = ">>="; yield RSHEQ;}
                                default  -> {text = ">>"; r.unread(c2); yield RSH;}
                            };
                        }
                        case '<' ->
                        {
                            final int c2 = r.read();
                            //noinspection SwitchStatementWithTooFewBranches
                            yield switch(c2)
                            {
                                case '=' -> {text = "<<="; yield LSHEQ;}
                                default  -> {text = "<<"; r.unread(c2); yield LSH;}
                            };
                        }
                        case '=' -> {text = "=="; yield EQ;}
                        case '&' -> {text = "&&"; yield AND;}
                        case '|' -> {text = "||"; yield OR;}
                    }
                else if(c1 == '=')
                    switch(c)
                    {
                        case '+' -> {text = "+="; yield ADDEQ;}
                        case '-' -> {text = "-="; yield SUBEQ;}
                        case '*' -> {text = "*="; yield MULEQ;}
                        case '/' -> {text = "/="; yield DIVEQ;}
                        case '%' -> {text = "%="; yield MODEQ;}
                        case '&' -> {text = "&="; yield ANDEQ;}
                        case '|' -> {text = "|="; yield OREQ;}
                        case '^' -> {text = "^="; yield XOREQ;}
                        case '!' -> {text = "!="; yield NEQ;}
                        case '<' -> {text = "<="; yield LEQ;}
                        case '>' -> {text = ">="; yield GEQ;}
                    }
                else if(c == '/' && c1 == '*') {skipBlockComment(r); yield null;}
                r.unread(c1);
                yield switch(c)
                {
                    case '!' -> NOT;
                    case '+' -> ADD;
                    case '-' -> SUB;
                    case '*' -> MUL;
                    case '/' -> DIV;
                    case '%' -> MOD;
                    case '&' -> BITAND;
                    case '|' -> BITOR;
                    case '^' -> BITXOR;
                    case '>' -> GT;
                    case '<' -> LT;
                    case '=' -> ASSIGN;
                    default  ->
                    {
                        reporter.report
                        (
                            r.line(),
                            "Unexpected character '"+Character.toString(c)+'\''
                        );
                        yield ERR;
                    }
                };
            }
        };
        return tt != null
            ? tt != ERR
                  ? new Token(tt,line,text)
                  : DUMMY
            : null;
    }
    public static class TokenIterator
    {
        private final Token[] t;
        private int p = 0;
        
        protected TokenIterator(final Token[] tokens) {t = tokens;}
        
        public boolean hasNext() {return p != t.length;}
        public Token next() {return t[p++];}
        public boolean canAdvance() {return p != t.length - 1;}
        public Token advance() {return t[++p];}
        public Token peek() {return t[p];}
        public Token peek(int offset) {return p+offset < t.length? t[p+offset] : null;}
        public boolean hasPrevious() {return p != 0;}
        public Token previous() {return t[--p];}
        
        public int pos() {return p;}
        public void pos(final int p) {this.p = p;}
    }
    public static TokenIterator tokenize(final java.io.Reader reader,final ErrReporter reporter) throws IOException
    {
        try(final Reader r = new Reader(reader))
        {
            final ArrayList<Token> tokens = new ArrayList<>();
            while(true)
            {
                Token t;
                // Ignore comments.
                do t = next(reporter,r);
                while(t == null);
                
                if(t != DUMMY)
                {
                    tokens.add(t);
                    if(t.type() == EOF) break;
                }
            }
            return reporter.reportAll()
                ? null
                : new TokenIterator(tokens.toArray(Token[]::new));
        }
    }
    public static TokenIterator tokenize(final java.io.Reader reader,final String name,final PrintStream output) throws IOException
    {
        return tokenize(reader,new ErrReporter(name,output));
    }
}