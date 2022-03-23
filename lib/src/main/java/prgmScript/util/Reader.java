package prgmScript.util;

import java.io.BufferedReader;
import java.io.IOException;

public class Reader implements AutoCloseable
{
    private final BufferedReader r;
    private final IntStack stk = new IntStack();
    private int line = 0;
    
    public Reader(final java.io.Reader reader) {r = new BufferedReader(reader);}
    
    public int line() {return line;}
    
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
    public void unread(final int c)
    {
        if(c == '\n') --line;
        stk.push(c);
    }
    public int skipWS() throws IOException
    {
        int c;
        do c = read(); while(Character.isWhitespace(c));
        return c;
    }
    
    @Override public void close() throws IOException {r.close();}
}