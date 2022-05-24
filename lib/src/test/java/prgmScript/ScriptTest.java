package prgmScript;

import prgmScript.exception.ScriptException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.Assert.*;

public class ScriptTest
{
    @Before
    public void before()
    {
        Script.setImportsDir(Path.of
        (
            System.getProperty("user.dir"),
            "src","test","java","prgmScript"
        ));
    }
    
    @Test
    public void conditional()
    {
        // null
        assertFalse(Script.conditional(null,BaseType.BOOL));
        
        // bool
        assertTrue (Script.conditional(true,BaseType.BOOL));
        assertFalse(Script.conditional(false,BaseType.BOOL));
        
        // int
        assertTrue (Script.conditional(1L,BaseType.INT));
        assertFalse(Script.conditional(0L,BaseType.INT));
        
        // float
        assertTrue (Script.conditional(1D,BaseType.FLOAT));
        assertFalse(Script.conditional(0D,BaseType.FLOAT));
        
        // str
        {
            final Map<String,Value> mock = Map.of(" ",new Value(ConstableType.STR,"test"));
            assertTrue (Script.conditional(mock,BaseType.STR));
            mock.get(" ").value = "";
            assertFalse(Script.conditional(mock,BaseType.STR));
        }
        
        // list
        {
            final ConstableType ct = Types.constableType(Types.listType(Type.BOOL),false);
            final Map<String,Value> mock = Map.of(" ",new Value(ct,List.of(true)));
            assertTrue (Script.conditional(mock,BaseType.LIST));
            mock.get(" ").value = List.of();
            assertFalse(Script.conditional(mock,BaseType.LIST));
        }
        
        // struct
        {
            final Map<String,Value> mock = Map.of("a",new Value(ConstableType.BOOL,true),
                                                  "b",new Value(Types.constableType(Types.funcType(Type.VOID),false),null),
                                                  "c",new Value(ConstableType.FLOAT,1D));
            assertTrue (Script.conditional(mock,BaseType.STRUCT));
            mock.get("c").value = 0D;
            assertFalse(Script.conditional(mock,BaseType.STRUCT));
        }
        
        // illegal types
        assertThrows(IllegalArgumentException.class,() -> Script.conditional(new Object(),BaseType.VOID));
        assertThrows(IllegalArgumentException.class,() -> Script.conditional(new Object(),BaseType.FUNC));
    }
    
    @Test
    public void testToString()
    {
        // void
        assertEquals("void",Script.toString(null,Type.VOID));
        
        // bool
        assertEquals("true",Script.toString(true,Type.BOOL));
        
        // int
        assertEquals("1",Script.toString(1L,Type.INT));
        
        // float
        assertEquals("1.0",Script.toString(1D,Type.FLOAT));
        
        // str
        assertEquals("test",Script.toString(Map.of(" ",new Value(ConstableType.STR,"test")),Type.STR));
        
        // list
        assertEquals
        (
            "[int:1,2,3]",
            Script.toString
            (
                Map.of
                (
                    " ",
                    new Value
                    (
                        Types.constableType(Types.listType(Type.INT),false),
                        List.of
                        (
                            new Value(ConstableType.INT,1L),
                            new Value(ConstableType.INT,2L),
                            new Value(ConstableType.INT,3L)
                        )
                    )
                ),
                Types.listType(Type.INT)
            )
        );
        
        // struct
        {
            // Since the key ordering is dependent on the map, the iteration process must replicate
            // what the code does.
            final Map<String,Value> mock = Map.of("x",new Value(ConstableType.BOOL,true),
                                                  "y",new Value(ConstableType.FLOAT,1D));
            final String out = Script.toString(mock,Types.structType("test"));
            final StringJoiner sj = new StringJoiner(",","{test:","}");
            for(final Map.Entry<String,Value> e : mock.entrySet())
                sj.add(e.getKey()+'='+Script.toString(e.getValue().value,e.getValue().type.type));
            assertEquals(sj.toString(),out);
        }
        
        // func
        assertEquals("func<int>(const bool,str)",Script.toString(null,Types.funcType(Type.INT,ConstableType.CONST_BOOL,ConstableType.STR)));
    }
    
    @Test
    public void setImportsDir()
    {
        Script.setImportsDir(Path.of(""));
        assertEquals(Path.of(""),Script.getImportsDir());
        Script.setImportsDir(null);
        assertEquals(Path.of(System.getProperty("user.dir")),Script.getImportsDir());
    }
    
    private static final String DIR = Path.of
    (
        System.getProperty("user.dir"),
        "src","test","java","prgmScript"
    ).toString();
    private static void testFile(final String file) throws IOException,ScriptException
    {
        final File f = Path.of(DIR,file+".prgm").toFile();
        try(final FileReader fr = new FileReader(f)) {assertNotNull(Script.run(fr,file,System.err));}
    }
    
    @Test public void testValidAssignments() throws IOException,ScriptException {testFile("ValidAssignments");}
    @Test public void testValidControlFlow() throws IOException,ScriptException {testFile("ValidControlFlow");}
    @Test public void testValidConversions() throws IOException,ScriptException {testFile("ValidConversions");}
    @Test public void testValidDecls() throws IOException,ScriptException {testFile("ValidDecls");}
    @Test public void testValidFunctions() throws IOException,ScriptException {testFile("ValidFunctions");}
    @Test public void testValidImports() throws IOException,ScriptException {testFile("ValidImports");}
    @Test public void testValidLists() throws IOException,ScriptException {testFile("ValidLists");}
    @Test public void testValidLiterals() throws IOException,ScriptException {testFile("ValidLiterals");}
    @Test public void testValidMath() throws IOException,ScriptException {testFile("ValidMath");}
    @Test public void testValidPrefixes() throws IOException,ScriptException {testFile("ValidPrefixes");}
    @Test public void testValidStrings() throws IOException,ScriptException {testFile("ValidStrings");}
    @Test public void testValidStructs() throws IOException,ScriptException {testFile("ValidStructs");}
    @Test public void testValidSuffixes() throws IOException,ScriptException {testFile("ValidSuffixes");}
    @Test public void testValidTernary() throws IOException,ScriptException {testFile("ValidTernary");}
    @Test public void testValidVoidLists() throws IOException,ScriptException {testFile("ValidVoidLists");}
}