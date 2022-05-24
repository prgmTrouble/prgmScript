package prgmScript.lib;

import org.junit.Test;
import prgmScript.Script;
import prgmScript.exception.ScriptException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;

public class prgmOutputTest
{
    private static final File DIR = Path.of
    (
        System.getProperty("user.dir"),
        "src","test","java","prgmScript","lib","OutputTest.prgm"
    ).toFile();
    @Test
    public void testOutput() throws IOException,ScriptException
    {
        try(final FileReader fr = new FileReader(DIR)) {assertNotNull(Script.run(fr,"OutputTest",System.err));}
    }
}