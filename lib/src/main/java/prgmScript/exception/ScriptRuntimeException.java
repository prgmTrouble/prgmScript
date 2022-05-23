package prgmScript.exception;

/** A type of exception which is thrown during a script's runtime. */
public class ScriptRuntimeException extends RuntimeException
{
    public ScriptRuntimeException(final int line,final String module,final String msg)
    {
        super(" ERROR | "+module+" ["+line+"]: "+msg);
    }
    public ScriptRuntimeException(final int line,final String module) {super(" ERROR | "+module+" ["+line+"]");}
}