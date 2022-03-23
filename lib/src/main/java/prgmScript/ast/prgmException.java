package prgmScript.ast;

import prgmScript.util.ErrReporter;

/** This exception type serves only to tell whether something was the origin of an exception or not. */
public class prgmException extends RuntimeException
{
    private final int l;
    private final String m,mod;
    public final boolean origin;
    public prgmException(final String module,final int line,final String msg)
    {
        l = line;
        origin = (m = msg) != null;
        mod = module;
    }
    public prgmException() {this(null,0,null);}
    
    @Override
    public String toString()
    {
        return (mod != null? "In module '"+mod+"': ": "") + (m == null? "["+l+"]" : ErrReporter.format(l,m));
    }
}