package prgmScript.util;

import java.io.BufferedReader;
import java.io.IOException;

/** A wrapper for a {@linkplain java.io.Reader} which allows read characters to be "unread". */
public class Reader implements AutoCloseable
{
    private final BufferedReader r;
    private final IntStack stk = new IntStack();
    private int line = 0;
    
    public Reader(final java.io.Reader reader) {r = new BufferedReader(reader);}
    /** @return The number of newline ('\n') characters read. */
    public int line() {return line;}
    
    /** @return The code point of the next character. */
    public int read() throws IOException
    {
        final int o;
        if(stk.empty())
        {
            if(!r.ready()) return -1;
            o = r.read();
        }
        else o = stk.pop();
        if(o == '\n') ++line;
        return o;
    }
    /** Puts the specified code point back into the reader. */
    public void unread(final int c)
    {
        if(c == '\n') --line;
        stk.push(c);
    }
    /**
     * Skips all characters which satisfy {@linkplain Character#isWhitespace(char)}.
     *
     * @return The first non-whitespace character encountered.
     */
    public int skipWS() throws IOException
    {
        int c;
        do c = read(); while(Character.isWhitespace(c));
        return c;
    }
    
    @Override public void close() throws IOException {r.close();}
}