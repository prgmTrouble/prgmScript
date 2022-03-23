package prgmScript.util;

import java.io.PrintStream;
import java.util.ArrayList;

public class ErrReporter
{
    private final ArrayList<String> errs = new ArrayList<>();
    public final String module;
    private final PrintStream ps;
    
    public ErrReporter(final String module,final PrintStream ps) {this.module = module; this.ps = ps;}
    
    private static String reportFmt(final int line,final String msg) {return (line+1)+"] "+msg;}
    public static String format(final int line,final String msg) {return "["+(line+1)+"] "+msg;}
    public void report(final int line,final String msg) {errs.add(reportFmt(line,msg));}
    public boolean reportAll()
    {
        if(errs.isEmpty()) return false;
        ps.println("In module '"+ module +"':");
        for(final String e : errs) ps.println("\t["+e);
        return true;
    }
}