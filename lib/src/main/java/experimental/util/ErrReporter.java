package experimental.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** A class which records errors and prints them out all at once. */
public class ErrReporter
{
    /** A list of reports. */
    private final List<String> errs = new ArrayList<>(),
                              warns = new ArrayList<>();
    /** The module which this reporter is reporting from. */
    public final String module;
    /** The print stream where all reports will be sent. */
    public final PrintStream ps;
    
    public ErrReporter(final String module,final PrintStream ps) {this.module = module; this.ps = ps;}
    
    private static String reportFmt(final int line,final String msg) {return (line+1)+"] "+msg;}
    /** Adds a warning to the list of reports. */
    public void warn(final int line,final String msg) {warns.add(reportFmt(line,msg));}
    /** Adds an error to the list of reports. */
    public void report(final int line,final String msg) {errs.add(reportFmt(line,msg));}
    /**
     * Prints all reports.
     *
     * @return {@code true} if {@linkplain ErrReporter#report(int,String)} was called at least once.
     */
    public boolean reportAll()
    {
        final boolean noErrs = errs.isEmpty();
        if(noErrs && warns.isEmpty()) return false;
        ps.println("In module '"+ module +"':");
        for(final String w : warns) ps.println("[WARNING] \t["+w);
        for(final String e :  errs) ps.println("[ ERROR ] \t["+e);
        return !noErrs;
    }
}