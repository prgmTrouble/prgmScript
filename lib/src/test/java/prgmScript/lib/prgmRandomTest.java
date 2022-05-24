package prgmScript.lib;

import org.junit.Test;
import prgmScript.Module;
import prgmScript.Script;
import prgmScript.Value;
import prgmScript.exception.ScriptException;
import prgmScript.util.XorShift;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class prgmRandomTest
{
    private static final File DIR = Path.of
    (
        System.getProperty("user.dir"),
        "src","test","java","prgmScript","lib","RandomTest.prgm"
    ).toFile();
    @Test
    public void testRandom() throws IOException,ScriptException
    {
        final Module m;
        try(final FileReader fr = new FileReader(DIR)) {assertNotNull(m = Script.run(fr,"RandomTest",System.err));}
        final List<Value> i32_1 = Script.listData(m.getValue( "i32_1").getValue()),
                          i32_2 = Script.listData(m.getValue( "i32_2").getValue()),
                          i64_1 = Script.listData(m.getValue( "i64_1").getValue()),
                          i64_2 = Script.listData(m.getValue( "i64_2").getValue()),
                         bi64_1 = Script.listData(m.getValue("bi64_1").getValue()),
                         bi64_2 = Script.listData(m.getValue("bi64_2").getValue()),
                          f32_1 = Script.listData(m.getValue( "f32_1").getValue()),
                          f32_2 = Script.listData(m.getValue( "f32_2").getValue()),
                          f64_1 = Script.listData(m.getValue( "f64_1").getValue()),
                          f64_2 = Script.listData(m.getValue( "f64_2").getValue()),
                         bf64_1 = Script.listData(m.getValue("bf64_1").getValue()),
                         bf64_2 = Script.listData(m.getValue("bf64_2").getValue()),
                            g_1 = Script.listData(m.getValue(   "g_1").getValue()),
                            g_2 = Script.listData(m.getValue(   "g_2").getValue()),
                            e_1 = Script.listData(m.getValue(   "e_1").getValue()),
                            e_2 = Script.listData(m.getValue(   "e_2").getValue());
        
        final Random r1 = new Random(1L);
        for(final Value v : i32_1) assertEquals(r1.nextInt        (),( long )v.getValue());
        for(final Value v : i64_1) assertEquals(r1.nextLong       (),( long )v.getValue());
        for(final Value v :bi64_1) assertEquals(r1.nextLong   (1,10),( long )v.getValue());
        for(final Value v : f32_1) assertEquals(r1.nextFloat      (),(double)v.getValue(),1e-10);
        for(final Value v : f64_1) assertEquals(r1.nextDouble     (),(double)v.getValue(),1e-10);
        for(final Value v :bf64_1) assertEquals(r1.nextDouble (1,10),(double)v.getValue(),1e-10);
        for(final Value v :   g_1) assertEquals(r1.nextGaussian   (),(double)v.getValue(),1e-10);
        for(final Value v :   e_1) assertEquals(r1.nextExponential(),(double)v.getValue(),1e-10);
        
        final XorShift r2 = new XorShift(1L);
        for(final Value v : i32_2) assertEquals(r2.nextInt        (),( long )v.getValue());
        for(final Value v : i64_2) assertEquals(r2.nextLong       (),( long )v.getValue());
        for(final Value v :bi64_2) assertEquals(r2.nextLong   (1,10),( long )v.getValue());
        for(final Value v : f32_2) assertEquals(r2.nextFloat      (),(double)v.getValue(),1e-10);
        for(final Value v : f64_2) assertEquals(r2.nextDouble     (),(double)v.getValue(),1e-10);
        for(final Value v :bf64_2) assertEquals(r2.nextDouble (1,10),(double)v.getValue(),1e-10);
        for(final Value v :   g_2) assertEquals(r2.nextGaussian   (),(double)v.getValue(),1e-10);
        for(final Value v :   e_2) assertEquals(r2.nextExponential(),(double)v.getValue(),1e-10);
    }
}