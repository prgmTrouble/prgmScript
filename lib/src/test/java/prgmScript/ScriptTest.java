package prgmScript;

import org.junit.Test;
import prgmScript.api.Libraries;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;

public class ScriptTest
{
    private static final String DIR = Path.of
    (
        System.getProperty("user.dir"),
        "src","test","java","prgmScript"
    ).toString();
    private static void testFile(final String file) throws IOException
    {
        final File f = Path.of(DIR,file+".prgm").toFile();
        try(final FileReader fr = new FileReader(f))
        {
            final Trace t = new Trace(f.toString(),System.out,System.err);
            t.enableLogs = true;
            assertNotNull(Script.run(fr,t,f.toString()));
        }
    }
    
    @Test public void testValidDecls() throws IOException {testFile("ValidDecls");}
    @Test public void testValidLiterals() throws IOException {testFile("ValidLiterals");}
    @Test public void testValidControlFlow() throws IOException {testFile("ValidControlFlow");}
    @Test public void testValidFunctions() throws IOException {testFile("ValidFunctions");}
    @Test
    public void testValidImports() throws IOException
    {
        Libraries.load();
        Script.setImportsDir(Path.of(System.getProperty("user.dir"),"src","test","java","prgmScript"));
        testFile("ValidImports");
    }
    
    @Test
    public void testMergeSort() throws IOException
    {
        Libraries.load();
        testFile("MergeSort");
    }
    
    @Test
    public void testTest() throws IOException
    {
        Libraries.load();
        testFile("ree");
    }
}