package prgmScript.exception;

/** An exception which is thrown during a script's compile time. */
public class ScriptException extends Exception
{
    public ScriptException(final int line,final String module,final String msg)
    {
        super(" ERROR | "+module+" ["+line+"]: "+msg);
    }
}