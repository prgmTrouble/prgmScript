package prgmScript.token;

import org.junit.Test;
import prgmScript.token.Tokenize.TokenIterator;

import java.io.*;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class TokenizeTest
{
    /*
    static void tree(final File f,final int d)
    {
        System.out.println
        (
            "| ".repeat(d)+
            "+--"+f.getName()
        );
        if(f.isDirectory())
        {
            final File[] children = f.listFiles();
            if(children != null)
            {
                final int d2 = d+1;
                for(final File f2 : children) tree(f2,d2);
            }
        }
    }
    @Test
    public void dummy()
    {
        tree(Paths.get(System.getProperty("user.dir")).toFile(),0);
    }
    */
    private static final String[] TOKEN_STRINGS =
    {
        "end-of-file","error-token","import","const",
        "void","bool","int","float","str","func","struct","identifier",
        "if","else","do","while","for","continue","break","return","throw",
        "true","false","integer","floating-point","string",
        "'('","')'","'['","']'","'{'","'}'","';'","','","'.'",
        "'!'","'~'","'?'","':'","'++'","'--'",
        "'+'","'-'","'*'","'/'","'%'","'&'","'|'","'^'",
        "'>'","'<'","'<<'","'>>'","'>>>'","'<='","'>='",
        "'&&'","'||'","'=='","'!='",
        "'='","'+='","'-='","'*='","'/='","'%='",
        "'&='","'|='","'^='","'<<='","'>>='","'>>>='"
    };
    private static final String[] INVALID_NUM =
    {
        ".0e$","0x$","0x.$","0x.0$","0x0p","0.e$","0e$","0b$","1e$",
        ".0E$","0X$","0X.$","0X.0$","0X0P","0.E$","0E$","0B$","1E$"
    };
    @Test
    public void testTokenize() throws IOException
    {
        // TokenType::toString()
        {
            byte ts = 0;
            for(final TokenType tt : TokenType.values())
                assertEquals(tt.toString(),TOKEN_STRINGS[ts++]);
        }
        
        // Valid Tokens
        {
            final File f = Paths.get
            (
                System.getProperty("user.dir"),
                "src","test","java","prgmScript","token","ValidTokenTest.prgm"
            ).toFile();
            try(final FileReader fr = new FileReader(f))
            {
                final TokenIterator i = Tokenize.tokenize(fr,f.toString(),System.err);
                assertNotNull(i);
                final File temp = File.createTempFile("ValidTokenTestTemp",".prgm");
                temp.deleteOnExit();
                try(final FileWriter fw = new FileWriter(temp))
                {
                    Token t = i.next();
                    while(t.type() != TokenType.EOF)
                    {
                        if(t.type() == TokenType.LIT_STR)
                        {
                            fw.write("\"");
                            t.value()
                             .codePoints()
                             .forEachOrdered
                              (
                                  x ->
                                  {
                                      try
                                      {
                                          fw.write
                                          (
                                              switch(x)
                                              {
                                                  case '\f' -> "\\f";
                                                  case '\n' -> "\\n";
                                                  case '\r' -> "\\r";
                                                  case '\t' -> "\\t";
                                                  case '\0' -> "\\0";
                                                  case '\1' -> "\\1";
                                                  case '\2' -> "\\2";
                                                  case '\3' -> "\\3";
                                                  case '\4' -> "\\4";
                                                  case '\5' -> "\\5";
                                                  case '\6' -> "\\6";
                                                  case '\7' -> "\\7";
                                                  case '"'  -> "\\\"";
                                                  case '\\' -> "\\\\";
                                                  case '\u00A7' -> "\\u00A7";
                                                  default   -> Character.toString(x);
                                              }
                                          );
                                      }
                                      catch(final IOException e) {fail(e.getMessage());}
                                  }
                              );
                            fw.write("\"");
                        }
                        else fw.write(t.value());
                        fw.write(" ");
                        t = i.next();
                    }
                }
                while(i.hasPrevious()) i.previous();
                try(final FileReader fr2 = new FileReader(temp))
                {
                    final TokenIterator j = Tokenize.tokenize(fr2,temp.toString(),System.err);
                    assertNotNull(j);
                    while(i.hasNext() && j.hasNext())
                        assertEquals(i.next().type(),j.next().type());
                    assertEquals(i.hasNext(),j.hasNext());
                }
            }
        }
        assertNotNull(Tokenize.tokenize(new StringReader("//"),"EOF line comment",System.err));
        
        // Invalid Number
        for(final String s : INVALID_NUM)
            assertNull(Tokenize.tokenize(new StringReader(s),s,System.err));
        
        // Invalid String
        assertNull(Tokenize.tokenize(new StringReader("\"\\u"),"\"\\u",System.err));
        assertNull(Tokenize.tokenize(new StringReader("\"\\u00A&\""),"\"\\u00A&\"",System.err));
        assertNull(Tokenize.tokenize(new StringReader("\""),"\"",System.err));
        
        // Unknown Token
        assertNull(Tokenize.tokenize(new StringReader("@"),"Unknown Token",System.err));
        assertNull(Tokenize.tokenize(new StringReader("@="),"Unknown Token",System.err));
    }
}