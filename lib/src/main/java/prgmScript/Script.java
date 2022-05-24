package prgmScript;

import prgmScript.exception.ScriptException;
import prgmScript.exception.ScriptRuntimeException;
import prgmScript.token.Token;
import prgmScript.token.TokenType;
import prgmScript.token.Tokenize;
import prgmScript.token.Tokenize.TokenIterator;
import prgmScript.util.ContainerUtil;
import prgmScript.util.ErrReporter;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class contains functions which parse and compile prgmScript.
 * <br>
 * Grammar:
 * <pre>
 *               Stmt := BlockStmt
 *                     | IfStmt
 *                     | ForStmt
 *                     | WhileStmt
 *                     | DoWhileStmt
 *                     | RetStmt
 *                     | ThrowStmt
 *                     | BreakStmt
 *                     | ContinueStmt
 *                     | StructDeclStmt
 *                     | ImportStmt
 *                     | DeclOrExprStmt
 *                     | ';'
 *
 *          BlockStmt := '{' {Stmt} '}'
 *             IfStmt := 'if' '(' Expr ')' Stmt ['else' Stmt]
 *            ForStmt := 'for' '(' (((DeclOrExprStmt|';') [Expr] ';' [Expr {, Expr}])|(['const'] Name ':' Expr)) ')' Stmt
 *          WhileStmt := 'while' '(' Expr ')' Stmt
 *        DoWhileStmt := 'do' Stmt 'while' '(' Expr ')'
 *            RetStmt := 'return' [Expr] ';'
 *          ThrowStmt := 'throw' [Expr] ';'
 *          BreakStmt := 'break' ';'
 *       ContinueStmt := 'continue' ';'
 *     StructDeclStmt := 'struct' StructName '{' [ArgList] '}'
 *         ImportStmt := 'import' LiteralStr ';'
 *     DeclOrExprStmt := (DeclStmt|ExprStmt)
 *           DeclStmt := ['const'] Type Name ['=' Expr] {',' Name ['=' Expr]} ';'
 *           ExprStmt := Expr ';'
 *
 *               Expr := LowPrecedence [Assignment]
 *      LowPrecedence := MediumPrecedence [TernaryExpr]
 *   MediumPrecedence := HighPrecedence [MathExpr]
 *     HighPrecedence := LiteralBool
 *                     | LiteralNum
 *                     | LiteralStr
 *                     | LiteralFunc [Call]
 *                     | LiteralList
 *                     | LiteralStruct
 *                     | Prefix
 *                     | Id
 *                     | '(' Expr ')'
 *
 *           MathExpr := {('&&'|'||'|'=='|'!='|'&gt'|'&lt'|'&gt='|'&lt='|'+'|'-'|'*'|'/'|'%'|'&'|'|'|'^'|'&lt&lt'|'&gt&gt'|'&gt&gt&gt') HighPrecedence}
 *        TernaryExpr := '?' Expr ':' LowPrecedence
 *         Assignment := ('+='|'&='|'/='|'&gt&gt&gt='|'&lt&lt='|'%='|'*='|'|='|'&gt&gt='|'-='|'^='|'=') Expr
 *               Call := '(' [Expr {',' Expr }] ')'
 *             Prefix := ('++'|'--'|'+'|'-'|'!'|'~') HighPrecedence
 *                 Id := Name {'.' Name} [Suffix]
 *             Suffix := (('[' Expr ']' [Suffix])|(Call [Suffix])|'++'|'--'|('.' Id))
 *
 *        LiteralFunc := 'func' '&lt' [Type|'void'] '&gt' '(' [ArgList] ')' BlockStmt
 *        LiteralList := '[' Type ':' [Expr {',' Expr}] ']'
 *      LiteralStruct := '{' StructName ':' [Name '=' Expr {',' Name '=' Expr}] '}'
 *
 *               Type := (Primitive|FuncType|StructName) {'[' ']'}
 *          Primitive := ('bool'|'int'|'float'|'str'|('void' '[' ']'))
 *           FuncType := 'func' '&lt' [Type|'void'] '&gt' '(' [['const'] Type {',' ['const'] Type}] ')'
 *         StructName := any Name which isn't a Primitive or a keyword (e.g. 'if', 'for', etc.)
 *
 *                Arg := ['const'] Type Name
 *            ArgList := Arg {',' Arg}
 * </pre>
 *
 * @see Tokenize Definitions for LiteralBool, LiteralNum, LiteralStr, and Name
 */
public final class Script
{
    /** Instances of this class are useless. */
    private Script() {}
    
    /** An enumeration of modes which control cursor behavior. */
    private enum ItrMode
    {
        /** Gets the token at the position behind the cursor. */
        prev,
        
        /** Gets the token at the cursor's position without advancing. */
        peek,
        
        /** Gets the token at the cursor's position, then advances the cursor. */
        next,
        
        /** Advances the cursor, then gets the token at the new position. */
        advance
    }
    /**
     * Safely gets a token from the token iterator, throwing an exception
     * if not possible.
     */
    private static Token nonEOF(final ItrMode itr,final TokenIterator tokens,final ErrReporter r)
    {
        if
        (
            // Determine if there isn't a readable token at the requested position.
            switch(itr)
            {
                case prev      -> !tokens.hasPrevious() || tokens.peek(-1).type() == TokenType.EOF;
                case peek,next -> !tokens.hasNext()     || tokens.peek(  ).type() == TokenType.EOF;
                default        -> !tokens.canAdvance()  || tokens.peek( 1).type() == TokenType.EOF;
            }
        )
        {
            r.report
            (
                // Get the last line number based on the mode.
                switch(itr)
                {
                    case prev,peek -> tokens.hasPrevious()? tokens.previous().line() : -1;
                    case next      ->
                    {
                        tokens.previous();
                        yield tokens.hasPrevious()? tokens.previous().line() : -1;
                    }
                    case advance   -> tokens.hasNext()? tokens.peek().line() : -1;
                },
                "Unexpected end-of-file"
            );
            // Since EOF was hit, there are no more errors to be
            // generated, we can simply throw and then report the
            // errors.
            throw new UncheckedIOException(new EOFException());
        }
        // No EOF, continue as usual.
        return switch(itr)
        {
            case prev    -> tokens.peek(-1);
            case peek    -> tokens.peek();
            case next    -> tokens.next();
            case advance -> tokens.advance();
        };
    }
    /**
     * Safely gets a token from the token iterator, or returns an EOF if not
     * possible.
     */
    private static Token getOrEOF(final ItrMode itr,final TokenIterator tokens)
    {
        return switch(itr)
        {
            case prev -> /*tokens.hasPrevious()?*/ tokens.peek(-1)  /*: GENERIC_EOF*/;
            case peek -> /*tokens.hasNext()    ?*/ tokens.peek()    /*: GENERIC_EOF*/;
            case next -> /*tokens.hasNext()    ?*/ tokens.next()    /*: GENERIC_EOF*/;
            default   -> /*tokens.canAdvance() ?*/ tokens.advance() /*: GENERIC_EOF*/;
        };
    }
    /**
     * Reports an error if the token returned from {@link #nonEOF} doesn't
     * match the allowed types.
     *
     * @return The parsed token.
     */
    private static Token eat(final ItrMode itr,final TokenIterator tokens,
                             final ErrReporter r,final TokenType...types)
    {
        final Token t = getOrEOF(itr,tokens);
        final TokenType tt = t.type();
        // Iterate through all the allowed types, returning if it found a match.
        for(final TokenType ttt : types) if(tt == ttt) return t;
        // Apply special /style/ for singleton case.
        if(types.length == 1) r.report(t.line(),"Expected "+types[0]+", got "+tt);
        else
        {
            final StringJoiner sj = new StringJoiner(",","[","]");
            for(final TokenType ttt : types) sj.add(ttt.toString());
            r.report(t.line(),"Expected one of "+sj+", got "+tt);
        }
        return t;
    }
    /**
     * Same as {@link #eat}, but in the form of a conditional.
     *
     * @return {@code true} if the token returned by {@link #nonEOF} does <b>not</b>
     *         match the allowed types.
     */
    private static boolean mismatch(final ItrMode itr,final TokenIterator tokens,
                                    final ErrReporter r,final TokenType...types)
    {
        // Copy-pasta from eat.
        final Token t = getOrEOF(itr,tokens);
        final TokenType tt = t.type();
        for(final TokenType ttt : types) if(tt == ttt) return false;
        if(types.length == 1) r.report(t.line(),"Expected "+types[0]+", got "+tt);
        else
        {
            final StringJoiner sj = new StringJoiner(",","[","]");
            for(final TokenType ttt : types) sj.add(ttt.toString());
            r.report(t.line(),"Expected one of "+sj+", got "+tt);
        }
        return true;
    }
    /**
     * @return {@code true} if the token returned by {@link #nonEOF} matches the
     *         allowed types.
     */
    private static boolean matches(final ItrMode itr,final TokenIterator tokens,
                                   final TokenType...types)
    {
        final TokenType t = getOrEOF(itr,tokens).type();
        for(final TokenType tt : types) if(t == tt) return true;
        return false;
    }
    /**
     * Skips until the current token matches the specified type with balance zero,
     * or an EOF is reached.
     * (e.g. if {@code tokens} has value {@code '())a'}, calling this function with
     * {@code until=')'} and {@code open='('} will leave the cursor at {@code 'a'})
     *
     * @param until Token type which decreases the balance counter.
     * @param open  Token type which increases the balance counter.
     */
    private static void skip(final TokenIterator tokens,final TokenType until,final TokenType open)
    {
        // Loop until the counter is balanced.
        for(int balance = 1;balance != 0;)
        {
            TokenType t;
            // Skip until a match for 'until' or 'open' is found.
            do if((t = getOrEOF(ItrMode.next,tokens).type()) == TokenType.EOF) return;
            while(t != until && t != open);
            // Update balance counter.
            balance += t == until? -1:1;
        }
    }
    /**
     * @return The precedence of the specified operator. Lower values
     *         represent lower precedence.
     */
    private static byte precedence(final TokenType op)
    {
        return switch(op)
        {
            case OR            -> (byte)0;
            case AND           -> (byte)1;
            case BITOR         -> (byte)2;
            case BITXOR        -> (byte)3;
            case BITAND        -> (byte)4;
            case EQ,NEQ        -> (byte)5;
            case LT,GT,LEQ,GEQ -> (byte)6;
            case LSH,RSH,LRSH  -> (byte)7;
            case ADD,SUB       -> (byte)8;
            case MUL,DIV,MOD   -> (byte)9;
            
            default            -> (byte)-1;
        };
    }
    
    /* ========== ~ I'm Very Lazy ~ ========== */
    private static long dtol(final double o) {return Double.doubleToRawLongBits(o);}
    private static double ltod(final long o) {return Double.longBitsToDouble(o);}
    
    /*/
    By convention, the return value of any operation is stored on the stack and all
    instructions are added to their blocks before returning.
    /*/
    
    /**
     * @param obj An object of any type.
     *
     * @return If the argument is a {@linkplain Value}, the value will be unwrapped. Otherwise,
     *         the argument is returned unchanged.
     */
    private static Object resolve(final Object obj) {return obj instanceof final Value v? v.value : obj;}
    /**
     * @param o    An object of any type.
     * @param from The {@linkplain BaseType} representing the first argument.
     *
     * @return A boolean representing the state of the first argument:
     * <table>
     * <thead>
     *   <tr>
     *     <th>Type</th>
     *     <th>conditional(o)</th>
     *   </tr>
     * </thead>
     * <tbody>
     *   <tr>
     *     <td>BOOL</td>
     *     <td>o</td>
     *   </tr>
     *   <tr>
     *     <td>FLOAT</td>
     *     <td>o != 0.0D</td>
     *   </tr>
     *   <tr>
     *     <td>INT</td>
     *     <td>o != 0L</td>
     *   </tr>
     *   <tr>
     *     <td>LIST</td>
     *     <td>!o.isEmpty()</td>
     *   </tr>
     *   <tr>
     *     <td>STR</td>
     *     <td>!o.isEmpty()</td>
     *   </tr>
     *   <tr>
     *     <td>STRUCT</td>
     *     <td>none of the values v in o have one of the types in the left column and conditional(v) is false</td>
     *   </tr>
     * </tbody>
     * </table>
     *
     * @throws ClassCastException {@code o} does not match the type implied by {@code from}.
     * @throws IllegalArgumentException {@code from} is not one of the types listed in the above chart.
     */
    static boolean conditional(Object o,final BaseType from)
    {
        if(o == null) return false;
        o = resolve(o);
        return switch(from)
        {
            case BOOL   -> (Boolean)o;
            case FLOAT  -> (Double)o != 0D;
            case INT    -> (Long)o != 0L;
            case LIST   -> !listData(o).isEmpty();
            case STR    -> !strData(o).isEmpty();
            case STRUCT ->
            {
                @SuppressWarnings("unchecked")
                final Map<String,Value> m = (Map<String,Value>)o;
                for(final Value v : m.values())
                    if(triviallyConvertible(Type.BOOL,v.type.type))
                        if(!conditional(v,v.type.type.base))
                            yield false;
                yield true;
            }
            default -> throw new IllegalArgumentException("Critical Error: unsupported type: "+from+" (with object class "+o.getClass()+')');
        };
    }
    /**
     * @param a  An object of any type.
     * @param at The {@linkplain Type} representing {@code a}
     * @param b  An object of any type.
     * @param bt The {@linkplain Type} representing {@code b}
     *
     * @return {@code true} iff the raw values of a and b are the same. This function only allows type
     *         differences if both values are numeric (e.g. integral or floating-point). Otherwise, it
     *         will attempt to run a deep-equals map operation or {@code a.equals(b)} if not applicable.
     */
    private static boolean equals(Object a,final Type at,Object b,final Type bt)
    {
        if(a == b) return true;
        if(a == null || b == null) return false;
        a = resolve(a);
        b = resolve(b);
        if(a == b) return true;
        if(a == null || b == null) return false;
        final BaseType abt = at.base,bbt = bt.base;
        return switch(abt)
        {
            case BOOL -> bbt == BaseType.BOOL && a.equals(b);
            case INT -> switch(bbt)
            {
                case INT -> (long)a == (long)b;
                case FLOAT -> (long)a == (double)b;
                default -> false;
            };
            case FLOAT -> switch(bbt)
            {
                case INT -> (double)a == (long)b;
                case FLOAT -> (double)a == (double)b;
                default -> false;
            };
            case STR -> bbt == BaseType.STR && strData(a).equals(strData(b));
            case LIST ->
            {
                if(!at.equals(bt) || at.subType.base == BaseType.VOID || bt.subType.base == BaseType.VOID) yield false;
                final List<Value> av = listData(a),
                                  bv = listData(b);
                if(av.size() != bv.size()) yield false;
                final Type ct = at.subType;
                for(final Iterator<Value> ai = av.iterator(),
                                          bi = bv.iterator();
                    ai.hasNext();)
                    if(!equals(ai.next().value,ct,bi.next().value,ct))
                        yield false;
                yield true;
            }
            case STRUCT ->
            {
                if(!at.structName.equals(bt.structName)) yield false;
                @SuppressWarnings("unchecked")
                final Map<String,Value> aa = (Map<String,Value>)a,
                                        bb = (Map<String,Value>)b;
                // Compare struct data.
                for(final Map.Entry<String,Value> e : aa.entrySet())
                {
                    final Value v = e.getValue();
                    final Type t = v.type.type;
                    if(!equals(v.value,t,bb.get(e.getKey()).value,t))
                        yield false;
                }
                yield true;
            }
            default -> a.equals(b);
        };
    }
    /** An object which holds the compiler's state. */
    private static class Context
    {
        /**
         * The current block.
         * @see Block
         * @see BlockBuilder
         */
        BlockBuilder bb;
        /**
         * The current file's {@linkplain TokenIterator}.
         * @see Tokenize
         */
        final TokenIterator tokens;
        /**
         * The compile-time scope.
         * @see Scope
         * @see CompilerScope
         */
        CompilerScope sc = new CompilerScope();
        /** This module's name. */
        final String module;
        /** The {@linkplain ErrReporter}. */
        final ErrReporter reporter;
        /** All currently active imports. Each entry has a counter to ensure no double unloads happen. */
        final Map<String,Integer> imports = new HashMap<>();
        int accPos = 0;
        final List<BlockBuilder> bbList = new ArrayList<>();
        byte deadCode = 0;
        final Map<String,Integer> bbNames = new HashMap<>();
        final String path;
        int subID = 0;
        boolean sideEffects = false;
        
        /** Initializes the context by running {@linkplain Tokenize#tokenize(Reader,ErrReporter)}. */
        Context(final String module,final Reader reader,final PrintStream err) throws IOException
        {
            tokens = Tokenize.tokenize(reader,reporter = new ErrReporter(this.module = module,err));
            path = "main";
        }
        @SuppressWarnings("CopyConstructorMissesField")
        Context(final Context other)
        {
            tokens = other.tokens;
            sc = other.sc;
            module = other.module;
            reporter = other.reporter;
            imports.putAll(other.imports);
            path = other.path+'.'+other.subID++;
        }
        
        /** Creates a new {@linkplain BlockBuilder}. */
        BlockBuilder createBB(final String name)
        {
            final int i = bbNames.getOrDefault(name,0);
            bbNames.put(name,i+1);
            return new BlockBuilder(module,i != 0? name + i : name);
        }
        
        @SuppressWarnings("unused")
        String dump()
        {
            final StringBuilder sb = new StringBuilder(module)
                                               .append(" (")
                                               .append(path)
                                               .append(')');
            int n = 0;
            for(final BlockBuilder bb : bbList)
                bb.dump(bbList,sb.append("\n\t").append(n++).append('\t'));
            return sb.append("\n\n").toString();
        }
        
        Block compile() throws ScriptException
        {
            //*debug*/ System.out.println(dump());
            final Block[] blocks = new Block[bbList.size()];
            int i = 0;
            for(final BlockBuilder b : bbList) blocks[i++] = b.build(blocks);
            return blocks[0];
        }
    }
    /** A representation of a runtime function. */
    static record Func(RuntimeScope scope,String[] argn,Block body) {}
    /** Return status for statements, which can be valid, invalid, or end of block. */
    private enum Status
    { //TODO add status for invalid but salvageable
        /** Valid        */OK,
        /** Invalid      */BAD,
        /** End of Block */SKIP
    }
    /** @return The element type of the list. (e.g. {@code bool[][][] -> bool}). */
    private static BaseType getElemType(Type t)
    {
        while(t.base == BaseType.LIST) t= t.subType;
        return t.base;
    }
    /**
     * @return {@code true} iff the two arguments represent the same type, or both are lists and the first type
     *         is a void list.
     */
    private static boolean compareOrVoidList(final Type a,final Type b)
    {
        // Check for 'void[]'.
        Type ast = a,bst = b;
        while(ast.base == BaseType.LIST && bst.base == BaseType.LIST)
        {
            ast = ast.subType;
            bst = bst.subType;
            if(ast.base == BaseType.VOID) return true;
        }
        // Compare if void list not found.
        return ast.equals(bst);
    }
    /** @return {@code true} iff the two arrays represent the same function arguments. */
    private static boolean compareArgs(final ConstableType[] a,final ConstableType[] b)
    {
        if(a != null && b != null && a.length == b.length)
        {
            for(int i = 0;i < a.length;++i)
                if(!compareOrVoidList(a[i].type,b[i].type))
                    return false;
            return true;
        }
        return false;
    }
        /** The type of String::length. Since this does not depend on the list's type, it can be constant. */
    private static final ConstableType
        STR_LENGTH = Types.constableType(Types.funcType(Type.INT),true),
        /** The type of String::substring. Since this does not depend on the list's type, it can be constant. */
        STR_SUBSTR = Types.constableType(Types.funcType(Type.STR,ConstableType.INT,ConstableType.INT),true);
    /** The type of List::length. Since this does not depend on the list's type, it can be constant. */
    private static final ConstableType LIST_LENGTH = Types.constableType(Types.funcType(Type.INT),true);
    /** Members of the string struct. */
    private static final Map<String,ConstableType> STR_MEMBERS = Map.of("length",STR_LENGTH,"substring",STR_SUBSTR),
                                                   VOID_LIST_MEMBERS = Map.of("length",LIST_LENGTH);
    /** @return Gets the members of the specified struct. */
    private static Map<String,ConstableType> getMembers(final CompilerScope scope,final Type struct)
    {// TODO see comment in listStruct
        return switch(struct.base)
        {
            case LIST   ->
            {
                final Type st = struct.subType;
                final ConstableType cct  = Types.constableType(st,true),
                                    push = Types.constableType(Types.funcType(Type.VOID,cct),true),
                                    pop  = Types.constableType(Types.funcType(st),true);
                yield st.base == BaseType.VOID
                    ? VOID_LIST_MEMBERS
                    : Map.of
                      (
                          "length"   ,LIST_LENGTH,
                          "front"    ,pop,
                          "back"     ,pop,
                          "pushFront",push,
                          "pushBack" ,push,
                          "popFront" ,pop,
                          "popBack"  ,pop,
                          "insert"   ,Types.constableType(Types.funcType(Type.VOID,ConstableType.INT,cct),true),
                          "remove"   ,Types.constableType(Types.funcType(st,ConstableType.INT),true)
                      );
            }
            case STR    -> STR_MEMBERS;
            case STRUCT -> scope.getStruct(struct.structName);
            default -> null;
        };
    }
    /** @return A struct wrapping a list converted to type {@code void[]}. */
    static Map<String,Value> voidList(final Type subType,final List<Value> list)
    {
        return Map.ofEntries
        (
            Map.entry(" subtype",new Value(Types.CONST_VOID,subType)),
            Map.entry(" ",new Value(Types.constableType(Types.listType(subType),true),list)),
            Map.entry
            (
                "length",
                new Value
                (
                    Types.constableType(Types.funcType(Type.INT),true),
                    new Func
                    (
                        new RuntimeScope(),new String[0],
                        new Block(Type.INT) {@Override Object exec(final RuntimeScope s) {return (long)list.size();}}
                    )
                )
            )
        );
    }
    /** @return A struct wrapping the specified list. */
    static Map<String,Value> listStruct(final Type subtype,final List<Value> list)
    {//TODO move copy and swap to library & add sublist
        final Type listType = Types.listType(subtype);
        final ConstableType est = Types.constableType(subtype,false),
                            cst = Types.constableType(subtype,true),
                            clt = Types.constableType(listType,true),
                          retct = Types.constableType(Types.funcType(subtype),true),
                          putct = Types.constableType(Types.funcType(Type.VOID,cst),true);
        final RuntimeScope pushFront = new RuntimeScope(),
                           pushBack = new RuntimeScope(),
                           insert = new RuntimeScope(),
                           remove = new RuntimeScope();
        pushFront.putField("\0",new Value(cst,null));
        pushBack.putField("\0",new Value(cst,null));
        insert.putField("\0",new Value(ConstableType.CONST_INT,null));
        insert.putField("\1",new Value(cst,null));
        remove.putField("\0",new Value(ConstableType.CONST_INT,null));
        return Map.of
        (
            // The data entry is not listed in 'getMembers' so that it is invisible.
            " ",new Value(clt,list),
            
            "length",
            new Value
            (
                Types.constableType(Types.funcType(Type.INT),true),
                new Func
                (
                    new RuntimeScope(),new String[0],
                    new Block(Type.INT) {@Override Object exec(final RuntimeScope s) {return (long)list.size();}}
                )
            ),
            
            "popFront",
            new Value
            (
                retct,
                new Func
                (
                    new RuntimeScope(),new String[0],
                    new Block(subtype) {@Override Object exec(final RuntimeScope s) {return list.remove(0);}}
                )
            ),
            
            "popBack",
            new Value
            (
                retct,
                new Func
                (
                    new RuntimeScope(),new String[0],
                    new Block(subtype) {@Override Object exec(final RuntimeScope s) {return list.remove(list.size()-1);}}
                )
            ),
            
            "pushFront",
            new Value
            (
                putct,
                new Func
                (
                    pushFront,new String[] {"\0"},
                    new Block(Type.VOID)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            list.add(0,new Value(est,s.getField("\0").value));
                            return null;
                        }
                    }
                )
            ),
            
            "pushBack",
            new Value
            (
                putct,
                new Func
                (
                    pushBack,new String[] {"\0"},
                    new Block(Type.VOID)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            list.add(new Value(est,s.getField("\0").value));
                            return null;
                        }
                    }
                )
            ),
            
            "front",
            new Value
            (
                retct,
                new Func
                (
                    new RuntimeScope(),new String[0],
                    new Block(subtype) {@Override Object exec(final RuntimeScope s) {return list.get(0);}}
                )
            ),
            
            "back",
            new Value
            (
                retct,
                new Func
                (
                    new RuntimeScope(),new String[0],
                    new Block(subtype) {@Override Object exec(final RuntimeScope s) {return list.get(list.size()-1);}}
                )
            ),
            
            "insert",
            new Value
            (
                Types.constableType(Types.funcType(Type.VOID,ConstableType.INT,cst),true),
                new Func
                (
                    insert,new String[] {"\0","\1"},
                    new Block(Type.VOID)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            list.add(((Long)s.getField("\0").value).intValue(),new Value(est,s.getField("\1").value));
                            return null;
                        }
                    }
                )
            ),
            
            "remove",
            new Value
            (
                Types.constableType(Types.funcType(subtype,ConstableType.INT),true),
                new Func
                (
                    remove,new String[] {"\0"},
                    new Block(subtype)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            return list.remove(((Long)s.getField("\0").value).intValue());
                        }
                    }
                )
            )
        );
    }
    /** @return The data from the list struct. */
    @SuppressWarnings("unchecked")
    public static List<Value> listData(final Object list)
    {
        return (List<Value>)((Map<String,Value>)list).get(" ").value;
    }
    /** @return A struct wrapping the specified string. */
    static Map<String,Value> strStruct(final String str)
    {//TODO move functions to library & add toIntList, character
        final RuntimeScope substr = new RuntimeScope();
        substr.putField("\0",new Value(ConstableType.INT,null));
        substr.putField("\1",new Value(ConstableType.INT,null));
        return Map.of
        (
            // The data entry is not listed in 'getMembers' so that it is invisible.
            " ",
            new Value(ConstableType.CONST_STR,str),
            
            "length",
            new Value
            (
                STR_LENGTH,
                new Func
                (
                    new RuntimeScope(),new String[0],
                    new Block(Type.INT) {@Override Object exec(final RuntimeScope s) {return (long)str.length();}}
                )
            ),
            
            "substring",
            new Value
            (
                STR_SUBSTR,
                new Func
                (
                    substr,new String[] {"\0","\1"},
                    new Block(Type.STR)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            return strStruct(str.substring(((Long)s.getField("\0").value).intValue(),
                                                           ((Long)s.getField("\1").value).intValue()));
                        }
                    }
                )
            )
        );
    }
    /** @return The data from the string struct. */
    @SuppressWarnings("unchecked")
    public static String strData(final Object o)
    {
        return (String)((Map<String,Value>)o).get(" ").value;
    }
    /** @return A string representation of the specified object. */
    @SuppressWarnings("unchecked")
    public static String toString(Object v,final Type t)
    {
        v = resolve(v);
        return switch(t.base)
        {
            case BOOL,INT,FLOAT -> v.toString();
            case STR -> strData(v);
            case FUNC -> t.toString();
            case LIST ->
            {
                final List<Value> v2 = listData(v);
                final Type st = t.subType;
                final StringJoiner sj = new StringJoiner(",","["+st+':',"]");
                for(final Value x : v2)
                    // Use 'x.type.type' instead of 'st' to avoid issues with void lists.
                    sj.add(toString(x.value,x.type.type));
                yield sj.toString();
            }
            case STRUCT ->
            {
                final Map<String,Value> v2 = (Map<String,Value>)v;
                final StringJoiner sj = new StringJoiner(",","{"+t.structName+':',"}");
                for(final Map.Entry<String,Value> e : v2.entrySet())
                    sj.add(e.getKey()+'='+toString(e.getValue().value,e.getValue().type.type));
                yield sj.toString();
            }
            default /* VOID */ -> "void";
        };
    }
    /**
     * Creates a list and pushes it to the stack.
     *
     * <pre>LiteralList := '[' Type ':' [Expr {',' Expr}] ']'</pre>
     *
     * @see Script#type(Context)
     * @see Script#parseExpr(Context)
     */
    private static Instruction list(final Context ctx) throws ScriptException
    {
        // Assume '\[' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // \[ Type : [Expr {, Expr}] \]
        
        // Parse list type.
        final Type subtype = type(ctx);
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COLON))
        {
            skip(ctx.tokens,TokenType.RBRACKET,TokenType.LBRACKET);
            return null;
        }
        // Parse elements.
        int nElem = 0;
        boolean ret = true;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.RBRACKET)) ctx.tokens.next(); // Advance past '\]'.
        else
        {
            // Parse expressions until a non-comma token or an invalid expression is encountered.
            do
            {
                // Parse expression.
                final Instruction i = parseExpr(ctx);
                if(i != null) {if(convert(ctx,i.type,subtype,i.line)) ++nElem;} // Incompatible types don't stop the parsing.
                else
                {
                    ret = false;
                    skip(ctx.tokens,TokenType.RBRACKET,TokenType.LBRACKET);
                    break;
                }
            }
            while(matches(ItrMode.next,ctx.tokens,TokenType.COMMA));
            // Ensure that the loop condition broke on a closing bracket.
            ret = ret && !mismatch(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.RBRACKET);
        }
        if(!ret) return null;
        final int ne = nElem;
        final ConstableType ct = Types.constableType(subtype,false);
        final Instruction i = new Instruction(line,Types.listType(subtype))
        {
            @Override
            Object exec(final RuntimeScope s)
            {
                final Value[] val = new Value[ne];
                for(int i = ne;i-- != 0;) val[i] = new Value(ct,resolve(s.popAccumulator()));
                s.pushAccumulator(listStruct(subtype,new ArrayList<>(Arrays.asList(val))));
                return null;
            }
        };
        ctx.bb.instruction(i,"list "+ne+','+subtype);
        ctx.accPos -= ne-1;
        // Though it would be possible to check for a suffix here, the user would not get any benefit from
        // that functionality.
        return i;
    }
    /**
     * Creates a struct and pushes it to the stack.
     *
     * <pre>LiteralStruct := '{' StructName ':' [Name '=' Expr {',' Name '=' Expr}] '}'</pre>
     *
     * @see Script#type(Context)
     * @see Script#parseExpr(Context)
     */
    private static Instruction struct(final Context ctx) throws ScriptException
    {
        // Assume '\{' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // \{ StructName : [Element = Value { , Element = Value}] \}
        
        // Parse struct type.
        final Map<String,ConstableType> struct;
        final String structName;
        {
            // Get the struct identifier. Nested struct declarations
            // are not allowed, so no '.'s need to be parsed.
            final Token t = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
            structName = t.value();
            if(t.type() != TokenType.ID) struct = null;
            else if((struct = ctx.sc.getStruct(t.value())) == null)
                ctx.reporter.report(t.line(),"Struct '"+t.value()+"' is undefined");
            if(struct == null)
            {
                skip(ctx.tokens,TokenType.RBRACE,TokenType.LBRACE);
                return null;
            }
        }
        // Ensure that there's a colon after the type.
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COLON))
        {
            skip(ctx.tokens,TokenType.RBRACE,TokenType.LBRACE);
            return null;
        }
        // Parse elements.
        final int size = struct.size();
        final String[] ids = new String[size];
        int n = 0;
        boolean ret = true;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.RBRACE)) ctx.tokens.next(); // Advance past '\}'.
        else
        {
            final Set<String> duplicates = new HashSet<>(struct.size());
            do
            {
                // Get an identifier.
                final Token id = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                // Ensure that the token following an identifier is a '='.
                if(id.type() != TokenType.ID || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ASSIGN))
                {
                    skip(ctx.tokens,TokenType.RBRACE,TokenType.LBRACE);
                    return null;
                }
                final boolean flag = duplicates.add(id.value());
                ids[size-n-1] = id.value();
                // Parse expression.
                final Instruction i = parseExpr(ctx);
                if(i != null)
                {
                    final ConstableType ct = struct.get(id.value());
                    if(ct != null)
                    {
                        if(convert(ctx,i.type,ct.type,id.line()))
                        {
                            if(flag) ++n;
                            else
                            {
                                ctx.reporter.warn(id.line(),"Duplicate assignment");
                                // Pop the expression so that it doesn't affect the other assignments.
                                ctx.bb.instruction
                                (
                                    new Instruction(i.line,null)
                                    {
                                        @Override
                                        Object exec(final RuntimeScope s)
                                        {
                                            s.popAccumulator();
                                            return null;
                                        }
                                    },
                                    "pop acc [duplicate assignment]"
                                );
                                --ctx.accPos;
                            }
                            continue;
                        }
                    }
                    else
                        ctx.reporter.report
                        (
                            id.line(),
                            "Field '"+id.value()+
                            "' in struct type '"+structName+
                            "' is undefined"
                        );
                }
                ret = false;
            }
            while(matches(ItrMode.next,ctx.tokens,TokenType.COMMA));
            // Handle missing members.
            if(n != size)
            {
                final Set<String> s = new HashSet<>(Set.copyOf(struct.keySet()));
                s.removeAll(Set.of(ids));
                final StringJoiner sj = new StringJoiner(",");
                for(final String st : s) sj.add(st);
                ctx.reporter.report(line,"Missing definitions for members: "+sj);
                ret = false;
            }
            // Ensure that the loop condition broke on a closing brace.
            ret = ret && !mismatch(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.RBRACE);
        }
        if(ret)
        {
            final ConstableType[] et = new ConstableType[struct.size()];
            for(int j = 0;j < size;++j) // ids array already reversed, don't need to do any special indexing.
                et[j] = struct.get(ids[j]);
            final Instruction i = new Instruction(line,Types.structType(structName))
            {
                @Override
                Object exec(final RuntimeScope s)
                {
                    final Map<String,Value> v = new HashMap<>(size);
                    // Convert the initializer to a literal struct.
                    for(int j = 0;j < size;++j)
                        v.put(ids[j],new Value(et[j],resolve(s.popAccumulator())));
                    s.pushAccumulator(v);
                    return null;
                }
            };
            final StringJoiner sj = new StringJoiner(",",'{'+structName+':',"}");
            for(int j = 0;j < size;++j)
                sj.add(ids[j]+'='+et[j]);
            ctx.bb.instruction(i,"struct "+sj);
            ctx.accPos -= size-1;
            // Though it would be possible to check for a '.' here, the user would not get any benefit from
            // that functionality.
            return i;
        }
        return null;
    }
    /**
     * Creates a function and pushes it to the stack.
     *
     * <pre>LiteralFunc := 'func' '&lt' [Type|'void'] '&gt' '(' [['const'] Type {',' ['const'] Type}] ')'</pre>
     *
     * @see Script#type(Context)
     * @see Script#funcRetType(Context)
     * @see Script#call(Context,Instruction)
     */
    private static Instruction func(final Context ctx) throws ScriptException
    {//TODO maybe functions which have list args/return can be assigned to functions with void list args/return?
        // Assume 'func' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // Must have the form 'func<[Type]>([Type Name {, Type Name}]) \{ Statements \}'
        
        // Parse return type.
        final Type nct = funcRetType(ctx);
        
        // Parse arguments.
        final CompilerScope s = new CompilerScope(ctx.sc);
        final ConstableType[] argt;
        final String[] argn;
        {
            record ArgType(String n,ConstableType t) {}
            final List<ArgType> l = new ArrayList<>();
            if(matches(ItrMode.peek,ctx.tokens,TokenType.RPAREN)) ctx.tokens.next(); // Advance past '\)'.
            else
            {
                // Parse type-name pairs until a non-comma token, invalid type, or invalid name is encountered.
                do
                {
                    // Parse type.
                    final boolean isConst = chkConst(ctx.tokens);
                    final Type act = type(ctx);
                    if(act == null)
                    {
                        skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                        return null;
                    }
                    
                    // Parse name.
                    final Token t = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                    if(t.type() != TokenType.ID)
                    {
                        skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                        return null;
                    }
                    
                    l.add(new ArgType(t.value(),Types.constableType(act,isConst)));
                }
                while(matches(ItrMode.next,ctx.tokens,TokenType.COMMA));
                // Ensure that the loop condition broke on a closing parentheses.
                if(mismatch(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.RPAREN)) return null;
            }
            int i = 0;
            argt = new ConstableType[l.size()];
            argn = new String[l.size()];
            for(final ArgType arg : l)
                s.putField(argn[i  ] = arg.n(),
                           argt[i++] = arg.t());
        }
        
        // Parse body.
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LBRACE)) return null;
        
        final Context nctx = new Context(ctx);
        nctx.bbList.add(nctx.bb = nctx.createBB("func.body"));
        if(!parseBlock(nctx,nct,true)) return null;
        final Block body = nctx.compile();
        final Instruction i = new Instruction(line,Types.funcType(nct,argt))
        {
            @Override
            Object exec(final RuntimeScope s)
            {
                final RuntimeScope ns = new RuntimeScope(s);
                ns.pushScope();
                for(int a = 0;a < argt.length;++a)
                    ns.putField(argn[a],new Value(argt[a],null));
                s.pushAccumulator(new Func(ns,argn,body));
                return null;
            }
        };
        final StringJoiner sj = new StringJoiner(",","func<"+nct+">(",")");
        for(int a = 0;a < argt.length;++a) sj.add(argt[a].toString()+' '+argn[a]);
        ctx.bb.instruction(i,sj.toString());
        ++ctx.accPos;
        return i;
    }
    private static Instruction idPath(final Context ctx,Type startType) throws ScriptException
    {
        // Can be identifier, list index, function call, or field access.
        final List<Token> p = new ArrayList<>();
        // Eat all the identifiers until the '.'s are exhausted.
        // Don't puke afterward, will do with check to suffix.
        do p.add(eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID));
        while(nonEOF(ItrMode.next,ctx.tokens,ctx.reporter).type() == TokenType.DOT);
        // Get the token that broke the loop condition.
        final Token op = ctx.tokens.peek(-1);
        final String[] path;
        final Type ct;
        {
            path = new String[p.size()];
            int i = 0;
            for(final Token s : p)
            {
                // Get the field's members.
                final Map<String,ConstableType> members = getMembers(ctx.sc,startType);
                if(members == null)
                {
                    ctx.reporter.report(s.line(),"Type '"+startType+"' does not have any members");
                    return null;
                }
                // Get member type.
                final ConstableType t2 = members.get(path[i++] = s.value());
                if(t2 == null)
                {
                    // Unknown field detected.
                    ctx.reporter.report
                    (
                        s.line(),
                        "Member '"+s.value()+
                        "' of type "+startType+
                        " is undefined"
                    );
                    return null;
                }
                startType = t2.type;
            }
            ct = startType;
        }
        final int line = op.line();
        final Instruction i = new Instruction(line,ct,true)
        {
            @Override @SuppressWarnings("unchecked")
            Object exec(final RuntimeScope s)
            {
                final Object[] d = s.accumulator.data();
                final int p = s.accumulator.pos()-1;
                Map<String,Value> v = (Map<String,Value>)resolve(d[p]);
                int i;
                for(i = 0;i < path.length-1;++i)
                    v = (Map<String,Value>)v.get(path[i]).value;
                d[p] = v.get(path[i]);
                return null;
            }
        };
        ctx.bb.instruction(i,"readPath "+String.join(".",path));
        return suffix(ctx,i,op);
    }
    /**
     * Gets and pushes an identifier's value to the stack.
     *
     * <pre>Id := Name {'.' Name} [Suffix]</pre>
     *
     * @see Script#suffix(Context,Instruction,Token)
     */
    private static Instruction id(final Context ctx,final Token id) throws ScriptException
    {
        final String start = id.value();
        final ConstableType t = ctx.sc.getField(start);
        final Instruction i = new Instruction(id.line(),t.type,true)
        {
            @Override
            Object exec(final RuntimeScope s)
            {
                s.pushAccumulator(s.getField(start));
                return null;
            }
        };
        ctx.bb.instruction(i,"read "+start);
        ++ctx.accPos;
        return suffix(ctx,i,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
    }
    /** @return {@code true} iff an object of type {@code from} can be converted to an object of type {@code to}. */
    static boolean triviallyConvertible(final Type to,final Type from)
    {
        final BaseType fbt = from.base;
        return switch(to.base)
        {
            case BOOL -> fbt != BaseType.FUNC && fbt != BaseType.VOID;
            case STR -> true;
            case INT,FLOAT -> switch(fbt)
            {
                case INT,FLOAT,BOOL -> true;
                default -> false;
            };
            case FUNC -> fbt == BaseType.FUNC &&
                         from.subType.equals(to.subType) &&
                         compareArgs(to.args,from.args);
            case LIST -> fbt == BaseType.LIST &&
                         (
                             to.subType.base == BaseType.VOID ||
                             triviallyConvertible(to.subType,from.subType)
                         );
            default -> to.equals(from);
        };
    }
    /** @return A {@linkplain Function} which converts an object of type {@code from} to an object of type {@code to}. */
    private static Function<Object,Object> converter(final boolean notRecursed,final ErrReporter reporter,
                                                     final Type from,final Type to,final int line)
    {
        final BaseType fbt = from.base;
        return switch(to.base)
        {
            case BOOL  -> a -> conditional(a,fbt);
            case STR   -> a -> strStruct(toString(a,from));
            case INT   -> fbt == BaseType.BOOL? a -> (Boolean)resolve(a)? 1L : 0L : a -> ((Number)resolve(a)).longValue();
            case FLOAT -> fbt == BaseType.BOOL? a -> (Boolean)resolve(a)? 1D : 0D : a -> ((Number)resolve(a)).doubleValue();
            default /* LIST */  ->
            {
                if(notRecursed)
                    // This warning is only dispatched once so that the user isn't spammed when
                    // nested lists are converted.
                    reporter.warn
                    (
                        line,
                        "Implicit conversion between list types. This will create a deep-copy " +
                        "of all elements, which is very expensive."
                    );
                // The two types are guaranteed to be convertible by calls to 'triviallyConvertible'
                final Type st = to.subType,st1 = from.subType;
                if(st.base == BaseType.VOID)
                    // Don't copy the list because library functions might want to pass-by-value.
                    yield a -> voidList(st1,listData(resolve(a)));
                final ConstableType cst = Types.constableType(st,false);
                final Function<Object,Object> c = converter(false,reporter,st1,st,line);
                yield a ->
                {
                    final List<Value> l1 = listData(resolve(a)),
                                      l2 = new ArrayList<>(l1.size());
                    for(final Value value : l1) l2.add(new Value(cst,c.apply(value.value)));
                    return listStruct(st,l2);
                };
            }
        };
    }
    /**
     * Converts the last item on the stack between types {@code from} and {@code to}. If both types
     * are the same, no instructions are generated.
     *
     * @return {@code true} iff the specified types are {@linkplain Script#triviallyConvertible(Type,Type)}.
     */
    private static boolean convert(final Context ctx,final Type from,final Type to,final int accPos,final int line)
    {
        if(triviallyConvertible(to,from))
        {
            if(!from.equals(to))
            {
                final Function<Object,Object> g = converter(true,ctx.reporter,from,to,line);
                final int rel = accPos+1;
                ctx.bb.instruction
                (
                    new Instruction(line,null)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            final Object[] data = s.accumulator.data();
                            final int i = s.accumulator.pos()-rel;
                            data[i] = g.apply(data[i]);
                            return null;
                        }
                    },
                    "convert ("+accPos+") "+from+" -> "+to
                );
            }
            return true;
        }
        ctx.reporter.report
        (
            line,
            "Cannot convert type "+from+
            " to type "+to
        );
        return false;
    }
    /**
     * Converts the last item on the stack between types {@code from} and {@code to}. If both types
     * are the same, no instructions are generated.
     *
     * @return {@code true} iff the specified types are {@linkplain Script#triviallyConvertible(Type,Type)}.
     */
    private static boolean convert(final Context ctx,final Type from,final Type to,final int line)
    {
        return convert(ctx,from,to,0,line);
    }
    /** @return {@code true} iff a const token was eaten. */
    private static boolean chkConst(final TokenIterator tokens)
    {
        if(matches(ItrMode.peek,tokens,TokenType.CONST))
        {
            tokens.next();
            return true;
        }
        return false;
    }
    /**
     * Evaluates a prefix operator.
     *
     * <pre>Prefix := ('++'|'--'|'+'|'-'|'!'|'~') HighPrecedence</pre>
     *
     * @see Script#highPrecedence(Context)
     */
    private static Instruction prefix(final Context ctx,final Token op) throws ScriptException
    {
        final Instruction i = highPrecedence(ctx);
        if(i == null) return null;
        final Type ct;
        final BaseType ib = i.type.base;
        final int line = op.line();
        final Function<Object,Object> g = switch(op.type())
        {
            case INC    ->
            {
                ctx.sideEffects = true;
                ct = ib == BaseType.INT? Type.INT : Type.FLOAT;
                if((ib != BaseType.INT && ib != BaseType.FLOAT) ||
                   !i.isValueType)
                    yield null;
                yield ib == BaseType.INT
                    ? o ->
                      {
                          final Value v = (Value)o;
                          if(v.type.isConst)
                              throw new ScriptRuntimeException(line,"Cannot modify const value");
                          return v.value = (Long)v.value + 1L;
                      }
                    : o ->
                      {
                          final Value v = (Value)o;
                          if(v.type.isConst)
                              throw new ScriptRuntimeException(line,"Cannot modify const value");
                          return v.value = (Double)v.value + 1D;
                      };
            }
            case DEC    ->
            {
                ctx.sideEffects = true;
                ct = ib == BaseType.INT? Type.INT : Type.FLOAT;
                if((ib != BaseType.INT && ib != BaseType.FLOAT) ||
                   !i.isValueType)
                    yield null;
                yield ib == BaseType.INT
                    ? o ->
                      {
                          final Value v = (Value)o;
                          if(v.type.isConst)
                              throw new ScriptRuntimeException(line,"Cannot modify const value");
                          return v.value = (Long)v.value - 1L;
                      }
                    : o ->
                      {
                          final Value v = (Value)o;
                          if(v.type.isConst)
                              throw new ScriptRuntimeException(line,"Cannot modify const value");
                          return v.value = (Double)v.value - 1D;
                      };
            }
            case ADD    -> switch(ib)
            {
                case BOOL      ->
                {
                    ct = Type.INT;
                    yield o -> (Boolean)resolve(o)? 1L:0L;
                }
                case INT,FLOAT ->
                {
                    ct = i.type;
                    yield Script::resolve;
                }
                default        -> {ct = null; yield null;}
            };
            case SUB    -> switch(ib)
            {
                case BOOL  ->
                {
                    ct = Type.INT;
                    yield o -> (Boolean)resolve(o)? -1L:0L;
                }
                case INT   ->
                {
                    ct = Type.INT;
                    yield o -> -(Long)resolve(o);
                }
                case FLOAT ->
                {
                    ct = Type.FLOAT;
                    yield o -> -(Double)resolve(o);
                }
                default    -> {ct = null; yield null;}
            };
            case NOT    -> triviallyConvertible(ct = Type.BOOL,i.type)
                ? o -> !conditional(resolve(o),ib)
                : null;
            case BITNOT -> switch(ib)
            {
                case BOOL  ->
                {
                    ct = Type.INT;
                    yield o -> conditional(resolve(o),ib)? ~1L:-1L;
                }
                case INT   ->
                {
                    ct = Type.INT;
                    yield o -> ~(Long)resolve(o);
                }
                case FLOAT ->
                {
                    ct = Type.FLOAT;
                    yield o -> ltod(~dtol((Double)resolve(o)));
                }
                default    -> {ct = null; yield null;}
            };
            default     -> {ct = null; yield null;}
        };
        if(g == null)
        {
            ctx.reporter.report
            (
                i.line,
                "Cannot use operator "+op.type()+
                " on a "+(i.isValueType? "non-":"")+
                "literal "+i.type
            );
            return null;
        }
        final Instruction o = new Instruction(line,ct)
        {
            @Override
            Object exec(final RuntimeScope s)
            {
                final Object[] d = s.accumulator.data();
                final int p = s.accumulator.pos()-1;
                d[p] = g.apply(d[p]);
                return null;
            }
        };
        ctx.bb.instruction(o,"prefix "+op.value());
        return o;
    }
    /**
     * Evaluates a suffix operator.
     *
     * <pre>Suffix := (('[' Expr ']' [Suffix])|(Call [Suffix])|'++'|'--'|('.' Id))</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#call(Context,Instruction)
     */
    private static Instruction suffix(final Context ctx,final Instruction expr,final Token op) throws ScriptException
    {
        return switch(op.type())
        {
            case LBRACKET ->
            {
                // List index detected.
                if(expr.type.base != BaseType.LIST)
                    ctx.reporter.report(op.line(),"Cannot use index-of operator on type '"+expr.type+'\'');
                else if(expr.type.subType.base == BaseType.VOID)
                    ctx.reporter.report(op.line(),"Cannot access elements from void lists");
                // Parse index expression.
                final Instruction idx = parseExpr(ctx);
                if(idx == null || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RBRACKET))
                {
                    skip(ctx.tokens,TokenType.RBRACKET,TokenType.LBRACKET);
                    yield null;
                }
                convert(ctx,idx.type,Type.INT,idx.line);
                final String module = ctx.module;
                final Instruction i = new Instruction(op.line(),expr.type.subType,true)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        final int i;
                        {
                            final long l = (Long)resolve(s.popAccumulator());
                            if(l < 0L || l >= Integer.MAX_VALUE)
                                // If some sort of exception handling is added in the future, the accumulator
                                // should be popped so that it's in a defined state.
                                // s.popAccumulator();
                                throw new ScriptRuntimeException(line,module,"Index " + l + " is out of bounds");
                            i = (int)l;
                        }
                        final Object[] d = s.accumulator.data();
                        final int p = s.accumulator.pos()-1;
                        d[p] = listData(resolve(d[p])).get(i);
                        return null;
                    }
                };
                --ctx.accPos;
                ctx.bb.instruction(i,"listAccess");
                yield suffix(ctx,i,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            case LPAREN   ->
            {
                // Call detected.
                if(expr.type.base != BaseType.FUNC)
                    ctx.reporter.report(op.line(),"Cannot use function call operator on type '"+expr.type+'\'');
                final Instruction c = call(ctx,expr);
                if(c == null) yield null;
                yield suffix(ctx,c,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            case INC,DEC  ->
            {
                if(expr.type.base != BaseType.INT && expr.type.base != BaseType.FLOAT)
                    ctx.reporter.report
                    (
                        expr.line,
                        "Cannot "+(op.type() == TokenType.INC? "increment":"decrement")+
                        " type "+expr.type
                    );
                if(expr.isValueType)
                {
                    final Function<Object,Object> g = expr.type.base == BaseType.INT
                        ? (op.type() == TokenType.INC? x ->   (Long)x + 1L : x ->   (Long)x - 1L)
                        : (op.type() == TokenType.INC? x -> (Double)x + 1D : x -> (Double)x - 1D);
                    final Instruction i = new Instruction(expr.line,expr.type,false)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            final Value v = (Value)s.accumulator.data()[s.accumulator.pos()-1];
                            if(v.type.isConst)
                                throw new ScriptRuntimeException(line,"Cannot modify const value");
                            v.value = g.apply(v.value);
                            return null;
                        }
                    };
                    ctx.bb.instruction(i,"suffix "+op.value());
                    ctx.sideEffects = true;
                    yield i;
                }
                ctx.reporter.report
                (
                    expr.line,
                    "Cannot "+(op.type() == TokenType.INC? "increment":"decrement")+
                    " a literal value"
                );
                ctx.sideEffects = true;
                yield null;
            }
            case DOT      -> idPath(ctx,expr.type);
            default       ->
            {
                // No operator found, puke the operator and return.
                ctx.tokens.previous();
                yield expr;
            }
        };
    }
    /**
     * Evaluates a function call expression.
     *
     * <pre>Call := '(' [Expr {',' Expr }] ')'</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#func(Context)
     */
    private static Instruction call(final Context ctx,final Instruction func) throws ScriptException
    {
        // Assume '(' already eaten.
        // Parse arguments.
        final ConstableType[] argt = func.type.args;
        final int argc = argt.length;
        {
            final Type[] args = new Type[argc];
            boolean flag = true,flag2 = true;
            int a;
            // Parse 'expr,' patterns.
            for(a = 0;a < argc - 1;++a)
            {
                final Instruction e = parseExpr(ctx);
                if(e == null || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COMMA))
                {
                    flag = false;
                    skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                    break;
                }
                flag2 = flag2 && convert(ctx,args[a] = e.type,argt[a].type,e.line);
            }
            final List<Type> extra = new ArrayList<>();
            if(flag)
            {
                // Parse last argument
                if(argc != 0)
                {
                    final Instruction e = parseExpr(ctx);
                    if(e == null) flag = false;
                    else flag2 = flag2 && convert(ctx,args[a] = e.type,argt[a].type,e.line);
                }
                // Handle extra args if necessary.
                if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN))
                    do
                    {
                        final Instruction ex = parseExpr(ctx);
                        extra.add(ex == null? null : ex.type);
                    }
                    while(!matches(ItrMode.next,ctx.tokens,TokenType.RPAREN));
            }
            // Ensure types are correct.
            if(!flag || !flag2 || !extra.isEmpty())
            {
                // Emit error.
                final StringJoiner sj = new StringJoiner(",","(",")");
                for(final Type e : args) sj.add(e == null? "ERR":e.toString());
                for(final Type e : extra) sj.add(e == null? "ERR":e.toString());
                ctx.reporter.report
                (
                    func.line,
                    "Invalid arguments for function type '"+func.type+
                    "', got '"+sj+'\''
                );
                ctx.sideEffects = true;
                return null;
            }
        }
        final boolean nonVoidRet = func.type.subType.base != BaseType.VOID;
        final Instruction i = new Instruction(func.line,func.type.subType)
        {
            @Override
            Object exec(final RuntimeScope s)
            {
                final Object[] o = new Object[argc];
                for(int i = argc;i-- != 0;)
                    o[i] = resolve(s.popAccumulator());
                final Func f = (Func)resolve(s.popAccumulator());
                final RuntimeScope fs = f.scope;
                final String[] argn = f.argn;
                for(int i = 0;i != argc;++i)
                    fs.setFieldValue(argn[i],o[i]);
                final Object ret = f.body.exec(fs);
                if(nonVoidRet) s.pushAccumulator(ret);
                return null;
            }
        };
        ctx.bb.instruction(i,"call "+func.type);
        ctx.accPos -= argc + (nonVoidRet? 0:1);
        ctx.sideEffects = true;
        return i;
    }
    /**
     * Parses a type token.
     *
     * <pre>
     *       Type := (Primitive|FuncType|StructName) {'[]'}
     *  Primitive := ('bool'|'int'|'float'|'str'|('void' '[' ']'))
     *   FuncType := 'func' '&lt' [Type|'void'] '&gt' '(' [['const'] Type {',' ['const'] Type}] ')'
     * StructName := any Name which isn't a Primitive or a keyword (e.g. 'if', 'for', etc.)
     * </pre>
     *
     * @see Script#funcRetType(Context)
     */
    private static Type type(final Context ctx,final boolean funcRet)
    {
        // A type must be a name followed by any number of empty bracket pairs.
        final Token t = eat(ItrMode.next,ctx.tokens,ctx.reporter,
                            TokenType.BOOL,TokenType.INT,TokenType.FLOAT,
                            TokenType.STR,TokenType.ID,TokenType.FUNC,
                            TokenType.VOID);
        Type ct = switch(t.type())
        {
            case VOID  -> Type.VOID;
            case BOOL  -> Type.BOOL;
            case INT   -> Type.INT;
            case FLOAT -> Type.FLOAT;
            case STR   -> Type.STR;
            case ID    ->
            {
                if(ctx.sc.getStruct(t.value()) == null)
                {
                    ctx.reporter.report(t.line(),"Struct type '"+t.value()+"' is undefined");
                    yield null;
                }
                yield Types.structType(t.value());
            }
            case FUNC  ->
            {
                // Function detected, eat another type.
                // Must have the form 'func<...>(...)[][]...'
                final Type nct = funcRetType(ctx);
                final ConstableType[] args;
                {
                    final List<ConstableType> l = new ArrayList<>();
                    if(matches(ItrMode.peek,ctx.tokens,TokenType.RPAREN)) ctx.tokens.next(); // Advance past '\)'.
                    else
                    {
                        do
                        {
                            // Parse type.
                            final boolean isConst = chkConst(ctx.tokens);
                            final Type act = type(ctx);
                            if(act == null)
                            {
                                skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                                yield null;
                            }
                            l.add(Types.constableType(act,isConst));
                        }
                        while(matches(ItrMode.next,ctx.tokens,TokenType.COMMA));
                        // Ensure that the loop condition broke on a closing parentheses.
                        if(mismatch(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.RPAREN))
                            yield null;
                    }
                    args = l.toArray(ConstableType[]::new);
                }
                yield Types.funcType(nct,args);
            }
            default    -> null;
        };
        if(ct != null)
        {
            if(matches(ItrMode.peek,ctx.tokens,TokenType.LBRACKET))
                // For list types, nest list types until there are no more brackets.
                do {eat(ItrMode.advance,ctx.tokens,ctx.reporter,TokenType.RBRACKET); ct = Types.listType(ct);}
                while(matches(ItrMode.advance,ctx.tokens,TokenType.LBRACKET));
            else if(ct.base == BaseType.VOID && !funcRet)
                ctx.reporter.report(t.line(),"Non-list void types are not allowed");
        }
        return ct;
    }
    /**
     * Parses a type token.
     *
     * <pre>
     *       Type := (Primitive|FuncType|StructName) {'[]'}
     *  Primitive := ('bool'|'int'|'float'|'str'|('void' '[' ']'))
     *   FuncType := 'func' '&lt' [Type|'void'] '&gt' '(' [['const'] Type {',' ['const'] Type}] ')'
     * StructName := any Name which isn't a Primitive or a keyword (e.g. 'if', 'for', etc.)
     * </pre>
     *
     * @see Script#funcRetType(Context)
     */
    private static Type type(final Context ctx) {return type(ctx,false);}
    /**
     * Parses a function return type.
     *
     * <pre>FuncType := 'func' '&lt' [Type|'void'] '&gt' '(' [['const'] Type {',' ['const'] Type}] ')'</pre>
     *
     * @see Script#type(Context)
     * @see Script#func(Context)
     */
    private static Type funcRetType(final Context ctx)
    {
        // Assume 'func' already eaten.
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LT))
        {
            skip(ctx.tokens,TokenType.GT,TokenType.LT);
            return null;
        }
        final Type ct;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.GT))
        {
            ctx.tokens.next(); // Eat '>'.
            ct = Type.VOID;
        }
        else
        {
            if((ct = type(ctx,true)) == null) return null;
            if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.GT))
            {
                skip(ctx.tokens,TokenType.GT,TokenType.LT);
                return null;
            }
        }
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN))
        {
            skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
            return null;
        }
        return ct;
    }
    /**
     * Evaluates a high-precedence expression.
     *
     * <pre>HighPrecedence := LiteralBool
     *                      | LiteralNum
     *                      | LiteralStr
     *                      | LiteralFunc [Call]
     *                      | LiteralList
     *                      | LiteralStruct
     *                      | Prefix
     *                      | Id
     *                      | '(' Expr ')'</pre>
     *
     * @see Script#func(Context)
     * @see Script#call(Context,Instruction)
     * @see Script#list(Context)
     * @see Script#struct(Context)
     * @see Script#prefix(Context,Token)
     * @see Script#id(Context,Token)
     * @see Script#parseExpr(Context)
     */
    private static Instruction highPrecedence(final Context ctx) throws ScriptException
    {
        Token t = nonEOF(ItrMode.next,ctx.tokens,ctx.reporter);
        final int line = t.line();
        return switch(t.type())
        {
            case TRUE,FALSE ->
            {
                final boolean b = Boolean.parseBoolean(t.value());
                final Instruction i = new Instruction(line,Type.BOOL)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.pushAccumulator(b);
                        return null;
                    }
                };
                ctx.bb.instruction(i,"pushAcc "+t.value());
                ++ctx.accPos;
                yield i;
            }
            case LIT_INT ->
            {
                String v = t.value();
                int radix = 10;
                if(v.charAt(0) == '0' && v.length() != 1)
                {
                    if(v.charAt(1) == 'x' || v.charAt(1) == 'X')
                        radix = 16;
                    else if(v.charAt(1) == 'b' || v.charAt(1) == 'B')
                        radix = 2;
                }
                if(radix != 10) v = v.substring(2);
                final long l = Long.parseLong(v,radix);
                final Instruction i = new Instruction(line,Type.INT)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.pushAccumulator(l);
                        return null;
                    }
                };
                ctx.bb.instruction(i,"pushAcc "+t.value());
                ++ctx.accPos;
                yield i;
            }
            case LIT_FLOAT ->
            {
                final double d = Double.parseDouble(t.value());
                final Instruction i = new Instruction(line,Type.FLOAT)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.pushAccumulator(d);
                        return null;
                    }
                };
                ctx.bb.instruction(i,"pushAcc "+t.value());
                ++ctx.accPos;
                yield i;
            }
            case LIT_STR ->
            {
                final Map<String,Value> str = strStruct(t.value());
                final Instruction i = new Instruction(line,Type.STR)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.pushAccumulator(str);
                        return null;
                    }
                };
                ctx.bb.instruction(i,"pushAcc "+t.value());
                ++ctx.accPos;
                yield suffix(ctx,i,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            case FUNC ->
            {
                final Instruction o = func(ctx);
                if(o != null && nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type() == TokenType.LPAREN)
                {
                    // Eat '\('.
                    final Token tt = ctx.tokens.next();
                    ctx.reporter.warn
                    (
                        tt.line(),
                        "Anonymous Function: Creating a function is expensive. Consider moving its code " +
                        "elsewhere."
                    );
                    // Parse anonymous function call.
                    yield suffix(ctx,o,tt);
                }
                yield o;
            }
            case LBRACKET -> list(ctx);
            case LBRACE -> struct(ctx);
            case ID -> id(ctx,t);
            case INC,DEC,ADD,SUB,NOT,BITNOT -> prefix(ctx,t);
            case LPAREN ->
            {
                // Try to get a sub-expression.
                final Instruction i =
                assignment
                (ctx,
                    ternary
                    (ctx,
                        math
                        (ctx,
                            highPrecedence(ctx)
                        )
                    )
                );
                yield mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN)? null : i;
            }
            default -> null;
        };
    }
    /** A representation of a binary operator and its return type. */
    private static record Operation(BiFunction<Object,Object,Object> op,Type ret) {}
    /** Evaluates a binary operator from two arguments in the stack.*/
    private static Operation getOp(final Type a,final Type b,final TokenType type,
                                   final Context ctx,final int line)
    {
        final BiFunction<Object,Object,Object> op;
        final BaseType abt = a.base,bbt = b.base;
        final Type ct = switch(type)
        {
            case AND,OR,EQ,NEQ,
                 GT,LT,GEQ,LEQ   ->
            {
                op = switch(type)
                {
                    case OR,AND -> convert(ctx,a,Type.BOOL,1,line) && convert(ctx,b,Type.BOOL,line)? type == TokenType.OR
                        ? (x,y) -> (Boolean)resolve(x) || (Boolean)resolve(y)
                        : (x,y) -> (Boolean)resolve(x) && (Boolean)resolve(y)
                        : null;
                    case EQ  -> (x,y) ->  equals(x,a,y,b);
                    case NEQ -> (x,y) -> !equals(x,a,y,b);
                    default  -> // GT,LT,GEQ,LEQ
                    {
                        // Void, struct, and function types aren't comparable.
                        // String and list types are only comparable to objects of the same type.
                        // Number and boolean types are comparable to themselves and each other.
                        final BiFunction<Object,Object,Object> o = switch(abt)
                        {
                            case LIST  -> bbt != BaseType.LIST? null : switch(type)
                            {
                                case GT  -> (x,y) -> listData(resolve(x)).size() >
                                                     listData(resolve(y)).size();
                                case LT  -> (x,y) -> listData(resolve(x)).size() <
                                                     listData(resolve(y)).size();
                                case GEQ -> (x,y) -> listData(resolve(x)).size() >=
                                                     listData(resolve(y)).size();
                                case LEQ -> (x,y) -> listData(resolve(x)).size() <=
                                                     listData(resolve(y)).size();
                                default  -> null;
                            };
                            case STR   -> bbt != BaseType.STR? null : switch(type)
                            {
                                case GT  -> (x,y) -> strData(resolve(x)).compareTo(strData(resolve(y))) >  0;
                                case LT  -> (x,y) -> strData(resolve(x)).compareTo(strData(resolve(y))) <  0;
                                case GEQ -> (x,y) -> strData(resolve(x)).compareTo(strData(resolve(y))) >= 0;
                                case LEQ -> (x,y) -> strData(resolve(x)).compareTo(strData(resolve(y))) <= 0;
                                default  -> null;
                            };
                            case BOOL  -> switch(bbt)
                            {
                                case BOOL  -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Boolean)resolve(x) &&
                                                        !(Boolean)resolve(y);
                                    case LT  -> (x,y) ->!(Boolean)resolve(x) &&
                                                         (Boolean)resolve(y);
                                    case GEQ -> (x,y) -> (Boolean)resolve(x) ||
                                                        !(Boolean)resolve(y);
                                    case LEQ -> (x,y) ->!(Boolean)resolve(x) ||
                                                         (Boolean)resolve(y);
                                    default  -> null;
                                };
                                case INT   -> switch(type)
                                {
                                    case GT  -> (x,y) -> ((Boolean)resolve(x)? 1L:0L) >
                                                             (Long)resolve(y);
                                    case LT  -> (x,y) -> ((Boolean)resolve(x)? 1L:0L) <
                                                             (Long)resolve(y);
                                    case GEQ -> (x,y) -> ((Boolean)resolve(x)? 1L:0L) >=
                                                             (Long)resolve(y);
                                    case LEQ -> (x,y) -> ((Boolean)resolve(x)? 1L:0L) <=
                                                             (Long)resolve(y);
                                    default  -> null;
                                };
                                case FLOAT -> switch(type)
                                {
                                    case GT  -> (x,y) -> ((Boolean)resolve(x)? 1D:0D) >
                                                           (Double)resolve(y);
                                    case LT  -> (x,y) -> ((Boolean)resolve(x)? 1D:0D) <
                                                           (Double)resolve(y);
                                    case GEQ -> (x,y) -> ((Boolean)resolve(x)? 1D:0D) >=
                                                           (Double)resolve(y);
                                    case LEQ -> (x,y) -> ((Boolean)resolve(x)? 1D:0D) <=
                                                           (Double)resolve(y);
                                    default  -> null;
                                };
                                default    -> null;
                            };
                            case INT   -> switch(bbt)
                            {
                                case BOOL  -> switch(type)
                                {
                                    case GT  -> (x,y) ->     (Long)resolve(x)         >
                                                         ((Boolean)resolve(y)? 1L:0L);
                                    case LT  -> (x,y) ->     (Long)resolve(x)         <
                                                         ((Boolean)resolve(y)? 1L:0L);
                                    case GEQ -> (x,y) ->     (Long)resolve(x)         >=
                                                         ((Boolean)resolve(y)? 1L:0L);
                                    case LEQ -> (x,y) ->     (Long)resolve(x)         <=
                                                         ((Boolean)resolve(y)? 1L:0L);
                                    default  -> null;
                                };
                                case INT   -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Long)resolve(x) >  (Long)resolve(y);
                                    case LT  -> (x,y) -> (Long)resolve(x) <  (Long)resolve(y);
                                    case GEQ -> (x,y) -> (Long)resolve(x) >= (Long)resolve(y);
                                    case LEQ -> (x,y) -> (Long)resolve(x) <= (Long)resolve(y);
                                    default  -> null;
                                };
                                case FLOAT -> switch(type)
                                {
                                    case GT  -> (x,y) -> ((Long)resolve(x)).doubleValue() >  (Double)resolve(y);
                                    case LT  -> (x,y) -> ((Long)resolve(x)).doubleValue() <  (Double)resolve(y);
                                    case GEQ -> (x,y) -> ((Long)resolve(x)).doubleValue() >= (Double)resolve(y);
                                    case LEQ -> (x,y) -> ((Long)resolve(x)).doubleValue() <= (Double)resolve(y);
                                    default  -> null;
                                };
                                default    -> null;
                            };
                            case FLOAT -> switch(bbt)
                            {
                                case BOOL  -> switch(type)
                                {
                                    case GT  -> (x,y) ->   (Double)resolve(x)         >
                                                         ((Boolean)resolve(y)? 1D:0D);
                                    case LT  -> (x,y) ->   (Double)resolve(x)         <
                                                         ((Boolean)resolve(y)? 1D:0D);
                                    case GEQ -> (x,y) ->   (Double)resolve(x)         >=
                                                         ((Boolean)resolve(y)? 1D:0D);
                                    case LEQ -> (x,y) ->   (Double)resolve(x)         <=
                                                         ((Boolean)resolve(y)? 1D:0D);
                                    default  -> null;
                                };
                                case INT   -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Double)resolve(x) >  ((Long)resolve(y)).doubleValue();
                                    case LT  -> (x,y) -> (Double)resolve(x) <  ((Long)resolve(y)).doubleValue();
                                    case GEQ -> (x,y) -> (Double)resolve(x) >= ((Long)resolve(y)).doubleValue();
                                    case LEQ -> (x,y) -> (Double)resolve(x) <= ((Long)resolve(y)).doubleValue();
                                    default  -> null;
                                };
                                case FLOAT -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Double)resolve(x) >  (Double)resolve(y);
                                    case LT  -> (x,y) -> (Double)resolve(x) <  (Double)resolve(y);
                                    case GEQ -> (x,y) -> (Double)resolve(x) >= (Double)resolve(y);
                                    case LEQ -> (x,y) -> (Double)resolve(x) <= (Double)resolve(y);
                                    default  -> null;
                                };
                                default    -> null;
                            };
                            default    -> null;
                        };
                        if(o == null)
                            ctx.reporter.report
                            (
                                line,
                                "Expression of type "+abt+
                                " cannot be compared to type "+bbt
                            );
                        yield o;
                    }
                };
                yield Type.BOOL;
            }
            case ADD             ->
            {
                if(abt == BaseType.STR || bbt == BaseType.STR)
                {
                    op = (x,y) -> strStruct(toString(x,a)+toString(y,b));
                    yield Type.STR;
                }
                yield switch(abt)
                {
                    case BOOL,INT,FLOAT -> switch(bbt)
                    {
                        case BOOL,INT,FLOAT ->
                        {
                            op = switch(abt)
                            {
                                case BOOL         -> switch(bbt)
                                {
                                    case BOOL         -> (x,y) -> ((Boolean)resolve(x)?1L:0L)+
                                                                  ((Boolean)resolve(y)?1L:0L);
                                    case INT          -> (x,y) -> ((Boolean)resolve(x)?1L:0L)+
                                                                      (Long)resolve(y);
                                    default /*FLOAT*/ -> (x,y) -> ((Boolean)resolve(x)?1D:0D)+
                                                                    (Double)resolve(y);
                                };
                                case INT          -> switch(bbt)
                                {
                                    case BOOL         -> (x,y) ->     (Long)resolve(x) +
                                                                  ((Boolean)resolve(y)?1L:0L);
                                    case INT          -> (x,y) ->     (Long)resolve(x) +
                                                                      (Long)resolve(y);
                                    default /*FLOAT*/ -> (x,y) ->     (Long)resolve(x) +
                                                                    (Double)resolve(y);
                                };
                                default /*FLOAT*/ -> switch(bbt)
                                {
                                    case BOOL         -> (x,y) ->   (Double)resolve(x) +
                                                                  ((Boolean)resolve(y)?1D:0D);
                                    case INT          -> (x,y) ->   (Double)resolve(x) +
                                                                      (Long)resolve(y);
                                    default /*FLOAT*/ -> (x,y) ->   (Double)resolve(x) +
                                                                    (Double)resolve(y);
                                };
                            };
                            //noinspection DuplicateExpressions
                            yield abt == BaseType.FLOAT || bbt == BaseType.FLOAT
                                ? Type.FLOAT
                                : Type.INT;
                        }
                        default ->
                        {
                            ctx.reporter.report(line,"Cannot add type "+abt+" to type "+bbt);
                            op = null;
                            yield null;
                        }
                    };
                    case LIST ->
                    {
                        if(bbt == BaseType.LIST)
                        {
                            if(getElemType(a.subType) == BaseType.VOID)
                            {
                                // Although it is technically possible to do type checking on the previous
                                // type of the list, reporting an error outright will discourage users from
                                // making their code more obtuse by using void lists everywhere.
                                ctx.reporter.report(line,"Cannot concatenate void lists");
                                op = null;
                                yield null;
                            }
                            if(!a.subType.equals(b.subType))
                            {
                                ctx.reporter.report
                                (
                                    line,
                                    "Cannot add list of type "+b.subType+
                                    " to list of type "+a.subType
                                );
                                op = null;
                                yield null;
                            }
                            op = (x,y) ->
                            {
                                final List<Value> l = new ArrayList<>(listData(resolve(x)));
                                l.addAll(listData(resolve(y)));
                                return listStruct(a,l);
                            };
                            yield a;
                        }
                        else
                        {
                            ctx.reporter.report(line,"Cannot add type list and type "+bbt);
                            op = null;
                            yield null;
                        }
                    }
                    default ->
                    {
                        ctx.reporter.report(line,"Cannot add type "+abt+" to type "+bbt);
                        op = null;
                        yield null;
                    }
                };
            }
            case SUB,MUL,DIV,MOD,
                 BITAND,BITOR,
                 BITXOR,LSH,RSH,
                 LRSH            ->
            {
                op = switch(abt)
                {
                    case BOOL  -> switch(bbt)
                    {
                        case BOOL  -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Boolean)resolve(x)?1L:0L) -
                                                    ((Boolean)resolve(y)?1L:0L);
                            case MUL,
                                 BITAND -> (x,y) -> (Boolean)resolve(x)&&(Boolean)resolve(y)?1L:0L;
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    if(!(Boolean)resolve(y)) throw new ScriptRuntimeException(line,module,"Divide by zero");
                                    return (Boolean)resolve(x)?1L:0L;
                                };
                            }
                            case MOD    -> (x,y) -> 0L;
                            case BITOR  -> (x,y) -> (Boolean)resolve(x)||(Boolean)resolve(y)?1L:0L;
                            case BITXOR -> (x,y) ->          resolve(x)!=         resolve(y)?1L:0L;
                            case LSH    -> (x,y) -> (Boolean)resolve(x)?(Boolean)resolve(y)?2L:1L:0L;
                            case RSH,
                                 LRSH   -> (x,y) -> (Boolean)resolve(x)&&!(Boolean)resolve(y)?1L:0L;
                            default     -> null;
                        };
                        case INT   -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Boolean)resolve(x)?1L:0L) -
                                                        (Long)resolve(y);
                            case MUL    -> (x,y) -> (Boolean)resolve(x)?(Long)resolve(y):0L;
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    try {return ((Boolean)resolve(x)?1L:0L)/(Long)resolve(y);}
                                    catch(final ArithmeticException e) {throw new ScriptRuntimeException(line,module,"Divide by zero");}
                                };
                            }
                            case MOD    -> (x,y) -> ((Boolean)resolve(x)?1L:0L) % (Long)resolve(y);
                            case BITAND -> (x,y) -> (Boolean)resolve(x)?(Long)resolve(y)&1L:0L;
                            case BITOR  -> (x,y) -> (Long)resolve(y)|((Boolean)resolve(x)?1L:0L);
                            case BITXOR -> (x,y) -> (Long)resolve(y)^((Boolean)resolve(x)?1L:0L);
                            case LSH    -> (x,y) -> ((Boolean)resolve(x)?1L:0L)<<(Long)resolve(y);
                            case RSH,
                                 LRSH   -> (x,y) -> ((Boolean)resolve(x)?1L:0L)>>>(Long)resolve(y);
                            default     -> null;
                        };
                        case FLOAT -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Boolean)resolve(x)?1D:0D) -
                                                      (Double)resolve(y);
                            case MUL    -> (x,y) -> (Boolean)resolve(x)?(Double)resolve(y):0D;
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    try {return (Boolean)resolve(x)?1D:0D/(Double)resolve(y);}
                                    catch(final ArithmeticException e) {throw new ScriptRuntimeException(line,module,"Divide by zero");}
                                };
                            }
                            case MOD    -> (x,y) -> ((Boolean)resolve(x)?1D:0D) % (Double)resolve(y);
                            case BITAND -> (x,y) -> ltod((Boolean)resolve(x)? dtol((Double)resolve(y))&1L:0L);
                            case BITOR  -> (x,y) -> ltod(dtol((Double)resolve(y))|((Boolean)resolve(x)?1L:0L));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)resolve(y))^((Boolean)resolve(x)?1L:0L));
                            case LSH    -> (x,y) -> (double)(((Boolean)resolve(x)?1L:0L)<<((Double)resolve(y)).longValue());
                            case RSH,
                                 LRSH   -> (x,y) -> (double)(((Boolean)resolve(x)?1L:0L)>>>((Double)resolve(y)).longValue());
                            default     -> null;
                        };
                        default    -> null;
                    };
                    case INT   -> switch(bbt)
                    {
                        case BOOL  -> switch(type)
                        {
                            case SUB    -> (x,y) ->     (Long)resolve(x) -
                                                    ((Boolean)resolve(y)?1L:0L);
                            case MUL    -> (x,y) -> (Boolean)resolve(y)?(Long)resolve(x):0L;
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    if(!(Boolean)resolve(y)) throw new ScriptRuntimeException(line,module,"Divide by zero");
                                    return resolve(x);
                                };
                            }
                            case MOD    -> (x,y) -> 0L;
                            case BITAND -> (x,y) -> (Boolean)resolve(y)?      (Long)resolve(x)&1L:0L;
                            case BITOR  -> (x,y) ->    (Long)resolve(x)|  ((Boolean)resolve(y)?1L:0L);
                            case BITXOR -> (x,y) ->    (Long)resolve(x)^  ((Boolean)resolve(y)?1L:0L);
                            case LSH    -> (x,y) ->    (Long)resolve(x)<< ((Boolean)resolve(y)?1L:0L);
                            case RSH    -> (x,y) ->    (Long)resolve(x)>> ((Boolean)resolve(y)?1L:0L);
                            case LRSH   -> (x,y) ->    (Long)resolve(x)>>>((Boolean)resolve(y)?1L:0L);
                            default     -> null;
                        };
                        case INT   -> switch(type)
                        {
                            case SUB    -> (x,y) -> (Long)resolve(x) - (Long)resolve(y);
                            case MUL    -> (x,y) -> (Long)resolve(x) * (Long)resolve(y);
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    try {return (Long)resolve(x)/(Long)resolve(y);}
                                    catch(final ArithmeticException e) {throw new ScriptRuntimeException(line,module,"Divide by zero");}
                                };
                            }
                            case MOD    -> (x,y) -> (Long)resolve(x) % (Long)resolve(y);
                            case BITAND -> (x,y) -> (Long)resolve(x) & (Long)resolve(y);
                            case BITOR  -> (x,y) -> (Long)resolve(x) | (Long)resolve(y);
                            case BITXOR -> (x,y) -> (Long)resolve(x) ^ (Long)resolve(y);
                            case LSH    -> (x,y) -> (Long)resolve(x) <<(Long)resolve(y);
                            case RSH    -> (x,y) -> (Long)resolve(x) >>(Long)resolve(y);
                            case LRSH   -> (x,y) -> (Long)resolve(x)>>>(Long)resolve(y);
                            default     -> null;
                        };
                        case FLOAT -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Long)resolve(x)).doubleValue() - (Double)resolve(y);
                            case MUL    -> (x,y) -> ((Long)resolve(x)).doubleValue() * (Double)resolve(y);
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    try {return ((Long)resolve(x)).doubleValue()/(Double)resolve(y);}
                                    catch(final ArithmeticException e) {throw new ScriptRuntimeException(line,module,"Divide by zero");}
                                };
                            }
                            case MOD    -> (x,y) -> ((Long)resolve(x)).doubleValue() % (Double)resolve(y);
                            case BITAND -> (x,y) -> ltod((Long)resolve(x) & dtol((Double)resolve(y)));
                            case BITOR  -> (x,y) -> ltod((Long)resolve(x) | dtol((Double)resolve(y)));
                            case BITXOR -> (x,y) -> ltod((Long)resolve(x) ^ dtol((Double)resolve(y)));
                            case LSH    -> (x,y) -> (double)((Long)resolve(x) <<((Double)resolve(y)).longValue());
                            case RSH    -> (x,y) -> (double)((Long)resolve(x) >>((Double)resolve(y)).longValue());
                            case LRSH   -> (x,y) -> (double)((Long)resolve(x)>>>((Double)resolve(y)).longValue());
                            default     -> null;
                        };
                        default    -> null;
                    };
                    case FLOAT -> switch(bbt)
                    {
                        case BOOL  -> switch(type)
                        {
                            case SUB    -> (x,y) ->  (Double)resolve(x) -
                                                   ((Boolean)resolve(y)?1D:0D);
                            case MUL    -> (x,y) -> (Boolean)resolve(y)?(Double)resolve(x):0D;
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    if(!(Boolean)resolve(y)) throw new ScriptRuntimeException(line,module,"Divide by zero");
                                    return resolve(x);
                                };
                            }
                            case MOD    -> (x,y) -> (Double)resolve(x)%((Boolean)resolve(y)?1D:0D);
                            case BITAND -> (x,y) -> ltod((Boolean)resolve(y)?   dtol((Double)resolve(x))&1L:0L);
                            case BITOR  -> (x,y) -> ltod(dtol((Double)resolve(x))|  ((Boolean)resolve(y)?1L:0L));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)resolve(x))^  ((Boolean)resolve(y)?1L:0L));
                            case LSH    -> (x,y) -> ltod(dtol((Double)resolve(x))<< ((Boolean)resolve(y)?1L:0L));
                            case RSH    -> (x,y) -> ltod(dtol((Double)resolve(x))>> ((Boolean)resolve(y)?1L:0L));
                            case LRSH   -> (x,y) -> ltod(dtol((Double)resolve(x))>>>((Boolean)resolve(y)?1L:0L));
                            default     -> null;
                        };
                        case INT   -> switch(type)
                        {
                            case SUB    -> (x,y) -> (Double)resolve(x) - ((Long)resolve(y)).doubleValue();
                            case MUL    -> (x,y) -> (Double)resolve(x) * ((Long)resolve(y)).doubleValue();
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    try {return (Double)resolve(x)/((Long)resolve(y)).doubleValue();}
                                    catch(final ArithmeticException e) {throw new ScriptRuntimeException(line,module,"Divide by zero");}
                                };
                            }
                            case MOD    -> (x,y) -> (Double)resolve(x) % ((Long)resolve(y)).doubleValue();
                            case BITAND -> (x,y) -> ltod(dtol((Double)resolve(x)) & (Long)resolve(y));
                            case BITOR  -> (x,y) -> ltod(dtol((Double)resolve(x)) | (Long)resolve(y));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)resolve(x)) ^ (Long)resolve(y));
                            case LSH    -> (x,y) -> (Double)resolve(x)*(double)(1L<<(Long)resolve(y));
                            case RSH,
                                 LRSH   -> (x,y) -> (Double)resolve(x)/(double)(1L<<(Long)resolve(y));
                            default     -> null;
                        };
                        case FLOAT -> switch(type)
                        {
                            case SUB    -> (x,y) -> (Double)resolve(x) - (Double)resolve(y);
                            case MUL    -> (x,y) -> (Double)resolve(x) * (Double)resolve(y);
                            case DIV    ->
                            {
                                final String module = ctx.module;
                                yield (x,y) ->
                                {
                                    try {return (Double)resolve(x)/(Double)resolve(y);}
                                    catch(final ArithmeticException e) {throw new ScriptRuntimeException(line,module,"Divide by zero");}
                                };
                            }
                            case MOD    -> (x,y) -> (Double)resolve(x) % (Double)resolve(y);
                            case BITAND -> (x,y) -> ltod(dtol((Double)resolve(x)) & dtol((Double)resolve(y)));
                            case BITOR  -> (x,y) -> ltod(dtol((Double)resolve(x)) | dtol((Double)resolve(y)));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)resolve(x)) ^ dtol((Double)resolve(y)));
                            case LSH    -> (x,y) -> (Double)resolve(x)*Math.pow(2D,(Double)resolve(y));
                            case RSH,
                                 LRSH   -> (x,y) -> (Double)resolve(x)/Math.pow(2D,(Double)resolve(y));
                            default     -> null;
                        };
                        default    -> null;
                    };
                    default    -> null;
                };
                if(op == null)
                {
                    ctx.reporter.report
                    (
                        line,
                        "Cannot "+switch(type)
                        {
                            case SUB    -> "subtract";
                            case MUL    -> "multiply";
                            case DIV    -> "divide";
                            case MOD    -> "compute the remainder of";
                            case BITAND -> "compute bitwise and on";
                            case BITOR  -> "compute bitwise or on";
                            case BITXOR -> "compute bitwise exlcusive or on";
                            case LSH    -> "left bit shift";
                            case RSH    -> "arithmetic right bit shift";
                            case LRSH   -> "logical right bit shift";
                            default     -> throw new AssertionError();
                        }+" types "+ abt +" and "+ bbt
                    );
                    yield null;
                }
                //noinspection DuplicateExpressions
                yield abt == BaseType.FLOAT || bbt == BaseType.FLOAT
                    ? Type.FLOAT
                    : Type.INT;
            }
            default              ->
            {
                ctx.reporter.report(line,"Invalid binary operator: "+type);
                op = null;
                yield null;
            }
        };
        return ct == null? null : new Operation(op,ct);
    }
    /**
     * Evaluates a math expression.
     *
     * <pre>MathExpr := {('&&'|'||'|'=='|'!='|'&gt'|'&lt'|'&gt='|'&lt='|'+'|'-'|'*'|'/'|'%'|'&'|'|'|'^'|'&lt&lt'|'&gt&gt'|'&gt&gt&gt') HighPrecedence}</pre>
     *
     * @see Script#mediumPrecedence(Context)
     */
    private static Instruction math(final Context ctx,final Instruction first) throws ScriptException
    {
        /*/
        The following is an algorithm to parse an infix-notation expression. It works by maintaining a stack of operands
        and operators, ensuring that the operators on the stack always have less precedence than the next as the loop
        invariant. If that isn't the case during some point in the execution, then the algorithm pops the last two
        operands with the last operator off their stacks and combines them into a single operand until the loop invariant
        is once again reached. Tokens which aren't an operator are assumed to be the end of the expression and therefore
        treated as if they had the highest precedence.
        
        For example, consider the equation 'a + b * c - d'. The operand stack will initially start with 'a' and 'b', and
        the operator stack will start with '+'. The next operator is '*', which has higher precedence than '+'. This can
        be added to the operator stack because it still preserves the loop invariant. The algorithm then pushes 'c' to
        the operand stack, and the next operator is '-'. Since this operator has lower precedence than the top of the
        operator stack, '*', the algorithm cannot push '-'. Instead, the algorithm will combine operations until it can
        push '-'. In this case, operands 'c' and 'b' with operator '*' will be popped, combined, and pushed, creating
        operand '(b * c)'. Since the top of the operator stack is now '+' (same precedence as '-'), the algorithm can
        push '-'. The algorithm then pushes 'd' to the operand stack, and the next operator is 'EOF'. Since 'EOF' has
        precedence '-1', the algorithm will be forced to combine the stacks until there are no more operators. This
        results in a final operand '(a + ((b * c) - d))'.
        /*/
        if(first == null) return null;
        final Instruction[] I = new Instruction[11]; // 10 happens to be the number of different precedence levels, so
        final Token[] O = new Token[10];             // the "stacks" can have constant size.
        I[0] = first;
        int i = 0;
        // Loop until a non-operator token is encountered.
        while(precedence(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type()) != (byte)-1)
        {
            // Eat the operator and the expression to the right of it.
            O[i] = ctx.tokens.next();
            {
                final Instruction e = highPrecedence(ctx);
                if(e == null) return null;
                I[++i] = e;
            }
            
            // While there are still operators on the stack and the cursor's operator is less than the stack's operator,
            // combine the operands and operators.
            while(i != 0 && precedence(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type()) <= precedence(O[i-1].type()))
            {
                final Instruction b = I[i],a = I[--i];
                final Token o = O[i];
                final Operation op = getOp(a.type,b.type,o.type(),ctx,o.line());
                if(op == null) return null;
                ctx.bb.instruction
                (
                    I[i] = new Instruction(o.line(),op.ret)
                    {
                        @Override
                        Object exec(final RuntimeScope s)
                        {
                            final Object bb = s.popAccumulator();
                            final Object[] d = s.accumulator.data();
                            final int p = s.accumulator.pos()-1;
                            d[p] = op.op.apply(d[p],bb);
                            return null;
                        }
                    },
                    "binOp "+o.value()
                );
                --ctx.accPos;
            }
        }
        if(i != 0)
        {
            ctx.reporter.report(first.line,"Invalid expression");
            return null;
        }
        return I[0];
    }
    /**
     * Evaluates a medium-precedence expression.
     *
     * <pre>MediumPrecedence := HighPrecedence [MathExpr]</pre>
     *
     * @see Script#highPrecedence(Context)
     * @see Script#math(Context,Instruction)
     */
    private static Instruction mediumPrecedence(final Context ctx) throws ScriptException
    {
        // Get a high-precedence expression.
        final Instruction i = highPrecedence(ctx);
        if(i == null) return null;
        // Parse binary operator, if necessary.
        return precedence(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type()) != (byte)-1
            ? math(ctx,i)
            : i;
    }
    /**
     * Evaluates a ternary conditional operator.
     *
     * <pre>TernaryExpr := '?' Expr ':' LowPrecedence</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#lowPrecedence(Context)
     */
    private static Instruction ternary(final Context ctx,final Instruction condition) throws ScriptException
    {
        if(condition != null && matches(ItrMode.peek,ctx.tokens,TokenType.CONDITION))
        {
            // Eat '?'.
            final int line = ctx.tokens.next().line();
            
            // Eat true branch.
            final BlockBuilder start = ctx.bb;
            // start -> then
            --ctx.accPos; start.branch(true,ctx.bbList.size());
            ctx.bbList.add(ctx.bb = ctx.createBB("ternary.then"));
            final Instruction t = parseExpr(ctx);
            if(mismatch(ItrMode.peek,ctx.tokens,ctx.reporter,TokenType.COLON))
            {
                ctx.bb = start;
                return null;
            }
            ctx.tokens.next(); // Eat ':'.
            final BlockBuilder tend = ctx.bb;
            
            // Eat false branch.
            // start -> else
            start.branch(false,ctx.bbList.size());
            ctx.bbList.add(ctx.bb = ctx.createBB("ternary.else"));
            final Instruction f = lowPrecedence(ctx);
            
            final int ei = ctx.bbList.size();
            tend.branch(ei);   // then -> end
            ctx.bb.branch(ei); // else -> end
            ctx.bbList.add(ctx.bb = ctx.createBB("ternary.end"));
            
            if(t == null || f == null) return null;
            if(!t.type.equals(f.type))
            {
                ctx.reporter.report
                (
                    line,
                    "Expressions in ternary operator have different types: "+
                    t.type+" and "+f.type
                );
                return null;
            }
            // Subtract one from the accumulator, since only one branch will be taken.
            --ctx.accPos;
            // Return dummy instruction. The instruction does not need to be added
            // to the block since both branches' instructions already push their
            // results to the stack.
            ctx.bb.dummyT = t.type;
            return new Instruction(line,t.type,t.isValueType && f.isValueType)
            {
                @Override Object exec(final RuntimeScope s) {return null;}
            };
        }
        return condition;
    }
    /**
     * Evaluates a low-precedence expression.
     *
     * <pre>LowPrecedence := MediumPrecedence [TernaryExpr]</pre>
     *
     * @see Script#mediumPrecedence(Context)
     * @see Script#ternary(Context,Instruction)
     */
    private static Instruction lowPrecedence(final Context ctx) throws ScriptException
    {
        // Get medium-precedence expression.
        final Instruction i = mediumPrecedence(ctx);
        if(i == null) return null;
        // Parse ternary expression if necessary.
        return matches(ItrMode.peek,ctx.tokens,TokenType.CONDITION)
            ? ternary(ctx,i)
            : i;
    }
    /**
     * Evaluates an assignment expression.
     *
     * <pre>Assignment := ('+='|'&='|'/='|'&gt&gt&gt='|'&lt&lt='|'%='|'*='|'|='|'&gt&gt='|'-='|'^='|'=') Expr</pre>
     *
     * @see Script#lowPrecedence(Context)
     * @see Script#parseExpr(Context)
     */
    private static Instruction assignment(final Context ctx,final Instruction lhs) throws ScriptException
    {
        return lhs == null? null : switch(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type())
        {
            case ADDEQ,ANDEQ,ASSIGN,
                 DIVEQ,LRSHEQ,LSHEQ,
                 MODEQ,MULEQ,OREQ,
                 RSHEQ,SUBEQ,XOREQ ->
            {
                if(!lhs.isValueType) ctx.reporter.report(lhs.line,"Expression yields a literal value");
                final int line;
                final TokenType tt;
                {
                    final Token t = ctx.tokens.next();
                    line = t.line();
                    tt = t.type();
                }
                final Instruction rhs = parseExpr(ctx);
                if(rhs != null && lhs.isValueType)
                {
                    final Type ct = lhs.type;
                    if(tt == TokenType.ASSIGN)
                    {
                        if(convert(ctx,rhs.type,ct,line))
                        {
                            final String module = ctx.module;
                            final Instruction i = new Instruction(line,ct,true)
                            {
                                @Override
                                Object exec(final RuntimeScope s)
                                {
                                    final Object o = resolve(s.popAccumulator());
                                    final Value v = (Value)s.accumulator.top();
                                    if(v.type.isConst && v.value != null)
                                        throw new ScriptRuntimeException(line,module,"Re-definition of const variable");
                                    v.value = o;
                                    return null;
                                }
                            };
                            ctx.bb.instruction(i,"assign");
                            --ctx.accPos;
                            ctx.sideEffects = true;
                            yield i;
                        }
                        ctx.sideEffects = true;
                        yield null;
                    }
                    final Operation op = getOp
                    (
                        lhs.type,rhs.type,
                        switch(tt)
                        {
                            case ADDEQ  -> TokenType.ADD;
                            case ANDEQ  -> TokenType.BITAND;
                            case DIVEQ  -> TokenType.DIV;
                            case LRSHEQ -> TokenType.LRSH;
                            case LSHEQ  -> TokenType.LSH;
                            case MODEQ  -> TokenType.MOD;
                            case MULEQ  -> TokenType.MUL;
                            case OREQ   -> TokenType.BITOR;
                            case RSHEQ  -> TokenType.RSH;
                            case SUBEQ  -> TokenType.SUB;
                            default     -> TokenType.BITXOR; // XOREQ
                        },
                        ctx,line
                    );
                    if(op != null)
                    {
                        // Execute the operator, convert the result into the left operand's type,
                        // then store the result.
                        {
                            final BiFunction<Object,Object,Object> o = op.op;
                            ctx.bb.instruction
                            (
                                new Instruction(line,null)
                                {
                                    @Override
                                    Object exec(final RuntimeScope s)
                                    {
                                        final Object[] d = s.accumulator.data();
                                        final int p = s.accumulator.pos() - 1;
                                        d[p] = o.apply(((Value)d[p-1]).value,d[p]);
                                        return null;
                                    }
                                },
                                "math "+tt
                            );
                        }
                        if(convert(ctx,rhs.type,lhs.type,line))
                        {
                            final String module = ctx.module;
                            final Instruction i = new Instruction(line,lhs.type,true)
                            {
                                @Override
                                Object exec(final RuntimeScope s)
                                {
                                    final Object o = s.popAccumulator();
                                    final Value v = (Value)s.accumulator.top();
                                    if(v.type.isConst && v.value != null)
                                        throw new ScriptRuntimeException(line,module,"Re-definition of const variable");
                                    v.value = o;
                                    return null;
                                }
                            };
                            ctx.bb.instruction(i,"assign");
                            --ctx.accPos;
                            ctx.sideEffects = true;
                            yield i;
                        }
                    }
                    ctx.sideEffects = true;
                    yield null;
                }
                ctx.sideEffects = true;
                yield null;
            }
            default -> lhs;
        };
    }
    /**
     * Evaluates an expression.
     *
     * <pre>Expr := LowPrecedence [Assignment]</pre>
     *
     * @see Script#lowPrecedence(Context)
     * @see Script#assignment(Context,Instruction)
     */
    private static Instruction parseExpr(final Context ctx) throws ScriptException
    {
        // Get low-precedence expression.
        final Instruction i = lowPrecedence(ctx);
        if(i == null) return null;
        // Parse assignment expression if necessary
        return matches(ItrMode.peek,ctx.tokens,
                       TokenType.ASSIGN,TokenType.ADDEQ,TokenType.ANDEQ,
                       TokenType.DIVEQ,TokenType.LRSHEQ,TokenType.LSHEQ,
                       TokenType.MODEQ,TokenType.MULEQ,TokenType.OREQ,
                       TokenType.RSHEQ,TokenType.SUBEQ,TokenType.XOREQ)
            ? assignment(ctx,i)
            : i;
    }
    
    /** Dummy return value for control flow. */
    static final Object RET_VOID = new Object();
    
    /*/
    By convention, statement parsing functions return true/false to indicate success and failure. All failures
    are reported in the functions that failed. It is up to the calling functions to determine if they can
    continue executing and perhaps catch more unrelated errors.
    /*/
    
    /** Pushes/pops the runtime and compile-time scopes. */
    private static void scopeManip(final Context ctx,final int line,final boolean push)
    {
        if(push)
        {
            ctx.sc.pushScope();
            ctx.bb.instruction
            (
                new Instruction(line,null)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.pushScope();
                        return null;
                    }
                },
                "pushScope"
            );
        }
        else
        {
            for(final String s : ctx.sc.popScope().modules)
                ctx.imports.computeIfPresent(s,(a,b) -> b != 1? b - 1 : null);
            ctx.bb.instruction
            (
                new Instruction(line,null)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.popScope();
                        return null;
                    }
                },
                "popScope"
            );
        }
    }
    /**
     * Evaluates a scoped code block.
     *
     * <pre>BlockStmt := '{' {Stmt} '}'</pre>
     *
     * @see Script#parseStmt(Context,Type,boolean,boolean)
     */
    private static boolean parseBlock(final Context ctx,final Type ret,final boolean top) throws ScriptException
    {
        // Assume '\{' already eaten.
        final int l = ctx.tokens.peek(-1).line();
        if(matches(ItrMode.peek,ctx.tokens,TokenType.RBRACE))
        {
            ctx.tokens.next();
            if(top) ctx.bb.ensureReturn(ctx.reporter,ret);
            return true;
        }
        // Each block gets its own scope.
        scopeManip(ctx,l,true);
        final boolean st = parseStmts(ctx,ret,true);
        if(top) ctx.bb.ensureReturn(ctx.reporter,ret);
        final int l2;
        if(st)
        {
            final Token t = ctx.tokens.peek(-1);
            assert t.type() == TokenType.RBRACE;
            // The above assertion is true calling 'parseStmts' with the 'block' argument set
            // to true will yield an error if the last token was not a closing brace. If this
            // isn't the case, then there is a bug in the compiler.
            l2 = t.line();
        }
        else l2 = l;
        scopeManip(ctx,l2,false);
        return st;
    }
    /**
     * Evaluates an if-then-else statement.
     *
     * <pre>IfStmt := 'if' '(' Expr ')' Stmt ['else' Stmt]</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#parseStmt(Context,Type,boolean,boolean)
     */
    private static boolean parseIf(final Context ctx,final Type ret) throws ScriptException
    {
        // Assume 'if' already eaten.
        // if( Condition ) ThenStmt [else ElseStmt]
        
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN)) return false;
        final Instruction c = parseExpr(ctx); // Condition
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN))
        {
            skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
            return false;
        }
        
        // ThenStmt
        final BlockBuilder start = ctx.bb;
        // start -> then
        --ctx.accPos; start.branch(true,ctx.bbList.size());
        ctx.bbList.add(ctx.bb = ctx.createBB("if.then"));
        boolean flag = parseStmt(ctx,ret,false,true) != Status.BAD
                       && c != null;

        int toElseOrEnd = ctx.bbList.size();
        // start -> (else|end)
        start.branch(false,toElseOrEnd);

        // ElseStmt
        if(ctx.tokens.peek().type() == TokenType.ELSE)
        {
            final BlockBuilder tend = ctx.bb;
            ctx.bbList.add(ctx.bb = ctx.createBB("if.else"));
            ctx.tokens.next(); // Eat 'else'.
            flag = parseStmt(ctx,ret,false,true) != Status.BAD
                   && flag;
            /*/
            The line immediately after this if statement simply sets
            the last block's branch to the index of the 'end' block.
            Since there is an else statement, the end block has moved.
            /*/
            toElseOrEnd = ctx.bbList.size();
            /*/
            ctx.bb is set to the 'then' or 'else' blocks depending on
            whether there was an else statement, so the end of the
            'then' branch must be updated here.
            /*/
            // then -> end
            tend.branch(toElseOrEnd);
        }
        // (then|else) -> end
        ctx.bb.branch(toElseOrEnd);
        
        ctx.bbList.add(ctx.bb = ctx.createBB("if.end"));
        return flag;
    }
    /**
     * Evaluates a for-loop statement.
     *
     * <pre>ForStmt := 'for' '(' (((DeclOrExprStmt|';') [Expr] ';' [Expr {, Expr}])|(['const'] Name ':' Expr)) ')' Stmt</pre>
     *
     * @see Script#parseDeclOrExprStmt(Context)
     * @see Script#parseExpr(Context)
     * @see Script#parseStmt(Context,Type,boolean,boolean)
     */
    private static boolean parseFor(final Context ctx,final Type ret) throws ScriptException
    {
        // Assume 'for' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN)) return false;
        final boolean isForEach;
        {
            final int p = ctx.tokens.pos();
            // Check for "[const] name :" because that pattern cannot appear in normal for loops immediately after
            // the opening parenthesis.
            TokenType t = nonEOF(ItrMode.next,ctx.tokens,ctx.reporter).type();
            if(t == TokenType.CONST) t = nonEOF(ItrMode.next,ctx.tokens,ctx.reporter).type();
            isForEach = t == TokenType.ID && nonEOF(ItrMode.next,ctx.tokens,ctx.reporter).type() == TokenType.COLON;
            ctx.tokens.pos(p);
        }
        if(isForEach)
        {
            // for([const] name : list) body
            final boolean isConst = chkConst(ctx.tokens);
            final Token e = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID); // Guaranteed not EOF by isForEach.
            if(e.type() != TokenType.ID)
            {
                skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                return false;
            }
            ctx.tokens.next(); // Eat ':', guaranteed by isForEach.
            
            // Parse list expression.
            final Instruction l = parseExpr(ctx);
            if(l == null)
            {
                skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                return false;
            }
            boolean flag = l.type.base != BaseType.LIST || l.type.subType.base == BaseType.VOID;
            if(flag)
                ctx.reporter.report
                (
                    l.line,
                    "For-each construct cannot be used on type "+l.type
                );
            
            // Set up loop scope.
            final String itrName = e.value();
            if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN))
            {
                skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                return false;
            }
            scopeManip(ctx,line,true);
            final ConstableType ct = Types.constableType(l.type.subType,isConst);
            ctx.sc.putField(itrName,ct);
            final Value field = new Value(ct,null),itr = new Value(null,null);
            ctx.bb.instruction
            (
                new Instruction(l.line,null)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.putField(itrName,field);
                        itr.value = listData(resolve(s.popAccumulator())).iterator();
                        return null;
                    }
                },
                "declForItr "+ct+','+itrName
            );
            --ctx.accPos;
            
            // cond = (name : list)
            final int toCond = ctx.bbList.size();
            ctx.bb.branch(toCond);
            ctx.bbList.add(ctx.bb = ctx.createBB("forEach.cond"));
            ctx.bb.instruction
            (
                new Instruction(l.line,Type.BOOL)
                {
                    @Override @SuppressWarnings("unchecked")
                    Object exec(final RuntimeScope s)
                    {
                        s.pushAccumulator(((Iterator<Value>)itr.value).hasNext());
                        return null;
                    }
                },
                "forHasNext"
            );
            ++ctx.accPos;
            
            // body
            final int toBody = ctx.bbList.size();
            final BlockBuilder endOfCond = ctx.bb;
            // cond -> body
            --ctx.accPos; endOfCond.branch(true,toBody);
            ctx.bbList.add(ctx.bb = ctx.createBB("forEach.body"));
            ctx.sc.enterLoop();
            ctx.bb.instruction
            (
                new Instruction(l.line,null)
                {
                    @Override @SuppressWarnings("unchecked")
                    Object exec(final RuntimeScope s)
                    {
                        field.value = ((Iterator<Value>)itr.value).next().value;
                        return null;
                    }
                },
                "forItrNext"
            );
            if(parseStmt(ctx,ret,false,true) == Status.BAD)
            {
                scopeManip(ctx,l.line,false);
                flag = true;
            }
            final List<Integer>[] cflow = ctx.sc.exitLoop();
            // body -> cond
            ctx.bb.branch(toCond);
            final int toEnd = ctx.bbList.size();
            // cond -> end
            endOfCond.branch(false,toEnd);
            ctx.bbList.add(ctx.bb = ctx.createBB("forEach.end"));
            
            // Direct all control flow to the correct places.
            for(final int bb : cflow[0]) // break
                ctx.bbList.get(bb).branch(toEnd);
            for(final int bb : cflow[1]) // continue
                ctx.bbList.get(bb).branch(toCond);
            
            scopeManip(ctx,ctx.tokens.peek(-1).line(),false);
            return !flag;
        }
        // for([init] ; [cond] ; [upd]) body
        scopeManip(ctx,line,true);
        boolean flag = true;
        
        // init
        if(matches(ItrMode.peek,ctx.tokens,TokenType.SEMICOLON))
            ctx.tokens.next(); // Eat ';'.
        else
        {
            final boolean hadFX = ctx.sideEffects;
            ctx.sideEffects = false;
            flag = parseDeclOrExprStmt(ctx);
            // Ignore side effects.
            if(ctx.sideEffects) --ctx.accPos;
            ctx.sideEffects = hadFX;
        }
        final BlockBuilder endOfStart = ctx.bb;
        
        // cond
        final BlockBuilder endOfCond;
        final int toCond;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.SEMICOLON))
        {
            toCond = -1;
            ctx.tokens.next(); // Eat ';'.
            endOfCond = null;
        }
        else
        {
            toCond = ctx.bbList.size();
            ctx.bbList.add(ctx.bb = ctx.createBB("for.cond"));
            flag = parseExpr(ctx) != null &&
                   !mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON) &&
                   flag; // No short circuit so that the tokens get eaten.
            endOfCond = ctx.bb;
        }
        
        // upd
        final BlockBuilder endOfUpd;
        final int toBody,toBodyOrCond,fromBody;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.RPAREN))
        {
            endOfUpd = null;
            toBody = ctx.bbList.size();
            fromBody = toBodyOrCond = toCond == -1? toBody : toCond;
            ctx.tokens.next(); // Eat ')'.
        }
        else
        {
            fromBody = ctx.bbList.size();
            ctx.bbList.add(ctx.bb = ctx.createBB("for.upd"));
            
            // Parse comma-separated expressions.
            final boolean hadFX = ctx.sideEffects;
            ctx.sideEffects = false;
            do
            {
                flag = parseExpr(ctx) != null && flag;
                // Ignore side effects.
                if(ctx.sideEffects)
                {
                    ctx.sideEffects = false;
                    --ctx.accPos;
                }
            }
            while(matches(ItrMode.next,ctx.tokens,TokenType.COMMA));
            ctx.sideEffects = hadFX;
            // Ensure loop broke on a closing parenthesis.
            flag = flag && !mismatch(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.RPAREN);
            
            toBody = ctx.bbList.size();
            toBodyOrCond = toCond == -1? toBody : toCond;
            // upd -> (body|cond)
            (endOfUpd = ctx.bb).branch(toBodyOrCond);
        }
        // start -> (body|cond)
        endOfStart.branch(toBodyOrCond);
        
        // body
        // (upd|body|cond) -> body
        (endOfCond == null? endOfUpd == null? ctx.bb : endOfUpd : endOfCond).branch(true,toBody);
        ctx.bbList.add(ctx.bb = ctx.createBB("for.body"));
        ctx.sc.enterLoop();
        flag = parseStmt(ctx,ret,false,true) != Status.BAD && flag;
        final List<Integer>[] cflow = ctx.sc.exitLoop();
        
        final int toEnd = ctx.bbList.size();
        // body -> (upd|body|cond)
        ctx.bb.branch(fromBody);
        if(endOfCond != null)
        {
            // cond -> end
            --ctx.accPos;
            endOfCond.branch(false,toEnd);
        }
        ctx.bbList.add(ctx.bb = ctx.createBB("for.end"));
    
        // Direct all control flow to the correct places.
        for(final int bb : cflow[0]) // break
            ctx.bbList.get(bb).branch(toEnd);
        for(final int bb : cflow[1]) // continue
            ctx.bbList.get(bb).branch(fromBody);
        
        scopeManip(ctx,ctx.tokens.peek(-1).line(),false);
        if(flag) return true;
        skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
        return false;
    }
    /**
     * Evaluates a while-loop statement.
     *
     * <pre>WhileStmt := 'while' '(' Expr ')' Stmt</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#parseStmt(Context,Type,boolean,boolean)
     */
    private static boolean parseWhile(final Context ctx,final Type ret) throws ScriptException
    {
        // Assume 'while' already eaten.
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN)) return false;
        // while( cond ) body
        
        // cond
        final int toCond = ctx.bbList.size();
        // start -> cond
        ctx.bb.branch(toCond);
        ctx.bbList.add(ctx.bb = ctx.createBB("while.cond"));
        final Instruction c = parseExpr(ctx);
        if(c == null || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN))
        {
            skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
            return false;
        }
        
        // body
        final BlockBuilder endOfCond = ctx.bb;
        // cond -> body
        --ctx.accPos; endOfCond.branch(true,ctx.bbList.size());
        ctx.bbList.add(ctx.bb = ctx.createBB("while.body"));
        ctx.sc.enterLoop();
        final boolean flag = parseStmt(ctx,ret,false,true) == Status.BAD;
        final List<Integer>[] cflow = ctx.sc.exitLoop();
        
        final int toEnd = ctx.bbList.size();
        // body -> cond
        ctx.bb.branch(toCond);
        // cond -> end
        endOfCond.branch(false,toEnd);
        
        // Direct all control flow to the correct places.
        for(final int bb : cflow[0]) // break
            ctx.bbList.get(bb).branch(toEnd);
        for(final int bb : cflow[1]) // continue
            ctx.bbList.get(bb).branch(toCond);
        
        ctx.bbList.add(ctx.bb = ctx.createBB("while.end"));
        return !flag;
    }
    /**
     * Evaluates a do-while loop statement.
     *
     * <pre>DoWhileStmt := 'do' Stmt 'while' '(' Expr ')'</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#parseStmt(Context,Type,boolean,boolean)
     */
    private static boolean parseDo(final Context ctx,final Type ret) throws ScriptException
    {
        // Assume 'do' already eaten.
        // do body while( cond );
        
        // body
        final int toBody = ctx.bbList.size();
        // start -> body
        ctx.bb.branch(toBody);
        ctx.bbList.add(ctx.bb = ctx.createBB("do.body"));
        ctx.sc.enterLoop();
        final boolean flag = parseStmt(ctx,ret,false,true) == Status.BAD;
        final List<Integer>[] cflow = ctx.sc.exitLoop();
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.WHILE) ||
           mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN))
            return false;
        
        // cond
        final Instruction c = parseExpr(ctx);
        if(c == null || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN))
        {
            skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
            return false;
        }
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON)) return false;
        
        final int toEnd = ctx.bbList.size();
        // body -> cond -> (body|end)
        --ctx.accPos;
        ctx.bb.branch( true,toBody);
        ctx.bb.branch(false,toEnd);
        ctx.bbList.add(ctx.bb = ctx.createBB("do.end"));
    
        // Direct all control flow to the correct places.
        for(final int bb : cflow[0]) // break
            ctx.bbList.get(bb).branch(toEnd);
        for(final int bb : cflow[1]) // continue
            ctx.bbList.get(bb).branch(toBody);
        
        return !flag;
    }
    /**
     * Evaluates a return statement.
     *
     * <pre>RetStmt := 'return' [Expr] ';'</pre>
     *
     * @see Script#parseExpr(Context)
     */
    private static boolean parseRet(final Context ctx,final Type ret) throws ScriptException
    {
        // Assume 'return' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // return [Expr] ;
        if(ctx.deadCode == (byte)0) ctx.deadCode = (byte)1;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.SEMICOLON))
        {
            ctx.bb.instruction
            (
                new Instruction(line,ret,false,true)
                {
                    @Override Object exec(final RuntimeScope s) {return RET_VOID;}
                },
                "return "+Type.VOID
            );
            // Eat ';'
            ctx.tokens.next();
            if(Type.VOID.equals(ret)) return true;
            ctx.reporter.report
            (
                line,
                "Expected return type "+ret+
                ", got type "+Type.VOID
            );
        }
        else
        {
            final Instruction i = parseExpr(ctx);
            // Eat ';'
            ctx.tokens.next();
            ctx.bb.instruction
            (
                new Instruction(line,ret,false,true) {@Override Object exec(final RuntimeScope s) {return resolve(s.popAccumulator());}},
                "return "+ret
            );
            --ctx.accPos;
            if(i != null)
            {
                if(i.type.equals(ret)) return true;
                ctx.reporter.report
                (
                    i.line,
                    "Expected return type "+ret+
                    ", got type "+i.type
                );
            }
        }
        return false;
    }
    /**
     * Evaluates a throw statement.
     *
     * <pre>ThrowStmt := 'throw' [Expr] ';'</pre>
     *
     * @see Script#parseExpr(Context)
     */
    private static boolean parseThrow(final Context ctx) throws ScriptException
    {
        // Assume 'throw' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // throw [Expr] ;
    
        if(ctx.deadCode == (byte)0) ctx.deadCode = (byte)1;
        if(matches(ItrMode.peek,ctx.tokens,TokenType.SEMICOLON))
        {
            ctx.bb.instruction
            (
                new Instruction(line,null)
                {
                    @Override Object exec(final RuntimeScope s) {throw new ScriptRuntimeException(line+1,ctx.module);}
                },
                "throw [null]"
            );
            return true;
        }
        
        // Expr
        final Instruction i = parseExpr(ctx);
        if(i == null || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON))
        {
            skip(ctx.tokens,TokenType.SEMICOLON,null);
            return false;
        }
        final Type t = i.type;
        
        ctx.bb.instruction
        (
            new Instruction(line,null)
            {
                @Override
                Object exec(final RuntimeScope s)
                {
                    throw new ScriptRuntimeException
                    (
                        line+1,ctx.module,
                        Script.toString(s.popAccumulator(),t)
                    );
                }
            },
            "throw"
        );
        --ctx.accPos;
        return true;
    }
    /**
     * Evaluates a break statement.
     *
     * <pre>BreakStmt := 'break' ';'</pre>
     */
    private static boolean parseBreak(final Context ctx)
    {
        // Assume 'break' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // break ;
    
        if(ctx.deadCode == (byte)0) ctx.deadCode = (byte)1;
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON))
            skip(ctx.tokens,TokenType.SEMICOLON,null);
        
        if(ctx.sc.cflow.empty())
        {
            ctx.reporter.report(line,"Break statement outside of loop");
            return false;
        }
        ctx.sc.cflow.top()[0].add(ctx.bbList.size()-1);
        return true;
    }
    /**
     * Evaluates a continue statement.
     *
     * <pre>ContinueStmt := 'continue' ';'</pre>
     */
    private static boolean parseContinue(final Context ctx)
    {
        // Assume 'continue' already eaten.
        final int line = ctx.tokens.peek(-1).line();
        // break ;
    
        if(ctx.deadCode == (byte)0) ctx.deadCode = (byte)1;
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON))
        {
            skip(ctx.tokens,TokenType.SEMICOLON,null);
            return false;
        }
        
        if(ctx.sc.cflow.empty())
        {
            ctx.reporter.report(line,"Continue statement outside of loop");
            return false;
        }
        ctx.sc.cflow.top()[1].add(ctx.bbList.size()-1);
        return true;
    }
    /**
     * Evaluates an expression statement.
     *
     * <pre>ExprStmt := Expr ';'</pre>
     *
     * @see Script#parseExpr(Context)
     * @see Script#parseDeclOrExprStmt(Context)
     */
    private static boolean parseExprStmt(final Context ctx) throws ScriptException
    {
        // Expr ;
        return parseExpr(ctx) != null && !mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);
    }
    /**
     * Evaluates a variable declaration statement.
     *
     * <pre>DeclStmt := ['const'] Type Name ['=' Expr] {',' Name ['=' Expr]} ';'</pre>
     *
     * @see Script#type(Context)
     * @see Script#parseDeclOrExprStmt(Context)
     * @see Script#parseExpr(Context)
     */
    private static boolean parseDeclStmt(final Context ctx) throws ScriptException
    {
        // [const] Type Name [= Expr] {, Name [= Expr]} ;
        final int line = nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).line();
    
        // Type
        final boolean isConst = chkConst(ctx.tokens);
        final Type ct = type(ctx);
        if(ct == null) return false;
        final ConstableType cct = Types.constableType(ct,isConst);
        
        final String[] n;
        final boolean[] b;
        final int ps;
        boolean flag = true;
        {
            record pair(String n,boolean i) {}
            final ArrayList<pair> pairs = new ArrayList<>();
            do
            {
                // Name
                final Token t = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                if(t.type() != TokenType.ID)
                {
                    skip(ctx.tokens,TokenType.SEMICOLON,null);
                    return false;
                }
                if(ctx.sc.getStruct(t.value()) != null)
                {
                    ctx.reporter.report
                    (
                        t.line(),
                        "Cannot use name '"+t.value()+
                        "'; a struct with that name exists"
                    );
                    flag = false;
                }
                
                // Expr
                final boolean i = nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type() == TokenType.ASSIGN;
                if(i)
                {
                    ctx.tokens.next(); // Eat '='.
                    final Instruction e = parseExpr(ctx);
                    flag = e != null && convert(ctx,e.type,ct,e.line) && flag;
                }
                if(mismatch(ItrMode.peek,ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.SEMICOLON))
                {
                    skip(ctx.tokens,TokenType.SEMICOLON,null);
                    return false;
                }
                
                pairs.add(new pair(t.value(),i));
            }
            while(!matches(ItrMode.next,ctx.tokens,TokenType.SEMICOLON));
            
            ps = pairs.size();
            n = new String[ps];
            b = new boolean[ps];
            int i = ps;
            for(final pair p : pairs)
            {
                ctx.sc.putField(n[--i] = p.n,cct);
                if(b[i] = p.i) --ctx.accPos;
            }
        }
        final StringJoiner sj = new StringJoiner(",","decl "+cct+' ',"");
        for(int i = 0;i < ps;++i) sj.add(n[i]);
        ctx.bb.instruction
        (
            new Instruction(line,null)
            {
                @Override
                Object exec(final RuntimeScope s)
                {
                    for(int i = 0;i < ps;++i)
                        s.putField(n[i],new Value(cct,b[i]? s.popAccumulator() : null));
                    return null;
                }
            },
            sj.toString()
        );
        return flag;
    }
    /**
     * Evaluates a variable declaration or expression statement.
     *
     * <pre>DeclOrExprStmt := (DeclStmt|ExprStmt)</pre>
     *
     * @see Script#parseDeclStmt(Context)
     * @see Script#parseExprStmt(Context)
     */
    private static boolean parseDeclOrExprStmt(final Context ctx) throws ScriptException
    {
        return switch(ctx.tokens.peek().type())
        {
            case VOID,BOOL,INT,FLOAT,STR,FUNC,CONST -> parseDeclStmt(ctx);
            case ID -> ctx.sc.getStruct(ctx.tokens.peek().value()) != null
                           ? parseDeclStmt(ctx)
                           : parseExprStmt(ctx);
            default -> parseExprStmt(ctx);
        };
    }
    /**
     * Evaluates a structure declaration statement.
     *
     * <pre>StructDeclStmt := 'struct' StructName '{' [ArgList] '}'</pre>
     *
     * @see Script#type(Context)
     */
    private static boolean parseStructDecl(final Context ctx)
    {
        // Assume 'struct' already eaten.
        final Token id = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
        if(id.type() != TokenType.ID) return false;
        final String name = id.value();
        {
            final char chr = name.charAt(0);
            if('Z' < chr || chr < 'A')
                ctx.reporter.warn
                (
                    id.line(),
                    "Struct name \""+name+"\" begins with " +
                    "a character which is not capitalized " +
                    "or not alphabetic (i.e. outside the " +
                    "range A-Z). It is advisable to follow " +
                    "this convention for readability."
                );
        }
        // struct Name \{ [Type Name {, Type Name }] \}
        
        if(mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LBRACE)) return false;
        final Map<String,ConstableType> fields = new HashMap<>();
        if(matches(ItrMode.peek,ctx.tokens,TokenType.RBRACE)) ctx.tokens.next(); // Eat '\}'.
        else
        {
            do
            {
                final boolean isConst = chkConst(ctx.tokens);
                final Type ct = type(ctx);
                final Token n = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                if(n.type() != TokenType.ID)
                {
                    skip(ctx.tokens,TokenType.RBRACE,TokenType.LBRACE);
                    return false;
                }
                fields.put(n.value(),Types.constableType(ct,isConst));
            }
            while(matches(ItrMode.next,ctx.tokens,TokenType.COMMA));
            // Ensure that the loop condition broke on a closing brace.
            if(mismatch(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.RBRACE))
            {
                skip(ctx.tokens,TokenType.RBRACE,TokenType.LBRACE);
                return false;
            }
        }
        final Map<String,ConstableType> f = ContainerUtil.makeImmutable(fields); // Make immutable
        ctx.sc.putStruct(name,f);
        // The runtime does not need any scope information, since type safety can be evaluated
        // at compile-time.
        return true;
    }
    /**
     * Base directory for imported scripts. Access is synchronized in case the API user decides to change the directory
     * on a different thread for whatever reason.
     */
    private static Path IMPORTS_DIR = Path.of(System.getProperty("user.dir"));
    /** Lock for the imports directory. */
    private static final Object pathLock = new Object();
    /** Sets the base path for all scripts. */
    public static void setImportsDir(Path path)
    {
        if(path == null) path = Path.of(System.getProperty("user.dir"));
        synchronized(pathLock) {IMPORTS_DIR = path;}
    }
    public static Path getImportsDir() {synchronized(pathLock) {return IMPORTS_DIR;}}
    /**
     * Evaluates an import statement.
     *
     * <pre>ImportStmt := 'import' LiteralStr ';'</pre>
     */
    private static boolean parseImport(final Context ctx)
    {
        // Assume 'import' already eaten.
        // import " Module " ;
        final Token module = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LIT_STR);
        if(module.type() != TokenType.LIT_STR || mismatch(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON))
            return false;
        final int line = module.line();
        
        final String m = module.value();
        // Increase the counter for the module.
        if(ctx.imports.compute(m,(k,v) -> v != null? v + 1 : 1) != 1)
            // Skip already loaded modules.
            return true;
        
        Module mm;
        synchronized(Module.REGISTRY)
        {
            // Get module if it exists.
            mm = Module.REGISTRY.get(m);
            if(mm == null)
            {
                // Try to compile external script.
                if(m.endsWith(".prgm"))
                {
                    final Path p = getImportsDir().resolve(Path.of(m));
                    // Try to read another script from file.
                    try(final FileReader fr = new FileReader(p.toFile()))
                    {
                        mm = run(fr,m,ctx.reporter.ps);
                        // If no errors were found in the script, add it to the registries.
                        if(mm != null) Module.REGISTRY.put(m,mm);
                    }
                    catch(final Exception e)
                    {
                        ctx.reporter.report
                        (
                            line,
                            "Could not read module '"+m+
                            "': "+e.getMessage()
                        );
                    }
                }
                else ctx.reporter.report(line,"Unknown module '"+m+'\'');
                if(mm == null)
                    // Create a dummy module so that more errors in this script can be
                    // caught before the compiler exits.
                    mm = new Module(new CompilerScopeEntry(),new RuntimeScopeEntry());
            }
        }
        ctx.sc.pushToScope(m,mm.compileTime);
        final RuntimeScopeEntry rt = mm.runTime;
        ctx.bb.instruction
        (
            new Instruction(line,null)
            {
                @Override
                Object exec(final RuntimeScope s)
                {
                    s.pushToScope(rt);
                    return null;
                }
            },
            "import "+module
        );
        return true;
    }
    /**
     * Evaluates a statement.
     *
     * <pre>
     * Stmt := BlockStmt
     *       | IfStmt
     *       | ForStmt
     *       | WhileStmt
     *       | DoWhileStmt
     *       | RetStmt
     *       | ThrowStmt
     *       | BreakStmt
     *       | ContinueStmt
     *       | StructDeclStmt
     *       | ImportStmt
     *       | DeclOrExprStmt
     *       | ';'
     * </pre>
     *
     * @see Script#parseBlock(Context,Type,boolean)
     * @see Script#parseIf(Context,Type)
     * @see Script#parseFor(Context,Type)
     * @see Script#parseWhile(Context,Type)
     * @see Script#parseDo(Context,Type)
     * @see Script#parseRet(Context,Type)
     * @see Script#parseThrow(Context)
     * @see Script#parseBreak(Context)
     * @see Script#parseContinue(Context)
     * @see Script#parseDeclOrExprStmt(Context)
     * @see Script#parseStructDecl(Context)
     * @see Script#parseImport(Context)
     */
    private static Status parseStmt(final Context ctx,final Type ret,final boolean block,final boolean branch)
                                    throws ScriptException
    {
        final Token pt = ctx.tokens.next();
        final boolean scflag = branch && pt.type() != TokenType.LBRACE;
        final boolean wasntDead = ctx.deadCode == (byte)0;
        if(ctx.deadCode == (byte)1)
        {
            // Since it is not trivial to ignore dead code, dead code will be reported as an error instead of
            // a warning. This is justified because unwanted code should be commented out or deleted.
            ctx.reporter.report(pt.line(),"Unreachable code");
            ctx.deadCode = (byte)2;
        }
        // Pushing and popping scope is necessary to prevent declarations
        // in non code-block branches from leaking into the outer scope.
        // e.g. if(condition) int x;
        if(scflag) scopeManip(ctx,pt.line(),true);
        final int p = ctx.accPos;
        final TokenType tt = pt.type();
        final Status retflag = switch(tt)
        {
            case EOF,RBRACE -> (tt == TokenType.RBRACE) == block? Status.SKIP : Status.BAD;
            case STRUCT     -> parseStructDecl(ctx)? Status.OK : Status.BAD;
            case LBRACE     ->
            {
                final boolean flag;
                if(ctx.tokens.canAdvance() && ctx.tokens.peek(1).type() == TokenType.COLON)
                {
                    ctx.tokens.previous(); // Puke previous token.
                    flag = parseDeclOrExprStmt(ctx);
                }
                else flag = parseBlock(ctx,ret,false);
                yield flag? Status.OK : Status.BAD;
            }
            case IF         -> parseIf      (ctx,ret)? Status.OK : Status.BAD;
            case FOR        -> parseFor     (ctx,ret)? Status.OK : Status.BAD;
            case DO         -> parseDo      (ctx,ret)? Status.OK : Status.BAD;
            case WHILE      -> parseWhile   (ctx,ret)? Status.OK : Status.BAD;
            case RETURN     -> parseRet     (ctx,ret)? Status.OK : Status.BAD;
            case THROW      -> parseThrow   (ctx)    ? Status.OK : Status.BAD;
            case BREAK      -> parseBreak   (ctx)    ? Status.OK : Status.BAD;
            case CONTINUE   -> parseContinue(ctx)    ? Status.OK : Status.BAD;
            case IMPORT     -> parseImport  (ctx)    ? Status.OK : Status.BAD;
            case SEMICOLON  -> Status.OK;//parseStmt    (ctx,ret,block,scope); // No-op.
            default         ->
            {
                ctx.tokens.previous(); // Puke previous token.
                yield parseDeclOrExprStmt(ctx)? Status.OK : Status.BAD;
            }
        };
        // Reset the accumulator to the state before the statement was executed.
        if(ctx.accPos != p)
        {
            final int line = ctx.tokens.peek(-1).line();
            ctx.bb.instruction
            (
                new Instruction(line,null)
                {
                    @Override
                    Object exec(final RuntimeScope s)
                    {
                        s.accumulator.pos(p);
                        return null;
                    }
                },
                "resetAcc "+p
            );
            ctx.accPos = p;
            if(!ctx.sideEffects) ctx.reporter.warn(line,"Unused result of expression");
        }
        ctx.sideEffects = false;
        // Reset the dead code tracker if inside a branching statement.
        if(wasntDead) ctx.deadCode = (byte)0;
        if(scflag) scopeManip(ctx,pt.line(),false);
        return retflag;
    }
    /**
     * Evaluates a list of statements.
     *
     * @param block {@code true} iff the closing token is a closing brace ('}') instead of an EOF token.
     *
     * @see Script#parseStmt(Context,Type,boolean,boolean)
     */
    private static boolean parseStmts(final Context ctx,final Type ret,final boolean block) throws ScriptException
    {
        Status last;
        //noinspection StatementWithEmptyBody
        while((last = parseStmt(ctx,ret,block,false)) == Status.OK);
        return last == Status.SKIP;
    }
    /** Parses and compiles a script. */
    private static Block parse(final Context ctx) throws ScriptException
    {
        ctx.bbList.add(ctx.bb = ctx.createBB("entry"));
        if(parseStmts(ctx,Type.VOID,false))
        {
            ctx.bb.ensureReturn(ctx.reporter,Type.VOID);
            return ctx.compile();
        }
        return null;
    }
    /** Parses, compiles, and executes a script. */
    public static Module run(final Reader reader,final String moduleName,final PrintStream err)
                             throws IOException,ScriptException
    {
        final CompilerScopeEntry compileTime;
        final Block entry;
        {
            final Context ctx = new Context(moduleName,reader,err);
            if(ctx.tokens == null) return null;
            entry = parse(ctx);
            if(ctx.reporter.reportAll()) return null;
            
            assert entry != null;
            /*/
            Proof of the above assertion:
             0| All code is assumed to be correct.
             1| 'ctx.reporter.reportAll' returns true iff there is at least one error reported.
             2| 'parse' returns a null value iff 'parseStmts' returns false.
             3| 'parseStmts' returns false iff one of the calls to 'parseStmt' returns 'Status.BAD'
             4| 'parseStmts' returns true iff one of the calls to 'parseStmt' returns 'Status.SKIP'
             5| 'parseStmts' is called with 'block' set to false
             6| Given 5, 'parseStmt' is called with 'block' set to false
             7| Given 6, The only way that 'Status.SKIP' can be returned from 'parseStmt' is if an
                'EOF' token is encountered.
             8| Given 7, The only ways that 'Status.BAD' can be returned from 'parseStmt' is if a
                '}' token is encountered or one of the other cases yields false.
             9| The only way that other cases yield false is if the functions have reported an error.
            10| Given all the above statements, entry must not be null at this point in the code.
            /*/
            
            compileTime = ctx.sc.popScope();
        }
        final RuntimeScope runTime = new RuntimeScope();
        Object result = entry;
        while(result instanceof final Block b)
            result = b.exec(runTime);
        final Map<String,Map<String,ConstableType>> structs;
        {
            final Map<String,Map<String,ConstableType>> s = new HashMap<>(compileTime.structs.size());
            for(final Map.Entry<String,Map<String,ConstableType>> e : compileTime.structs.entrySet())
                s.put(e.getKey(),ContainerUtil.makeImmutable(e.getValue()));
            structs = ContainerUtil.makeImmutable(s);
        }
        return new Module
        (
            new CompilerScopeEntry
            (
                ContainerUtil.makeImmutable(compileTime.fields),
                structs,
                ContainerUtil.makeImmutable(compileTime.modules)
            ),
            new RuntimeScopeEntry
            (
                ContainerUtil.makeImmutable(runTime.popScope().fields),
                structs
            )
        );
    }
}