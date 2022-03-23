package prgmScript.ast;

public abstract class Stmt
{
    public final int line;
    public Stmt(final int line) {this.line = line;}
    
    public abstract Literal eval(final Scope<Value> scope);
}