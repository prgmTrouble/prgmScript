package prgmScript.api;

/** This class loads the standard libraries. */
public final class Libraries
{
    private Libraries() {}
    
    /** Loads all standard libraries. */
    public static void load()
    {
        prgmMath.register();
        prgmRandom.register();
        prgmOutput.register();
    }//TODO primitive lists, complicated lists, maps
}