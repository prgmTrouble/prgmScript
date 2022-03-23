package prgmScript;

import prgmScript.ast.prgmException;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Trace
{
    private final String module;
    public boolean enableLogs = false;
    public final PrintStream out,err;
    private int indent = 0;
    public final Map<String,Module> imports = new HashMap<>();
    
    public Trace(final String module,final PrintStream out,final PrintStream err)
    {
        this.module = module;
        this.out = out;
        this.err = err;
    }
    
    private void addIndent() {++indent;}
    private void subIndent() {--indent;}
    public void trace(final int script,final String msg,final int line)
    {
        if(enableLogs)
            out.println("    ".repeat(indent)+"["+line+"|"+(script+1)+"] "+msg);
    }
    public void trace(final int script,final String msg)
    {
        if(enableLogs)
            trace(script,msg,Thread.currentThread().getStackTrace()[2].getLineNumber());
    }
    public <T> T wrapTrace(final int script,final String msg,final Supplier<T> s)
    {
        if(enableLogs)
        {
            trace(script,msg,Thread.currentThread().getStackTrace()[2].getLineNumber());
            addIndent();
            final T out = s.get();
            subIndent();
            return out;
        }
        return s.get();
    }
    @FunctionalInterface public interface Functor {void get();}
    public void wrapTrace(@SuppressWarnings("SameParameterValue") final int script,
                          @SuppressWarnings("SameParameterValue") final String msg,final Functor f)
    {
        if(enableLogs)
        {
            trace(script,msg,Thread.currentThread().getStackTrace()[2].getLineNumber());
            addIndent();
            f.get();
            subIndent();
        }
    }
    public void report(final int line,final String msg) {throw new prgmException(module,line,msg);}
}