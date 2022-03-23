package prgmScript;

import prgmScript.ast.*;
import prgmScript.token.Token;
import prgmScript.token.TokenType;
import prgmScript.token.Tokenize;
import prgmScript.token.Tokenize.TokenIterator;
import prgmScript.util.ErrReporter;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public final class Script
{
    // Private default constructor + final class -> cannot instantiate.
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
    
    /* ========== Numeric Casing ========== */
    private static boolean compareArgs(final Type[] a,final Type[] b)
    {
        if(a != null && b != null && a.length == b.length)
        {
            for(int i = 0;i < a.length;++i)
                if(!a[i].equals(b[i]))
                    return false;
            return true;
        }
        return false;
    }
    private static boolean triviallyConvertible(final Type to,final Type from)
    {
        return switch(to.simple())
        {
            case BOOL,STR -> true;
            case INT,FLOAT -> switch(from.simple())
            {
                case INT,FLOAT -> true;
                default -> false;
            };
            case FUNC -> from.simple() == BaseType.FUNC &&
                         from.complex().equals(to.complex()) &&
                         compareArgs(to.args(),from.args());
            case LIST -> from.simple() == BaseType.LIST &&
                         from.complex().equals(to.complex());
            default -> to.equals(from);
        };
    }
    private static Literal trivialConversion(final Literal l,final Type t)
    {
        final Object v = switch(t.simple())
        {
            case BOOL -> l.conditional();
            case STR -> toString(l);
            case INT -> l.type.simple() == BaseType.INT
                ? (Long)l.value()
                : ((Double)l.value()).longValue(); // Guaranteed by triviallyConvertible.
            case FLOAT -> l.type.simple() == BaseType.INT
                ? ((Long)l.value()).doubleValue()
                : (Double)l.value(); // Guaranteed by triviallyConvertible.
            default -> l.value();
        };
        return new Literal(l.line,t) {@Override public Object value() {return v;}};
    }
    
    /*
        Conventions:
            - Functions expect iterator to be on the first token or second token of the
              expression, check the comments at the top of the function.
            - Functions will return with the iterator past the last token of the expression.
    */
    
    private static boolean chkConst(final TokenIterator tokens)
    {
        if(matches(ItrMode.peek,tokens,TokenType.CONST))
        {
            tokens.next();
            return true;
        }
        return false;
    }
    /* ========== Expressions ========== */
    private static Expr prefix(final ErrReporter r,final Trace trace,final TokenIterator tokens,
                               final Token op,final Scope<Type> scope)
    {
        final Expr e = highPrecedence(r,trace,tokens,scope);
        if(e == null) return null;
        @FunctionalInterface interface getter {Object get(Scope<Value> s);}
        final Type ct;
        final getter g = switch(op.type())
        {
            case INC    ->
            {
                ct = e.type;
                if((e.type.simple() != BaseType.INT && e.type.simple() != BaseType.FLOAT) ||
                   !(e instanceof final Value v))
                    yield null;
                yield e.type.simple() == BaseType.INT
                    ? s ->
                      {
                          final long l = (Long)v.evaluate(s).value()+1L;
                          v.setValue(trace,s,l);
                          return l;
                      }
                    : s ->
                      {
                          final double d = (Double)v.evaluate(s).value()+1D;
                          v.setValue(trace,s,d);
                          return d;
                      };
            }
            case DEC    ->
            {
                ct = e.type;
                if((e.type.simple() != BaseType.INT && e.type.simple() != BaseType.FLOAT) ||
                   !(e instanceof final Value v))
                    yield null;
                yield e.type.simple() == BaseType.INT
                    ? s ->
                      {
                          final long l = (Long)v.evaluate(s).value()-1L;
                          v.setValue(trace,s,l);
                          return l;
                      }
                    : s ->
                      {
                          final double d = (Double)v.evaluate(s).value()-1D;
                          v.setValue(trace,s,d);
                          return d;
                      };
            }
            case ADD    -> switch(e.type.simple())
            {
                case BOOL      -> {ct = Primitives.INT.type; yield s -> (Boolean)e.evaluate(s).value()?1L:0L;}
                case INT,FLOAT -> {ct = e.type;              yield s -> e.evaluate(s).value();}
                default        -> {ct = null; yield null;}
            };
            case SUB    -> switch(e.type.simple())
            {
                case BOOL  -> {ct = Primitives.INT  .type; yield s -> (Boolean)e.evaluate(s).value()?-1L:0L;}
                case INT   -> {ct = Primitives.INT  .type; yield s -> -(Long)e.evaluate(s).value();}
                case FLOAT -> {ct = Primitives.FLOAT.type; yield s -> -(Double)e.evaluate(s).value();}
                default    -> {ct = null; yield null;}
            };
            case NOT    ->
            {
                ct = Primitives.BOOL.type;
                yield e.type.simple() == BaseType.VOID
                    ? null
                    : s -> e.evaluate(s).conditional();
            }
            case BITNOT -> switch(e.type.simple())
            {
                case BOOL  -> {ct = Primitives.INT.type; yield s -> (Boolean)e.evaluate(s).value()?~1L:-1L;}
                case INT   -> {ct = Primitives.INT.type; yield s -> ~(Long)e.evaluate(s).value();}
                case FLOAT -> {ct = Primitives.FLOAT.type; yield s -> ltod(~dtol((Double)e.evaluate(s).value()));}
                default    -> {ct = null; yield null;}
            };
            default     -> {ct = null; yield null;}
        };
        if(g == null)
        {
            r.report
            (
                e.line,
                "Cannot use operator "+op.type()+
                " on a "+(e instanceof Value? "non-":"")+
                "literal "+e.type
            );
            return null;
        }
        return new Expr(e.line,ct)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace(line,"prefix::eval",() -> literal(g::get,s));
            }
        };
    }
    private static Expr call(final ErrReporter r,final Trace trace,final TokenIterator tokens,
                             final Scope<Type> scope,final Expr func)
    {
        // Parse arguments.
        final Expr[] args = new Expr[func.type.args().length];
        {
            boolean flag = true;
            int a;
            // Parse 'expr,' patterns.
            for(a = 0;a < args.length - 1;++a)
            {
                final Expr e = parseExpr(r,trace,tokens,scope);
                if(e == null || mismatch(ItrMode.next,tokens,r,TokenType.COMMA))
                {
                    flag = false;
                    skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                }
                else args[a] = e;
            }
            final List<Type> extra = new ArrayList<>();
            {
                // Parse last argument
                if(args.length != 0)
                {
                    final Expr e = parseExpr(r,trace,tokens,scope);
                    if(e == null) flag = false;
                    args[a] = e;
                }
                // Handle extra args if necessary.
                if(mismatch(ItrMode.next,tokens,r,TokenType.RPAREN))
                    do
                    {
                        final Expr ex = parseExpr(r,trace,tokens,scope);
                        extra.add(ex == null? null : ex.type);
                    }
                    while(!matches(ItrMode.next,tokens,TokenType.RPAREN));
            }
            // Ensure types are correct.
            for(a = 0;flag && a < args.length;++a)
                flag = triviallyConvertible(args[a].type,func.type.args()[a]);
            if(!flag)
            {
                // Emit error.
                final StringJoiner sj = new StringJoiner(",","(",")");
                for(final Expr e : args) sj.add(e == null? "ERR":e.type.toString());
                for(final Type e : extra) sj.add(e == null? "ERR":e.toString());
                r.report
                (
                    func.line,
                    "Invalid arguments for function type '"+func.type+
                    "', got '"+sj+'\''
                );
                return null;
            }
        }
        // Recurse, but the next token cannot be an increment or
        // decrement because functions return literals and lists
        // only.
        return suffix
        (
            r,trace,tokens,scope,
            new Expr(func.line,func.type.complex())
            {
                @Override
                public Literal eval(final Scope<Value> s)
                {
                    return trace.wrapTrace
                    (line,"call::eval",() -> {
                        final Literal[] a = new Literal[args.length];
                        for(int i = 0;i < a.length;++i)
                            a[i] = trivialConversion(args[i].evaluate(s),func.type.args()[i]);
                        return literal(s2 -> ((Func)func.evaluate(s2)).call(a),s);
                    });
                }
            },
            nonEOF(ItrMode.next,tokens,r)
        );
    }
    private static Expr suffix(final ErrReporter r,final Trace trace,final TokenIterator tokens,
                               final Scope<Type> scope,final Expr expr,final Token op)
    {
        return switch(op.type())
        {
            case LBRACKET ->
            {
                // List index detected.
                if(expr.type.simple() != BaseType.LIST)
                {
                    r.report(op.line(),"Cannot use index-of operator on type '"+expr.type+'\'');
                    skip(tokens,TokenType.RBRACKET,TokenType.LBRACKET);
                    yield null;
                }
                // Parse index expression.
                final Expr idx = parseExpr(r,trace,tokens,scope);
                if(idx == null || mismatch(ItrMode.next,tokens,r,TokenType.RBRACKET))
                    yield null;
                if(idx.type.simple() != BaseType.INT)
                {
                    // I'm too lazy to do implicit casting
                    // for bool here, just use unary + lmao
                    r.report(idx.line,"Expression does not resolve to numeric type");
                    yield null;
                }
                final String module = r.module;
                yield suffix
                (
                    r,trace,tokens,scope,
                    new Value(expr.line,expr.type.complex())
                    {
                        // Get the index.
                        int i(final Scope<Value> s)
                        {
                            final long l = (Long)idx.evaluate(s).value();
                            if(l < 0 || l >= 1L<<32)
                                throw new prgmException(module,line,"Index "+l+ " is out of bounds");
                            return (int)l;
                        }
                        @Override
                        protected void setValue(final Scope<Value> s,final Object v)
                        {
                            trace.wrapTrace
                            (line,"suffix::setValue",() -> {
                                final int i = i(s);
                                final List<Literal> l = listData(expr.evaluate(s));
                                for(int j = l.size();j <= i;++j) l.add(null);
                                l.set(i,literal(s2 -> v,s));
                            });
                        }
                        @Override
                        public Literal eval(final Scope<Value> s)
                        {
                            return trace.wrapTrace
                            (line,"suffix::eval",() -> {
                                final int i = i(s);
                                final List<Literal> l = listData(expr.evaluate(s));
                                return l.get(i);
                            });
                        }
                    },
                    nonEOF(ItrMode.next,tokens,r)
                );
            }
            case LPAREN   ->
            {
                // Call detected.
                if(expr.type.simple() != BaseType.FUNC)
                {
                    r.report(op.line(),"Cannot use function call operator on type '"+expr.type+'\'');
                    skip(tokens,TokenType.RBRACKET,TokenType.LBRACKET);
                    yield null;
                }
                yield call(r,trace,tokens,scope,expr);
            }
            case INC,DEC  ->
            {
                if(expr.type.simple() != BaseType.INT && expr.type.simple() != BaseType.FLOAT)
                {
                    r.report
                    (
                        expr.line,
                        "Cannot "+(op.type() == TokenType.INC? "increment":"decrement")+
                        " type "+expr.type
                    );
                    yield null;
                }
                if(expr instanceof final Value v)
                {
                    if(expr.type.simple() == BaseType.INT)
                    {
                        @FunctionalInterface interface getter {long get(long x);}
                        final getter g = op.type() == TokenType.INC? x -> x + 1L : x -> x - 1L;
                        yield new Expr(v.line,v.type)
                        {
                            @Override
                            public Literal eval(final Scope<Value> s)
                            {
                                return trace.wrapTrace
                                (line,(op.type() == TokenType.INC? "inc":"dec")+"::eval<int>",() -> {
                                    final long l = (Long)v.evaluate(s).value();
                                    v.setValue(trace,s,g.get(l));
                                    return literal(v::evaluate,s);
                                });
                            }
                        };
                    }
                    else
                    {
                        @FunctionalInterface interface getter {double get(double x);}
                        final getter g = op.type() == TokenType.INC? x -> x + 1D : x -> x - 1D;
                        yield new Expr(v.line,v.type)
                        {
                            @Override
                            public Literal eval(final Scope<Value> s)
                            {
                                return trace.wrapTrace
                                (line,(op.type() == TokenType.INC? "inc":"dec")+"::eval<float>",() -> {
                                    final double d = (Double)v.evaluate(s).value();
                                    v.setValue(trace,s,g.get(d));
                                    return literal(v::evaluate,s);
                                });
                            }
                        };
                    }
                }
                r.report
                (
                    expr.line,
                    "Cannot "+(op.type() == TokenType.INC? "increment":"decrement")+
                    " a literal value"
                );
                yield null;
            }
            default       ->
            {
                // No operator found, puke the operator and return.
                tokens.previous();
                yield expr;
            }
        };
    }
    private static Type funcRetType(final ErrReporter r,final Trace trace,final TokenIterator tokens,
                                    final Scope<Type> scope)
    {
        if(mismatch(ItrMode.next,tokens,r,TokenType.LT))
        {
            skip(tokens,TokenType.GT,TokenType.LT);
            return null;
        }
        final Type ct;
        if(matches(ItrMode.peek,tokens,TokenType.GT))
        {
            tokens.next(); // Eat '>'.
            ct = Primitives.VOID.type;
        }
        else
        {
            if(nonEOF(ItrMode.peek,tokens,r).type() == TokenType.VOID)
            {
                tokens.next(); // Eat 'void'.
                ct = Primitives.VOID.type;
            }
            else if((ct = type(false,r,trace,tokens,scope)) == null)
                return null;
            if(mismatch(ItrMode.next,tokens,r,TokenType.GT))
            {
                skip(tokens,TokenType.GT,TokenType.LT);
                return null;
            }
        }
        if(mismatch(ItrMode.next,tokens,r,TokenType.LPAREN))
        {
            skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
            return null;
        }
        return ct;
    }
    private static Type type(final boolean isConst,final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // A type must be a name followed by any number of empty bracket pairs.
        final Token t = eat(ItrMode.next,tokens,r,
                            TokenType.BOOL,TokenType.INT,TokenType.FLOAT,
                            TokenType.STR,TokenType.ID,TokenType.FUNC);
        Type ct = switch(t.type())
        {
            case BOOL  -> Primitives.BOOL .type;
            case INT   -> Primitives.INT  .type;
            case FLOAT -> Primitives.FLOAT.type;
            case STR   -> Primitives.STR  .type;
            case ID    ->
            {
                if(scope.getStruct(t.value()) == null)
                {
                    r.report(t.line(),"Struct type '"+t.value()+"' is undefined");
                    yield null;
                }
                yield new Type(isConst,BaseType.STRUCT,t.value(),null);
            }
            case FUNC  ->
            {
                // Function detected, eat another type.
                // Must have the form 'func<...>(...)[][]...'
                final Type nct = funcRetType(r,trace,tokens,scope);
                final Type[] args;
                {
                    final List<Type> l = new ArrayList<>();
                    if(matches(ItrMode.peek,tokens,TokenType.RPAREN)) tokens.next(); // Advance past '\)'.
                    else
                    {
                        do
                        {
                            // Parse type.
                            final Type act = type(chkConst(tokens),r,trace,tokens,scope);
                            if(act == null)
                            {
                                skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                                yield null;
                            }
                            l.add(act);
                        }
                        while(matches(ItrMode.next,tokens,TokenType.COMMA));
                        // Ensure that the loop condition broke on a closing parentheses.
                        if(mismatch(ItrMode.prev,tokens,r,TokenType.RPAREN))
                            yield null;
                    }
                    args = l.toArray(Type[]::new);
                }
                yield new Type(isConst,BaseType.FUNC,null,nct,args);
            }
            default    -> null;
        };
        if(ct != null && matches(ItrMode.peek,tokens,TokenType.LBRACKET))
            // For list types, nest list types until there are no more brackets.
            do {eat(ItrMode.advance,tokens,r,TokenType.RBRACKET); ct = new Type(false,BaseType.LIST,null,ct);}
            while(matches(ItrMode.advance,tokens,TokenType.LBRACKET));
        return ct;
    }
    /** Creates a literal function reference. */
    private static Literal cfunc(final Function<Literal[],Object> f,final int line,final Type ret,final Type...arg)
    {
        return new Literal(line,new Type(true,BaseType.FUNC,null,ret,arg))
        {
            @Override
            public Object value()
            {
                return new Func(0,ret) {@Override public Object call(final Literal...a) {return f.apply(a);}};
            }
        };
    }
    /** Gets the actual list from the code's object representation. */
    @SuppressWarnings("unchecked")
    private static List<Literal> listData(final Literal l)
    {
        return (List<Literal>)((Map<String,Literal>)l.value()).get("data").value();
    }
    private static class LiteralList extends Literal
    {
        final Trace t;
        final List<Literal> l;
        LiteralList(final Trace trace,final int line,final Type ct,final List<Literal> l)
        {
            super(line,ct);
            t = trace;
            this.l = l;
        }
        @Override
        public Object value()
        {
            return Map.ofEntries
            (
                // 'data' is not listed in 'Scope::getMembers' so that it is invisible.
                Map.entry("data",     new Literal(line,type) {@Override public Object value() {return t.wrapTrace(line,"list::data",() -> l);}}),
                Map.entry("length",   cfunc(a -> t.wrapTrace(line,"list::length",l::size),line,Primitives.INT.type)),
                Map.entry("popFront", cfunc(a -> t.wrapTrace(line,"list::popFront",() -> l.remove(0)),line,type.complex())),
                Map.entry("popBack",  cfunc(a -> t.wrapTrace(line,"list::popBack",() -> l.remove(l.size()-1)),line,type.complex())),
                Map.entry("pushFront",cfunc(a -> t.wrapTrace(line,"list::pushFront",() -> {l.add(0,a[0]); return LiteralList.this;}),line,type,type.complex())),
                Map.entry("pushBack", cfunc(a -> t.wrapTrace(line,"list::pushBack",() -> {l.add(a[0]); return LiteralList.this;}),line,type,type.complex())),
                Map.entry("front",    cfunc(a -> t.wrapTrace(line,"list::front",() -> l.get(0)),line,type.complex())),
                Map.entry("back",     cfunc(a -> t.wrapTrace(line,"list::back",() -> l.get(l.size()-1)),line,type.complex())),
                Map.entry("insert",   cfunc(a -> t.wrapTrace(line,"list::insert",() -> {l.add(((Long)a[0].value()).intValue(),a[1]); return this;}),
                                            line,type,Primitives.INT.type,type.complex())),
                Map.entry("remove",   cfunc(a -> t.wrapTrace(line,"list::remove",() -> l.remove(((Long)a[0].value()).intValue())),
                                            line,type.complex(),Primitives.INT.type)),
                Map.entry("copy",     cfunc(a -> t.wrapTrace(line,"list::copy",() -> new ArrayList<>(l)),line,type)),
                Map.entry("swap",     cfunc(a -> t.wrapTrace(line,"list::set", () -> {
                                                @SuppressWarnings("unchecked")
                                                final List<Literal> l2 = (List<Literal>)a[0].value(),
                                                                    l3 = new ArrayList<>(l2);
                                                l2.clear();
                                                l2.addAll(l);
                                                l.addAll(l3);
                                                return null;
                                            }),line,Primitives.VOID.type,type))
            );
        }
        @Override
        public boolean equals(final Object obj)
        {
            return obj == this ||
            (
                obj instanceof Literal l && l.type.equals(type) &&
                listData(this).equals(listData(l))
            );
        }
    }
    /** Gets the actual string from the code's object representation. */
    @SuppressWarnings("unchecked")
    private static String strData(final Literal l)
    {
        return (String)((Map<String,Literal>)l.value()).get("data").value();
    }
    private static class LiteralStr extends Literal
    {
        final Trace t;
        final String s;
        LiteralStr(final Trace trace,final int line,final String s)
        {
            super(line,Primitives.STR.type);
            t = trace;
            this.s = s;
        }
        @Override
        public Object value()
        {
            return Map.of
            (
                // 'data' is not listed in 'Scope::getMembers' so that it is invisible.
                "data",     new Literal(line,Primitives.STR.type) {@Override public Object value() {return t.wrapTrace(line,"str::data",() -> s);}},
                "length",   cfunc(a -> t.wrapTrace(line,"str::length",s::length),line,Primitives.INT.type),
                "substring",cfunc
                (
                    a -> t.wrapTrace
                    (line,"str::substring",() ->
                        new LiteralStr(t,line,s.substring(((Long)a[0].value()).intValue(),((Long)a[1].value()).intValue()))
                    ),
                    line,Primitives.STR.type,Primitives.INT.type,Primitives.INT.type
                )
            );
        }
        @Override
        public boolean equals(final Object obj)
        {
            return obj == this ||
            (
                obj instanceof Literal l && l.type.equals(type) &&
                strData(this).equals(strData(l))
            );
        }
    }
    private static Expr list(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Assume '\[' already eaten.
        final int line = tokens.peek(-1).line();
        // Parse list type.
        final Type subtype = type(chkConst(tokens),r,trace,tokens,scope);
        if(mismatch(ItrMode.next,tokens,r,TokenType.COLON))
        {
            skip(tokens,TokenType.RBRACKET,TokenType.LBRACKET);
            return null;
        }
        // Parse elements.
        final List<Expr> l = new ArrayList<>();
        boolean ret = true;
        if(matches(ItrMode.peek,tokens,TokenType.RBRACKET)) tokens.next(); // Advance past '\]'.
        else
        {
            do
            {
                // Parse expression.
                final Expr e = parseExpr(r,trace,tokens,scope);
                if(e != null)
                {
                    if(triviallyConvertible(subtype,e.type))
                    {
                        l.add(new Expr(e.line,subtype)
                        {
                            @Override
                            protected Literal eval(final Scope<Value> s)
                            {
                                return trivialConversion(e.evaluate(s),subtype);
                            }
                        });
                        continue;
                    }
                    // Type mismatch detected.
                    r.report
                    (
                        e.line,
                        "Expression of type "+e.type+
                        " cannot be assigned to list of type "+subtype
                    );
                }
                ret = false;
                skip(tokens,TokenType.RBRACKET,TokenType.LBRACKET);
                break;
            }
            while(matches(ItrMode.next,tokens,TokenType.COMMA));
            // Ensure that the loop condition broke on a closing bracket.
            ret = ret && !mismatch(ItrMode.prev,tokens,r,TokenType.RBRACKET);
        }
        final Type ct = new Type(false,BaseType.LIST,null,subtype);
        return ret
            ? new Expr(line,ct)
              {
                  @Override
                  public Literal eval(final Scope<Value> s)
                  {
                      return trace.wrapTrace
                      (line,"literalList::eval",() -> {
                          // Convert the initializer into a literal list.
                          final List<Literal> v = new ArrayList<>();
                          for(final Expr e : l) v.add(e.evaluate(s));
                          return new LiteralList(trace,line,ct,v);
                      });
                  }
              }
            : null;
    }
    private static Expr struct(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Assume '\{' already eaten.
        final int line = tokens.peek(-1).line();
        // Parse struct type.
        final Map<String,Type> struct;
        final String structName;
        {
            // Get the struct identifier. Nested struct declarations
            // are not allowed, so no '.'s need to be parsed.
            final Token t = eat(ItrMode.next,tokens,r,TokenType.ID);
            structName = t.value();
            if(t.type() != TokenType.ID) struct = null;
            else if((struct = scope.getStruct(t.value())) == null)
                r.report(t.line(),"Struct '"+t.value()+"' is undefined");
            if(struct == null)
            {
                skip(tokens,TokenType.RBRACE,TokenType.LBRACE);
                return null;
            }
        }
        // Ensure that there's a colon after the type.
        if(mismatch(ItrMode.next,tokens,r,TokenType.COLON))
        {
            skip(tokens,TokenType.RBRACE,TokenType.LBRACE);
            return null;
        }
        // Parse elements.
        final Map<String,Expr> m = new HashMap<>();
        boolean ret = true;
        if(matches(ItrMode.peek,tokens,TokenType.RBRACE)) tokens.next(); // Advance past '\}'.
        else
        {
            do
            {
                // Get an identifier. I could emit warnings about duplicate definitions, but I'm lazy.
                final Token id = eat(ItrMode.next,tokens,r,TokenType.ID);
                // Ensure that the token following an identifier is a '='.
                if(id.type() != TokenType.ID || mismatch(ItrMode.next,tokens,r,TokenType.ASSIGN))
                {
                    skip(tokens,TokenType.RBRACE,TokenType.LBRACE);
                    return null;
                }
                // Parse expression.
                final Expr e = parseExpr(r,trace,tokens,scope);
                if(e != null)
                {
                    final Type ct = struct.get(id.value());
                    if(ct != null && triviallyConvertible(ct,e.type))
                    {
                        m.put(id.value(),new Expr(e.line,ct)
                        {
                            @Override
                            protected Literal eval(final Scope<Value> s)
                            {
                                return trivialConversion(e.evaluate(s),ct);
                            }
                        });
                        continue;
                    }
                    // Type mismatch detected.
                    if(ct == null)
                        r.report
                        (
                            id.line(),
                            "Field '"+id.value()+
                            "' in struct type '"+structName+
                            "' is undefined"
                        );
                    else
                        r.report
                        (
                            e.line,
                            "Expression of type "+e.type+
                            " cannot be assigned to field of type "+struct.get(id.value())+
                            " in struct of type '"+structName+'\''
                        );
                }
                ret = false;
            }
            while(matches(ItrMode.next,tokens,TokenType.COMMA));
            // Ensure that the loop condition broke on a closing brace.
            ret = ret && !mismatch(ItrMode.prev,tokens,r,TokenType.RBRACE);
        }
        if(ret)
        {
            // I'm too lazy to default-construct the missing elements, so
            // I just report it as an error.
            if(struct.keySet().equals(m.keySet()))
                return new Expr(line,new Type(false,BaseType.STRUCT,structName,null))
                {
                    @Override
                    public Literal eval(final Scope<Value> s)
                    {
                        return trace.wrapTrace
                        (line,"literalStruct::eval",() -> literal(s2 -> {
                            // Convert the initializer to a literal struct.
                            final Map<String,Literal> v = new HashMap<>();
                            for(final Map.Entry<String,Expr> e : m.entrySet())
                                v.put(e.getKey(),e.getValue().evaluate(s2));
                            return v;
                        },s));
                    }
                };
            final StringJoiner sj = new StringJoiner("','","['","']");
            for(final String s : struct.keySet()) if(m.containsKey(s)) sj.add(s);
            r.report(line,"Missing initialization for fields: "+sj);
        }
        return null;
    }
    private static Expr func(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Assume 'func' already eaten.
        // Must have the form 'func<[Type]>([Type Name {, Type Name}]) \{ Statements \} [([Arg {, Arg}])]'
        
        // Parse return type.
        final int line = tokens.peek(-1).line();
        final Type nct = funcRetType(r,trace,tokens,scope);
        
        // Parse arguments.
        final Scope<Type> s = new Scope<>(scope);
        final Type[] argt;
        final String[] argn;
        {
            record Argument(String name,Type type) {}
            final List<Argument> l = new ArrayList<>();
            if(matches(ItrMode.peek,tokens,TokenType.RPAREN)) tokens.next(); // Advance past '\)'.
            else
            {
                do
                {
                    // Parse type.
                    final Type act = type(chkConst(tokens),r,trace,tokens,scope);
                    if(act == null)
                    {
                        skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                        return null;
                    }
                    
                    // Parse name.
                    final Token t = eat(ItrMode.next,tokens,r,TokenType.ID);
                    if(t.type() != TokenType.ID)
                    {
                        skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                        return null;
                    }
                    
                    l.add(new Argument(t.value(),act));
                }
                while(matches(ItrMode.next,tokens,TokenType.COMMA));
                // Ensure that the loop condition broke on a closing parentheses.
                if(mismatch(ItrMode.prev,tokens,r,TokenType.RPAREN)) return null;
            }
            argt = new Type[l.size()];
            argn = new String[l.size()];
            int i = 0;
            for(final Argument arg : l)
                s.putField(argn[i  ] = arg.name,
                           argt[i++] = arg.type);
        }
        
        // Parse body.
        if(mismatch(ItrMode.next,tokens,r,TokenType.LBRACE)) return null;
        final Stmt body = parseBlock(r,trace,tokens,s,nct);
        if(body == null) return null;
        // I'm far too lazy to check the block for return statement here,
        // so it will be done during runTime.
        
        return new Expr(line,new Type(false,BaseType.FUNC,null,nct,argt))
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace
                (line,"literalFunc::eval",() -> {
                    // Freeze the current scope. The scope must be frozen
                    // in order to copy the variables for use whenever it
                    // is invoked.
                    final Scope<Value> s2 = new Scope<>(s);
                    return new Func(line,nct)
                    {
                        @Override
                        public Literal call(final Literal...args)
                        {
                            return trace.wrapTrace
                            (line,"func::call",() -> {
                                for(int i = 0;i < argt.length;++i)
                                    s2.getField(argn[i]).setValue(trace,s2,args[i].value());
                                return body.eval(s2);
                            });
                        }
                    };
                });
            }
        };
    }
    private static Expr id(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                           final Token id)
    {
        // Can be identifier, list index, function call, or field access.
        final List<Token> p = new ArrayList<>();
        // Eat all the identifiers until the '.'s are exhausted.
        // Don't puke afterward, will do with check to suffix.
        while(nonEOF(ItrMode.next,tokens,r).type() == TokenType.DOT)
            p.add(eat(ItrMode.next,tokens,r,TokenType.ID));
        // Get the token that broke the loop condition.
        final Token op = tokens.peek(-1);
        final String start = id.value();
        final String[] path;
        final Type ct;
        {
            // Get the first identifier.
            Type t = scope.getField(id.value());
            if(t == null)
            {
                r.report(id.line(),"Identifier '"+id.value()+"' is undefined");
                return null;
            }
            path = new String[p.size()+1];
            path[0] = start;
            int i = 0;
            for(final Token s : p)
            {
                // Get the field's members.
                final Map<String,Type> members = scope.getMembers(t);
                if(members == null)
                {
                    r.report(s.line(),"Type '"+t+"' does not have any members");
                    return null;
                }
                // Get member type.
                final Type t2 = members.get(path[+i] = s.value());
                if(t2 == null)
                {
                    // Unknown field detected.
                    r.report
                    (
                        s.line(),
                        "Member '"+s.value()+
                        "' of type "+t+
                        " is undefined"
                    );
                    return null;
                }
                t = t2;
            }
            ct = t;
        }
        // Parse suffix.
        return suffix
        (
            r,trace,tokens,scope,
            path.length == 1
                ? new Value(op.line(),ct)
                  {
                      @Override
                      protected void setValue(final Scope<Value> s,final Object v)
                      {
                          trace.wrapTrace(line,"id::setValue",() -> s.getField(start).setValue(trace,s,v));
                      }
                      @Override
                      public Literal eval(final Scope<Value> s)
                      {
                          return trace.wrapTrace(line,"id::eval",() -> s.getField(start).evaluate(s));
                      }
                  }
                : new Value(op.line(),ct)
                  {
                      @SuppressWarnings("unchecked")
                      Map<String,Literal> m(final Scope<Value> s)
                      {
                          Literal v = s.getField(start).evaluate(s);
                          for(int i = 1;i < path.length;++i)
                              v = ((Map<String,Literal>)v.value()).get(path[i]);
                          return (Map<String,Literal>)v.value();
                      }
                      @Override
                      protected void setValue(final Scope<Value> s,final Object v)
                      {
                          trace.wrapTrace(line,"id::setValue",() -> m(s).put(path[path.length-1],literal(s2 -> v,s)));
                      }
                      @Override
                      public Literal eval(final Scope<Value> s)
                      {
                          return trace.wrapTrace(line,"id::eval",() -> m(s).get(path[path.length-1]));
                      }
                  },
            op
        );
    }
    private static Expr highPrecedence(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        Token t = nonEOF(ItrMode.next,tokens,r);
        return switch(t.type())
        {
            case TRUE,FALSE ->
            {
                final boolean b = Boolean.parseBoolean(t.value());
                yield new Literal(t.line(),Primitives.BOOL.type)
                {
                    @Override public Object value() {return trace.wrapTrace(line,"literalBool::value",() -> b);}
                };
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
                yield new Literal(t.line(),Primitives.INT.type)
                {
                    @Override public Object value() {return trace.wrapTrace(line,"literalInt::value",() -> l);}
                };
            }
            case LIT_FLOAT ->
            {
                final double d = Double.parseDouble(t.value());
                yield new Literal(t.line(),Primitives.FLOAT.type)
                {
                    @Override public Object value() {return trace.wrapTrace(line,"literalFloat::value",() -> d);}
                };
            }
            case LIT_STR -> new LiteralStr(trace,t.line(),t.value());
            case FUNC ->
            {
                final Expr o = func(r,trace,tokens,scope);
                if(o != null && nonEOF(ItrMode.peek,tokens,r).type() == TokenType.LPAREN)
                {
                    tokens.next(); // Eat '\('.
                    // Parse anonymous function call.
                    yield call(r,trace,tokens,scope,o);
                }
                yield o;
            }
            case LBRACKET -> list(r,trace,tokens,scope);
            case LBRACE -> struct(r,trace,tokens,scope);
            case ID -> id(r,trace,tokens,scope,t);
            case INC,DEC,ADD,SUB,NOT,BITNOT -> prefix(r,trace,tokens,t,scope);
            case LPAREN ->
            {
                // Try to get a sub-expression.
                final Expr e = assignment
                (r,trace,tokens,scope,
                    ternary
                    (r,trace,tokens,scope,
                        binaryOp
                        (r,trace,tokens,scope,
                            highPrecedence(r,trace,tokens,scope)
                        )
                    )
                );
                yield mismatch(ItrMode.next,tokens,r,TokenType.RPAREN)? null : e;
            }
            default -> null;
        };
    }
    public static String toString(final Literal l)
    {
        return switch(l.type.simple())
        {
            case BOOL,INT,FLOAT -> l.value().toString();
            case STR -> strData(l);
            case FUNC -> l.type.toString();
            case LIST ->
            {
                final List<Literal> v = listData(l);
                final StringJoiner sj = new StringJoiner(",","[","]");
                for(final Literal x : v) sj.add(toString(x));
                yield sj.toString();
            }
            case STRUCT ->
            {
                @SuppressWarnings("unchecked")
                final Map<String,Literal> v = (Map<String,Literal>)l.value();
                final StringJoiner sj = new StringJoiner(",","{","}");
                for(final Map.Entry<String,Literal> e : v.entrySet())
                    sj.add(e.getKey()+':'+toString(e.getValue()));
                yield sj.toString();
            }
            case VOID -> "void";
        };
    }
    @FunctionalInterface private interface operator {Object get(Literal x,Literal y);}
    private static record Operation(operator op,Type ret) {}
    private static Operation getOp(final Expr a,final Expr b,final TokenType type,
                                   final ErrReporter r,final Trace trace,final int line)
    {
        final operator op;
        final Type ct = switch(type)
        {
            case AND,OR,EQ,NEQ,
                 GT,LT,GEQ,LEQ   ->
            {
                op = switch(type)
                {
                    case OR  -> (x,y) -> x.conditional() || y.conditional();
                    case AND -> (x,y) -> x.conditional() && y.conditional();
                    case EQ  -> Literal::equals;
                    case NEQ -> (x,y) ->!x.equals(y);
                    case GT,LT,GEQ,LEQ ->
                    {
                        // Void, struct, and function types aren't comparable.
                        // String and list types are only comparable to objects of the same type.
                        // Number and boolean types are comparable to themselves and each other.
                        if
                        (
                            switch(a.type.simple())
                            {
                                case VOID,STRUCT,FUNC -> true;
                                case STR,LIST -> b.type.simple() != a.type.simple();
                                default -> switch(b.type.simple())
                                {
                                    case VOID,STRUCT,FUNC,LIST,STR -> true;
                                    default -> false;
                                };
                            }
                        )
                        {
                            r.report
                            (
                                line,
                                "Expression of type "+a.type.simple()+
                                " cannot be compared to type "+b.type.simple()
                            );
                            yield null;
                        }
                        yield switch(a.type.simple())
                        {
                            case LIST  -> switch(type)
                            {
                                case GT  -> (x,y) -> listData(x).size() >
                                                     listData(y).size();
                                case LT  -> (x,y) -> listData(x).size() <
                                                     listData(y).size();
                                case GEQ -> (x,y) -> listData(x).size() >=
                                                     listData(y).size();
                                case LEQ -> (x,y) -> listData(x).size() <=
                                                     listData(y).size();
                                default  -> throw new AssertionError();
                            };
                            case STR   -> switch(type)
                            {
                                case GT  -> (x,y) -> (strData(x)).compareTo(strData(y)) >  0;
                                case LT  -> (x,y) -> (strData(x)).compareTo(strData(y)) <  0;
                                case GEQ -> (x,y) -> (strData(x)).compareTo(strData(y)) >= 0;
                                case LEQ -> (x,y) -> (strData(x)).compareTo(strData(y)) <= 0;
                                default  -> throw new AssertionError();
                            };
                            case BOOL  -> switch(b.type.simple())
                            {
                                case BOOL  -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Boolean)x.value() &&
                                                        !(Boolean)y.value();
                                    case LT  -> (x,y) ->!(Boolean)x.value() &&
                                                         (Boolean)y.value();
                                    case GEQ -> (x,y) -> (Boolean)x.value() ||
                                                        !(Boolean)y.value();
                                    case LEQ -> (x,y) ->!(Boolean)x.value() ||
                                                         (Boolean)y.value();
                                    default  -> throw new AssertionError();
                                };
                                case INT   -> switch(type)
                                {
                                    case GT  -> (x,y) -> ((Boolean)x.value()? 1L:0L) >
                                                             (Long)y.value();
                                    case LT  -> (x,y) -> ((Boolean)x.value()? 1L:0L) <
                                                             (Long)y.value();
                                    case GEQ -> (x,y) -> ((Boolean)x.value()? 1L:0L) >=
                                                             (Long)y.value();
                                    case LEQ -> (x,y) -> ((Boolean)x.value()? 1L:0L) <=
                                                             (Long)y.value();
                                    default  -> throw new AssertionError();
                                };
                                case FLOAT -> switch(type)
                                {
                                    case GT  -> (x,y) -> ((Boolean)x.value()? 1D:0D) >
                                                           (Double)y.value();
                                    case LT  -> (x,y) -> ((Boolean)x.value()? 1D:0D) <
                                                           (Double)y.value();
                                    case GEQ -> (x,y) -> ((Boolean)x.value()? 1D:0D) >=
                                                           (Double)y.value();
                                    case LEQ -> (x,y) -> ((Boolean)x.value()? 1D:0D) <=
                                                           (Double)y.value();
                                    default  -> throw new AssertionError();
                                };
                                default    -> throw new AssertionError();
                            };
                            case INT   -> switch(b.type.simple())
                            {
                                case BOOL  -> switch(type)
                                {
                                    case GT  -> (x,y) ->     (Long)x.value()         >
                                                         ((Boolean)y.value()? 1L:0L);
                                    case LT  -> (x,y) ->     (Long)x.value()         <
                                                         ((Boolean)y.value()? 1L:0L);
                                    case GEQ -> (x,y) ->     (Long)x.value()         >=
                                                         ((Boolean)y.value()? 1L:0L);
                                    case LEQ -> (x,y) ->     (Long)x.value()         <=
                                                         ((Boolean)y.value()? 1L:0L);
                                    default  -> throw new AssertionError();
                                };
                                case INT   -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Long)x.value() >  (Long)y.value();
                                    case LT  -> (x,y) -> (Long)x.value() <  (Long)y.value();
                                    case GEQ -> (x,y) -> (Long)x.value() >= (Long)y.value();
                                    case LEQ -> (x,y) -> (Long)x.value() <= (Long)y.value();
                                    default  -> throw new AssertionError();
                                };
                                case FLOAT -> switch(type)
                                {
                                    case GT  -> (x,y) -> ((Long)x.value()).doubleValue() >  (Double)y.value();
                                    case LT  -> (x,y) -> ((Long)x.value()).doubleValue() <  (Double)y.value();
                                    case GEQ -> (x,y) -> ((Long)x.value()).doubleValue() >= (Double)y.value();
                                    case LEQ -> (x,y) -> ((Long)x.value()).doubleValue() <= (Double)y.value();
                                    default  -> throw new AssertionError();
                                };
                                default    -> throw new AssertionError();
                            };
                            case FLOAT -> switch(b.type.simple())
                            {
                                case BOOL  -> switch(type)
                                {
                                    case GT  -> (x,y) ->   (Double)x.value()         >
                                                         ((Boolean)y.value()? 1D:0D);
                                    case LT  -> (x,y) ->   (Double)x.value()         <
                                                         ((Boolean)y.value()? 1D:0D);
                                    case GEQ -> (x,y) ->   (Double)x.value()         >=
                                                         ((Boolean)y.value()? 1D:0D);
                                    case LEQ -> (x,y) ->   (Double)x.value()         <=
                                                         ((Boolean)y.value()? 1D:0D);
                                    default  -> throw new AssertionError();
                                };
                                case INT   -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Double)x.value() >  ((Long)y.value()).doubleValue();
                                    case LT  -> (x,y) -> (Double)x.value() <  ((Long)y.value()).doubleValue();
                                    case GEQ -> (x,y) -> (Double)x.value() >= ((Long)y.value()).doubleValue();
                                    case LEQ -> (x,y) -> (Double)x.value() <= ((Long)y.value()).doubleValue();
                                    default  -> throw new AssertionError();
                                };
                                case FLOAT -> switch(type)
                                {
                                    case GT  -> (x,y) -> (Double)x.value() >  (Double)y.value();
                                    case LT  -> (x,y) -> (Double)x.value() <  (Double)y.value();
                                    case GEQ -> (x,y) -> (Double)x.value() >= (Double)y.value();
                                    case LEQ -> (x,y) -> (Double)x.value() <= (Double)y.value();
                                    default  -> throw new AssertionError();
                                };
                                default    -> throw new AssertionError();
                            };
                            default    -> throw new AssertionError();
                        };
                    }
                    default  -> throw new AssertionError();
                };
                yield Primitives.BOOL.type;
            }
            case ADD             -> switch(a.type.simple())
            {
                case BOOL,INT,FLOAT -> switch(b.type.simple())
                {
                    case BOOL,INT,FLOAT ->
                    {
                        op = switch(a.type.simple())
                        {
                            case BOOL  -> switch(b.type.simple())
                            {
                                case BOOL  -> (x,y) -> ((Boolean)x.value()?1L:0L)+
                                                       ((Boolean)y.value()?1L:0L);
                                case INT   -> (x,y) -> ((Boolean)x.value()?1L:0L)+
                                                           (Long)y.value();
                                case FLOAT -> (x,y) -> ((Boolean)x.value()?1D:0D)+
                                                         (Double)y.value();
                                default    -> throw new AssertionError();
                            };
                            case INT   -> switch(b.type.simple())
                            {
                                case BOOL  -> (x,y) ->     (Long)x.value() +
                                                       ((Boolean)y.value()?1L:0L);
                                case INT   -> (x,y) ->     (Long)x.value() +
                                                           (Long)y.value();
                                case FLOAT -> (x,y) ->     (Long)x.value() +
                                                         (Double)y.value();
                                default    -> throw new AssertionError();
                            };
                            case FLOAT -> switch(b.type.simple())
                            {
                                case BOOL  -> (x,y) ->   (Double)x.value() +
                                                       ((Boolean)y.value()?1D:0D);
                                case INT   -> (x,y) ->   (Double)x.value() +
                                                           (Long)y.value();
                                case FLOAT -> (x,y) ->   (Double)x.value() +
                                                         (Double)y.value();
                                default    -> throw new AssertionError();
                            };
                            default    -> throw new AssertionError();
                        };
                        yield a.type.simple() == BaseType.FLOAT || b.type.simple() == BaseType.FLOAT
                            ? Primitives.FLOAT.type
                            : Primitives.INT.type;
                    }
                    case STR ->
                    {
                        op = (x,y) -> new LiteralStr(trace,line,toString(x)+toString(y));
                        yield Primitives.STR.type;
                    }
                    default ->
                    {
                        r.report(line,"Cannot add type "+a.type.simple()+" to type "+b.type.simple());
                        op = null;
                        yield null;
                    }
                };
                case STR ->
                {
                    op = (x,y) -> new LiteralStr(trace,line,toString(x)+toString(y));
                    yield Primitives.STR.type;
                }
                case LIST -> //noinspection SwitchStatementWithTooFewBranches
                             switch(b.type.simple())
                {
                    case LIST ->
                    {
                        if(!a.type.complex().equals(b.type.complex()))
                        {
                            r.report
                            (
                                line,
                                "Cannot add list of type "+b.type.complex()+
                                " to list of type "+a.type.complex()
                            );
                            op = null;
                            yield null;
                        }
                        op = (x,y) ->
                        {
                            final List<Literal> l = new ArrayList<>(listData(x));
                            l.addAll(listData(y));
                            return new LiteralList(trace,line,a.type,l);
                        };
                        yield a.type;
                    }
                    default   ->
                    {
                        r.report(line,"Cannot add type list and type "+b.type.simple());
                        op = null;
                        yield null;
                    }
                };
                default ->
                {
                    r.report(line,"Cannot add type "+a.type.simple()+" to type "+b.type.simple());
                    op = null;
                    yield null;
                }
            };
            case SUB,MUL,DIV,MOD,
                 BITAND,BITOR,
                 BITXOR,LSH,RSH,
                 LRSH            ->
            {
                final BaseType sa = a.type.simple(),sb = b.type.simple();
                if
                (
                    switch(sa)
                    {
                        case BOOL,INT,FLOAT -> false;
                        default             -> true;
                    } ||
                    switch(sb)
                    {
                        case BOOL,INT,FLOAT -> false;
                        default             -> true;
                    }
                )
                {
                    r.report
                    (
                        line,
                        "Cannot "+switch(type)
                        {
                            case SUB    -> "subtract";
                            case MUL    -> "multiply";
                            case DIV    -> "divide";
                            case MOD    -> "compute the remainder of";
                            case BITAND -> "bitwise and";
                            case BITOR  -> "bitwise or";
                            case BITXOR -> "bitwise exlcusive or";
                            case LSH    -> "left bit shift";
                            case RSH    -> "arithmetic right bit shift";
                            case LRSH   -> "logical right bit shift";
                            default     -> throw new AssertionError();
                        }+" type "+sa+" and "+sb
                    );
                    op = null;
                    yield null;
                }
                op = switch(sa)
                {
                    case BOOL  -> switch(sb)
                    {
                        case BOOL  -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Boolean)x.value()?1L:0L) -
                                                    ((Boolean)y.value()?1L:0L);
                            case MUL,
                                 BITAND -> (x,y) -> (Boolean)x.value()&&(Boolean)y.value()?1L:0L;
                            case DIV    -> (x,y) ->
                            {
                                if(!(Boolean)y.value()) trace.report(line,"Divide by zero");
                                return (Boolean)x.value()?1L:0L;
                            };
                            case MOD    -> (x,y) -> 0;
                            case BITOR  -> (x,y) -> (Boolean)x.value()||(Boolean)y.value()?1L:0L;
                            case BITXOR -> (x,y) -> x.value()!=y.value()?1L:0L;
                            case LSH    -> (x,y) -> (Boolean)x.value()?(Boolean)y.value()?2L:1L:0L;
                            case RSH,
                                 LRSH   -> (x,y) -> (Boolean)x.value()&&!(Boolean)y.value()?1L:0L;
                            default     -> throw new AssertionError();
                        };
                        case INT   -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Boolean)x.value()?1L:0L) -
                                                        (Long)y.value();
                            case MUL    -> (x,y) -> (Boolean)x.value()?(Long)y.value():0L;
                            case DIV    -> (x,y) ->
                            {
                                try {return ((Boolean)x.value()?1L:0L)/(Long)y.value();}
                                catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                return null;
                            };
                            case MOD    -> (x,y) -> ((Boolean)x.value()?1L:0L) % (Long)y.value();
                            case BITAND -> (x,y) -> (Boolean)x.value()?(Long)y.value()&1L:0L;
                            case BITOR  -> (x,y) -> (Long)y.value()|((Boolean)x.value()?1L:0L);
                            case BITXOR -> (x,y) -> (Long)y.value()^((Boolean)x.value()?1L:0L);
                            case LSH    -> (x,y) -> ((Boolean)x.value()?1L:0L)<<(Long)y.value();
                            case RSH,
                                 LRSH   -> (x,y) -> ((Boolean)x.value()?1L:0L)>>>(Long)y.value();
                            default     -> throw new AssertionError();
                        };
                        case FLOAT -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Boolean)x.value()?1D:0D) -
                                                      (Double)y.value();
                            case MUL    -> (x,y) -> (Boolean)x.value()?(Double)y.value():0D;
                            case DIV    -> (x,y) ->
                            {
                                try {return (Boolean)x.value()?1D:0D/(Double)y.value();}
                                catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                return null;
                            };
                            case MOD    -> (x,y) -> ((Boolean)x.value()?1D:0D) % (Double)y.value();
                            case BITAND -> (x,y) -> ltod((Boolean)x.value()? dtol((Double)y.value())&1L: 0L);
                            case BITOR  -> (x,y) -> ltod(dtol((Double)y.value())|((Boolean)x.value()?1L:0L));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)y.value())^((Boolean)x.value()?1L:0L));
                            case LSH    -> (x,y) -> (double)(((Boolean)x.value()?1L:0L)<<((Double)y.value()).longValue());
                            case RSH,
                                 LRSH   -> (x,y) -> (double)(((Boolean)x.value()?1L:0L)>>>((Double)y.value()).longValue());
                            default     -> throw new AssertionError();
                        };
                        default    -> throw new AssertionError();
                    };
                    case INT   -> switch(sb)
                    {
                        case BOOL  -> switch(type)
                        {
                            case SUB    -> (x,y) ->     (Long)x.value() -
                                                    ((Boolean)y.value()?1L:0L);
                            case MUL    -> (x,y) -> (Boolean)y.value()?(Long)x.value():0L;
                            case DIV    -> (x,y) ->
                            {
                                if(!(Boolean)y.value()) trace.report(line,"Divide by zero");
                                return x.value();
                            };
                            case MOD    -> (x,y) -> 0L;
                            case BITAND -> (x,y) -> (Boolean)y.value()?   (Long)x.value()&1L:0L;
                            case BITOR  -> (x,y) -> (Long)x.value()|  ((Boolean)y.value()?1L:0L);
                            case BITXOR -> (x,y) -> (Long)x.value()^  ((Boolean)y.value()?1L:0L);
                            case LSH    -> (x,y) -> (Long)x.value()<< ((Boolean)y.value()?1L:0L);
                            case RSH    -> (x,y) -> (Long)x.value()>> ((Boolean)y.value()?1L:0L);
                            case LRSH   -> (x,y) -> (Long)x.value()>>>((Boolean)y.value()?1L:0L);
                            default     -> throw new AssertionError();
                        };
                        case INT   -> switch(type)
                        {
                            case SUB    -> (x,y) -> (Long)x.value() - (Long)y.value();
                            case MUL    -> (x,y) -> (Long)x.value() * (Long)y.value();
                            case DIV    -> (x,y) ->
                            {
                                try {return (Long)x.value()/(Long)y.value();}
                                catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                return null;
                            };
                            case MOD    -> (x,y) -> (Long)x.value() % (Long)y.value();
                            case BITAND -> (x,y) -> (Long)x.value() & (Long)y.value();
                            case BITOR  -> (x,y) -> (Long)x.value() | (Long)y.value();
                            case BITXOR -> (x,y) -> (Long)x.value() ^ (Long)y.value();
                            case LSH    -> (x,y) -> (Long)x.value() <<(Long)y.value();
                            case RSH    -> (x,y) -> (Long)x.value() >>(Long)y.value();
                            case LRSH   -> (x,y) -> (Long)x.value()>>>(Long)y.value();
                            default     -> throw new AssertionError();
                        };
                        case FLOAT -> switch(type)
                        {
                            case SUB    -> (x,y) -> ((Long)x.value()).doubleValue() - (Double)y.value();
                            case MUL    -> (x,y) -> ((Long)x.value()).doubleValue() * (Double)y.value();
                            case DIV    -> (x,y) ->
                            {
                                try {return ((Long)x.value()).doubleValue()/(Double)y.value();}
                                catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                return null;
                            };
                            case MOD    -> (x,y) -> ((Long)x.value()).doubleValue() % (Double)y.value();
                            case BITAND -> (x,y) -> ltod((Long)x.value() & dtol((Double)y.value()));
                            case BITOR  -> (x,y) -> ltod((Long)x.value() | dtol((Double)y.value()));
                            case BITXOR -> (x,y) -> ltod((Long)x.value() ^ dtol((Double)y.value()));
                            case LSH    -> (x,y) -> (double)((Long)x.value() <<((Double)y.value()).longValue());
                            case RSH    -> (x,y) -> (double)((Long)x.value() >>((Double)y.value()).longValue());
                            case LRSH   -> (x,y) -> (double)((Long)x.value()>>>((Double)y.value()).longValue());
                            default     -> throw new AssertionError();
                        };
                        default    -> throw new AssertionError();
                    };
                    case FLOAT -> switch(sb)
                    {
                        case BOOL  -> switch(type)
                        {
                            case SUB    -> (x,y) ->   (Double)x.value() -
                                                    ((Boolean)y.value()?1D:0D);
                            case MUL    -> (x,y) -> (Boolean)y.value()?(Double)x.value():0D;
                            case DIV    -> (x,y) ->
                            {
                                if(!(Boolean)y.value()) trace.report(line,"Divide by zero");
                                return x.value();
                            };
                            case MOD    -> (x,y) -> (Double)x.value()%((Boolean)y.value()?1D:0D);
                            case BITAND -> (x,y) -> ltod((Boolean)y.value()?   dtol((Double)x.value())&1L:0L);
                            case BITOR  -> (x,y) -> ltod(dtol((Double)x.value())|  ((Boolean)y.value()?1L:0L));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)x.value())^  ((Boolean)y.value()?1L:0L));
                            case LSH    -> (x,y) -> ltod(dtol((Double)x.value())<< ((Boolean)y.value()?1L:0L));
                            case RSH    -> (x,y) -> ltod(dtol((Double)x.value())>> ((Boolean)y.value()?1L:0L));
                            case LRSH   -> (x,y) -> ltod(dtol((Double)x.value())>>>((Boolean)y.value()?1L:0L));
                            default     -> throw new AssertionError();
                        };
                        case INT   -> switch(type)
                        {
                            case SUB    -> (x,y) -> (Double)x.value() - ((Long)y.value()).doubleValue();
                            case MUL    -> (x,y) -> (Double)x.value() * ((Long)y.value()).doubleValue();
                            case DIV    -> (x,y) ->
                            {
                                try {return (Double)x.value()/((Long)y.value()).doubleValue();}
                                catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                return null;
                            };
                            case MOD    -> (x,y) -> (Double)x.value() % ((Long)y.value()).doubleValue();
                            case BITAND -> (x,y) -> ltod(dtol((Double)x.value()) & (Long)y.value());
                            case BITOR  -> (x,y) -> ltod(dtol((Double)x.value()) | (Long)y.value());
                            case BITXOR -> (x,y) -> ltod(dtol((Double)x.value()) ^ (Long)y.value());
                            case LSH    -> (x,y) -> (Double)x.value()*(double)(1L<<(Long)y.value());
                            case RSH,
                                 LRSH   -> (x,y) -> (Double)x.value()/(double)(1L<<(Long)y.value());
                            default     -> throw new AssertionError();
                        };
                        case FLOAT -> switch(type)
                        {
                            case SUB    -> (x,y) -> (Double)x.value() - (Double)y.value();
                            case MUL    -> (x,y) -> (Double)x.value() * (Double)y.value();
                            case DIV    -> (x,y) ->
                            {
                                try {return (Double)x.value()/(Double)y.value();}
                                catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                return null;
                            };
                            case MOD    -> (x,y) -> (Double)x.value() % (Double)y.value();
                            case BITAND -> (x,y) -> ltod(dtol((Double)x.value()) & dtol((Double)y.value()));
                            case BITOR  -> (x,y) -> ltod(dtol((Double)x.value()) | dtol((Double)y.value()));
                            case BITXOR -> (x,y) -> ltod(dtol((Double)x.value()) ^ dtol((Double)y.value()));
                            case LSH    -> (x,y) -> (Double)x.value()*Math.pow(2D,(Double)y.value());
                            case RSH,
                                 LRSH   -> (x,y) -> (Double)x.value()/Math.pow(2D,(Double)y.value());
                            default     -> throw new AssertionError();
                        };
                        default    -> throw new AssertionError();
                    };
                    default    -> throw new AssertionError();
                };
                yield a.type.simple() == BaseType.FLOAT || b.type.simple() == BaseType.FLOAT
                    ? Primitives.FLOAT.type
                    : Primitives.INT.type;
            }
            default              ->
            {
                r.report(line,"Invalid binary operator: "+type);
                op = null;
                yield null;
            }
        };
        return ct == null? null : new Operation(op,ct);
    }
    private static Expr binaryOp(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                 final Expr first)
    {
        final Expr[] E = new Expr[11];   // 10 happens to be the number of different precedence levels, so
        final Token[] O = new Token[10]; // the "stack" can have constant size.
        E[0] = first;
        int i = 0;
        // Loop until a non-operator token is encountered.
        while(precedence(nonEOF(ItrMode.peek,tokens,r).type()) != -1)
        {
            // Eat the operator, and the expression to the right of it.
            O[i] = tokens.next();
            {
                final Expr e = highPrecedence(r,trace,tokens,scope);
                if(e == null) return null;
                E[++i] = e;
            }
            
            // While there are still operators on the stack and the cursor's
            // operator token is less than the
            while(i != 0 && precedence(nonEOF(ItrMode.peek,tokens,r).type()) <= precedence(O[i-1].type()))
            {
                final Expr b = E[i--],a = E[i];
                final Token o = O[i];
                final Operation op = getOp(a,b,o.type(),r,trace,o.line());
                if(op == null) return null;
                final String opName = o.toString();
                E[i] = new Expr(o.line(),op.ret)
                {
                    @Override
                    public Literal eval(final Scope<Value> s)
                    {
                        return trace.wrapTrace
                        (line,"operator_"+opName+"::eval",() -> literal(s2 ->
                            op.op.get(a.evaluate(s2),b.evaluate(s2))
                        ,s));
                    }
                };
            }
        }
        if(i != 0)
        {
            r.report(first.line,"Invalid expression");
            return null;
        }
        return E[0];
    }
    private static Expr mediumPrecedence(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Get a high-precedence expression.
        final Expr e = highPrecedence(r,trace,tokens,scope);
        if(e == null) return null;
        // Parse binary operator, if necessary.
        return precedence(nonEOF(ItrMode.peek,tokens,r).type()) != -1
            ? binaryOp(r,trace,tokens,scope,e)
            : e;
    }
    private static Expr ternary(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                final Expr condition)
    {
        if(matches(ItrMode.peek,tokens,TokenType.CONDITION))
        {
            // Eat '?'.
            final int line = tokens.next().line();
            // Eat true branch.
            final Expr t = parseExpr(r,trace,tokens,scope);
            if(mismatch(ItrMode.peek,tokens,r,TokenType.COLON)) return null;
            // Eat ':'.
            tokens.next();
            // Eat false branch.
            final Expr f = lowPrecedence(r,trace,tokens,scope);
            if(t == null || f == null) return null;
            if(!t.type.equals(f.type))
            {
                r.report
                (
                    line,
                    "Expressions in ternary operator have different types: "+
                    t.type+" and "+f.type
                );
                return null;
            }
            return new Expr(line,t.type)
            {
                @Override
                public Literal eval(final Scope<Value> s)
                {
                    return trace.wrapTrace
                    (line,"conditional::eval",() ->
                        condition.evaluate(s).conditional()
                            ? t.evaluate(s)
                            : f.evaluate(s)
                    );
                }
            };
        }
        return condition;
    }
    private static Expr lowPrecedence(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Get medium-precedence expression.
        final Expr e = mediumPrecedence(r,trace,tokens,scope);
        if(e == null) return null;
        // Parse ternary expression if necessary.
        return matches(ItrMode.peek,tokens,TokenType.CONDITION)
            ? ternary(r,trace,tokens,scope,e)
            : e;
    }
    private static Expr assignment(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                   final Expr lhs)
    {
        return switch(nonEOF(ItrMode.peek,tokens,r).type())
        {
            case ADDEQ,ANDEQ,ASSIGN,
                 DIVEQ,LRSHEQ,LSHEQ,
                 MODEQ,MULEQ,OREQ,
                 RSHEQ,SUBEQ,XOREQ ->
            {
                if(!(lhs instanceof Value)) r.report(lhs.line,"Expression yields a literal value");
                final int line = tokens.peek().line();
                final TokenType tt = tokens.next().type();
                final Expr rhs = parseExpr(r,trace,tokens,scope);
                if(rhs != null && lhs instanceof final Value lv)
                {
                    final Type ct = lhs.type;
                    final operator op = tt == TokenType.ASSIGN
                        ? switch(lhs.type.simple())
                          {
                              case BOOL   -> (x,y) -> y.conditional();
                              case INT    -> switch(rhs.type.simple())
                              {
                                  case BOOL  -> (x,y) -> y.conditional()?1L:0L;
                                  case INT   -> (x,y) -> y.value();
                                  case FLOAT -> (x,y) -> ((Double)y.value()).longValue();
                                  default    ->
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot assign type "+rhs.type.simple()+
                                          " to type "+ct.simple()
                                      );
                                      yield null;
                                  }
                              };
                              case FLOAT  -> switch(rhs.type.simple())
                              {
                                  case BOOL  -> (x,y) -> y.conditional()?1D:0D;
                                  case INT   -> (x,y) -> ((Long)y.value()).doubleValue();
                                  case FLOAT -> (x,y) -> y.value();
                                  default    ->
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot assign type "+rhs.type.simple()+
                                          " to type "+ct.simple()
                                      );
                                      yield null;
                                  }
                              };
                              case STR    -> (x,y) -> new LiteralStr(trace,line,toString(y));
                              case LIST,
                                   STRUCT ->
                              {
                                  if(!ct.equals(rhs.type))
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot assign type "+rhs.type+
                                          " to type "+ct
                                      );
                                      yield null;
                                  }
                                  yield lhs.type.simple() == BaseType.LIST
                                            ? (x,y) -> new LiteralList(trace,line,ct,listData(y))
                                            : (x,y) -> y.value();
                              }
                              case FUNC   ->
                              {
                                  if(!lhs.type.equals(rhs.type))
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot assign type "+lhs.type+
                                          " to type "+rhs.type
                                      );
                                      yield null;
                                  }
                                  yield (x,y) -> y;
                              }
                              default     ->
                              {
                                  r.report
                                  (
                                      line,
                                      "Cannot assign type "+rhs.type+
                                      " to type "+ct
                                  );
                                  yield null;
                              }
                          }
                        : switch(lhs.type.simple())
                          {
                              case INT -> switch(rhs.type.simple())
                              {
                                  case BOOL  -> switch(tt)
                                  {
                                      case  ADDEQ -> (x,y) -> (Long)x.value() + ((Boolean)y.value()?1L:0L);
                                      case  ANDEQ -> (x,y) -> (Long)x.value() & ((Boolean)y.value()?1L:0L);
                                      case  DIVEQ -> (x,y) ->
                                      {
                                          if(!(Boolean)y.value()) trace.report(line,"Divide by zero");
                                          return x.value();
                                      };
                                      case LRSHEQ -> (x,y) -> (Long)x.value()>>>((Boolean)y.value()?1L:0L);
                                      case  LSHEQ -> (x,y) -> (Long)x.value() <<((Boolean)y.value()?1L:0L);
                                      case  MODEQ -> (x,y) -> 0L;
                                      case  MULEQ -> (x,y) -> (Boolean)y.value()?x.value():0L;
                                      case   OREQ -> (x,y) -> (Long)x.value() | ((Boolean)y.value()?1L:0L);
                                      case  RSHEQ -> (x,y) -> (Long)x.value() >>((Boolean)y.value()?1L:0L);
                                      case  SUBEQ -> (x,y) -> (Long)x.value() - ((Boolean)y.value()?1L:0L);
                                      case  XOREQ -> (x,y) -> (Long)x.value() ^ ((Boolean)y.value()?1L:0L);
                                      default     -> throw new AssertionError();
                                  };
                                  case INT   -> switch(tt)
                                  {
                                      case  ADDEQ -> (x,y) -> (Long)x.value() + (Long)y.value();
                                      case  ANDEQ -> (x,y) -> (Long)x.value() & (Long)y.value();
                                      case  DIVEQ -> (x,y) ->
                                      {
                                          try {return (Long)x.value()/(Long)y.value();}
                                          catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                          return null;
                                      };
                                      case LRSHEQ -> (x,y) -> (Long)x.value()>>>(Long)y.value();
                                      case  LSHEQ -> (x,y) -> (Long)x.value() <<(Long)y.value();
                                      case  MODEQ -> (x,y) -> (Long)x.value() % (Long)y.value();
                                      case  MULEQ -> (x,y) -> (Long)x.value() * (Long)y.value();
                                      case   OREQ -> (x,y) -> (Long)x.value() | (Long)y.value();
                                      case  RSHEQ -> (x,y) -> (Long)x.value() >>(Long)y.value();
                                      case  SUBEQ -> (x,y) -> (Long)x.value() - (Long)y.value();
                                      case  XOREQ -> (x,y) -> (Long)x.value() ^ (Long)y.value();
                                      default     -> throw new AssertionError();
                                  };
                                  case FLOAT -> switch(tt)
                                  {
                                      case  ADDEQ -> (x,y) -> (Long)x.value() + ((Double)y.value()).longValue();
                                      case  ANDEQ -> (x,y) -> (Long)x.value() & ((Double)y.value()).longValue();
                                      case  DIVEQ -> (x,y) ->
                                      {
                                          try {return (long)(((Long)x.value()).doubleValue()/(Double)y.value());}
                                          catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                          return null;
                                      };
                                      case LRSHEQ -> (x,y) -> (Long)x.value()>>>((Double)y.value()).longValue();
                                      case  LSHEQ -> (x,y) -> (Long)x.value() <<((Double)y.value()).longValue();
                                      case  MODEQ -> (x,y) -> (Long)x.value() % ((Double)y.value()).longValue();
                                      case  MULEQ -> (x,y) -> (Long)x.value() * ((Double)y.value()).longValue();
                                      case   OREQ -> (x,y) -> (Long)x.value() | ((Double)y.value()).longValue();
                                      case  RSHEQ -> (x,y) -> (Long)x.value() >>((Double)y.value()).longValue();
                                      case  SUBEQ -> (x,y) -> (Long)x.value() - ((Double)y.value()).longValue();
                                      case  XOREQ -> (x,y) -> (Long)x.value() ^ ((Double)y.value()).longValue();
                                      default     -> throw new AssertionError();
                                  };
                                  default    ->
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot use operator "+tt+
                                          " on type "+ BaseType.INT +
                                          " and type "+rhs.type
                                      );
                                      yield null;
                                  }
                              };
                              case FLOAT -> switch(rhs.type.simple())
                              {
                                  case BOOL  -> switch(tt)
                                  {
                                      case  ADDEQ -> (x,y) -> (Double)x.value() + ((Boolean)y.value()?1D:0D);
                                      case  ANDEQ -> (x,y) -> ltod(dtol((Double)x.value()) & ((Boolean)y.value()?1L:0L));
                                      case  DIVEQ -> (x,y) ->
                                      {
                                          if(!(Boolean)y.value()) trace.report(line,"Divide by zero");
                                          return x.value();
                                      };
                                      case  RSHEQ,
                                           LRSHEQ -> (x,y) -> (Double)x.value()/((Boolean)y.value()?2D:1D);
                                      case  MODEQ -> (x,y) -> (Double)x.value()%((Boolean)y.value()?1D:0D);
                                      case  MULEQ -> (x,y) -> (Boolean)y.value()?x.value():0D;
                                      case   OREQ -> (x,y) -> ltod(dtol((Double)x.value()) | ((Boolean)y.value()?1L:0L));
                                      case  LSHEQ -> (x,y) -> (Double)x.value()*(double)(1L<<((Boolean)y.value()?1L:0L));
                                      case  SUBEQ -> (x,y) -> (Double)x.value() - ((Boolean)y.value()?1D:0D);
                                      case  XOREQ -> (x,y) -> ltod(dtol((Double)x.value()) ^ ((Boolean)y.value()?1L:0L));
                                      default     -> throw new AssertionError();
                                  };
                                  case INT   -> switch(tt)
                                  {
                                      case  ADDEQ -> (x,y) -> (Double)x.value() + ((Long)y.value()).doubleValue();
                                      case  ANDEQ -> (x,y) -> ltod(dtol((Double)x.value()) & (Long)y.value());
                                      case  DIVEQ -> (x,y) ->
                                      {
                                          try {return (Double)x.value()/((Long)y.value()).doubleValue();}
                                          catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                          return null;
                                      };
                                      case  RSHEQ,
                                           LRSHEQ -> (x,y) -> (Double)x.value()/(double)(1L<<(Long)y.value());
                                      case  LSHEQ -> (x,y) -> (Double)x.value()*(double)(1L<<(Long)y.value());
                                      case  MODEQ -> (x,y) -> (Double)x.value() % ((Long)y.value()).doubleValue();
                                      case  MULEQ -> (x,y) -> (Double)x.value() * ((Long)y.value()).doubleValue();
                                      case   OREQ -> (x,y) -> ltod(dtol((Double)x.value()) | (Long)y.value());
                                      case  SUBEQ -> (x,y) -> (Double)x.value() - ((Long)y.value()).doubleValue();
                                      case  XOREQ -> (x,y) -> ltod(dtol((Double)x.value()) ^ (Long)y.value());
                                      default     -> throw new AssertionError();
                                  };
                                  case FLOAT -> switch(tt)
                                  {
                                      case  ADDEQ -> (x,y) -> (Double)x.value() + (Double)y.value();
                                      case  ANDEQ -> (x,y) -> ltod(dtol((Double)x.value()) & dtol((Double)y.value()));
                                      case  DIVEQ -> (x,y) ->
                                      {
                                          try {return (Double)x.value()/(Double)y.value();}
                                          catch(final ArithmeticException e) {trace.report(line,"Divide by zero");}
                                          return null;
                                      };
                                      case  RSHEQ,
                                           LRSHEQ -> (x,y) -> (Double)x.value()/Math.pow(2D,(Double)y.value());
                                      case  LSHEQ -> (x,y) -> (Double)x.value()*Math.pow(2D,(Double)y.value());
                                      case  MODEQ -> (x,y) -> (Double)x.value() % (Double)y.value();
                                      case  MULEQ -> (x,y) -> (Double)x.value() * (Double)y.value();
                                      case   OREQ -> (x,y) -> ltod(dtol((Double)x.value()) | dtol((Double)y.value()));
                                      case  SUBEQ -> (x,y) -> (Double)x.value() - (Double)y.value();
                                      case  XOREQ -> (x,y) -> ltod(dtol((Double)x.value()) ^ dtol((Double)y.value()));
                                      default     -> throw new AssertionError();
                                  };
                                  default    ->
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot use operator "+tt+
                                          " on type "+ BaseType.INT +
                                          " and type "+rhs.type
                                      );
                                      yield null;
                                  }
                              };
                              case STR ->
                              {
                                  if(tt != TokenType.ADDEQ)
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot use operator "+tt+
                                          " on type "+ BaseType.STR +
                                          " and type "+rhs.type
                                      );
                                      yield null;
                                  }
                                  yield (x,y) -> new LiteralStr(trace,line,toString(x)+toString(y));
                              }
                              case LIST ->
                              {
                                  if(!lhs.type.equals(rhs.type) || tt != TokenType.ADDEQ)
                                  {
                                      r.report
                                      (
                                          line,
                                          "Cannot use operator "+tt+
                                          " on type "+lhs.type+
                                          " and type "+rhs.type
                                      );
                                      yield null;
                                  }
                                  yield (x,y) ->
                                  {
                                      final List<Literal> v = new ArrayList<>(listData(x));
                                      v.addAll(listData(y));
                                      return new LiteralList(trace,line,ct,v);
                                  };
                              }
                              default ->
                              {
                                  r.report
                                  (
                                      line,
                                      "Cannot use operator "+tt+
                                      " on type "+lhs.type+
                                      " and type "+rhs.type
                                  );
                                  yield null;
                              }
                          };
                    if(op == null) yield null;
                    yield tt == TokenType.ASSIGN
                        ? new Expr(line,ct)
                          {
                              @Override
                              public Literal eval(final Scope<Value> s)
                              {
                                  return trace.wrapTrace
                                  (line,"operator_"+tt+"::eval",() -> {
                                      final Object v = op.get(null,rhs.evaluate(s));
                                      lv.setValue(trace,s,v);
                                      return literal(s2 -> v,s);
                                  });
                              }
                          }
                        : new Expr(line,ct)
                          {
                              @Override
                              public Literal eval(final Scope<Value> s)
                              {
                                  return trace.wrapTrace
                                  (line,"operator_"+tt+"::eval",() -> {
                                      final Object v = op.get(lhs.evaluate(s),rhs.evaluate(s));
                                      lv.setValue(trace,s,v);
                                      return literal(s2 -> v,s);
                                  });
                              }
                          };
                }
                yield null;
            }
            default -> lhs;
        };
    }
    private static Expr parseExpr(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Get low-precedence expression.
        final Expr e = lowPrecedence(r,trace,tokens,scope);
        if(e == null) return null;
        // Parse assignment expression if necessary
        return matches(ItrMode.peek,tokens,
                       TokenType.ASSIGN,TokenType.ADDEQ,TokenType.ANDEQ,
                       TokenType.DIVEQ,TokenType.LRSHEQ,TokenType.LSHEQ,
                       TokenType.MODEQ,TokenType.MULEQ,TokenType.OREQ,
                       TokenType.RSHEQ,TokenType.SUBEQ,TokenType.XOREQ)
            ? assignment(r,trace,tokens,scope,e)
            : e;
    }
    
    /* ========== Statements ========== */
    private static class Continue extends Literal
    {
        final Trace t;
        Continue(final Trace trace,final int line) {super(line,Primitives.VOID.type); t = trace;}
        @Override
        public Object value()
        {
            t.trace(line,"continue::value");
            return null;
        }
        @Override public boolean equals(final Object obj) {return obj instanceof Continue;}
    }
    private static class Break extends Literal
    {
        final Trace t;
        Break(final Trace trace,final int line) {super(line,Primitives.VOID.type); t = trace;}
        @Override
        public Object value()
        {
            t.trace(line,"break::value");
            return null;
        }
        @Override public boolean equals(final Object obj) {return obj instanceof Break;}
    }
    private static class RET_VOID extends Literal
    {
        final Trace t;
        public RET_VOID(final Trace trace,final int line) {super(line,Primitives.VOID.type); t = trace;}
        @Override
        public Object value()
        {
            t.trace(line,"RET_VOID::value");
            return null;
        }
        @Override public boolean equals(final Object obj) {return obj instanceof RET_VOID;}
    }
    private static class DUMMY extends Stmt
    {
        final Trace t;
        public final boolean skip;
        public DUMMY(final Trace trace,final int line,final boolean skip) {super(line); this.skip = skip; t = trace;}
        @Override
        public Literal eval(final Scope<Value> s)
        {
            t.trace(line,"DUMMY::eval");
            return null;
        }
        @Override public boolean equals(final Object obj) {return obj instanceof DUMMY;}
    }
    private static Stmt parseBlock(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                   final Type ret)
    {
        // Assume '\{' already eaten.
        final int l = tokens.peek(-1).line();
        if(matches(ItrMode.peek,tokens,TokenType.RBRACE))
        {
            tokens.next();
            return new DUMMY(trace,l,true);
        }
        // Each block gets its own scope.
        scope.pushScope();
        final Stmt[] st = parseStmts(r,trace,tokens,scope,ret,true);
        scope.popScope();
        return st != null
            ? st.length != 0
                ? new Stmt(l)
                  {
                      @Override
                      public Literal eval(final Scope<Value> s)
                      {
                          return trace.wrapTrace
                          (line,"block::eval",() -> {
                              s.pushScope();
                              for(final Stmt v : st)
                              {
                                  final Literal l = v.eval(s);
                                  if(l != null) {s.popScope(); return l;}
                              }
                              s.popScope();
                              return null;
                          });
                      }
                  }
                : new DUMMY(trace,l,true)
            : null;
    }
    private static Stmt parseIf(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                final Type ret)
    {
        // Assume 'if' already eaten.
        final int l = tokens.peek(-1).line();
        // if( Condition ) ThenStmt [else ElseStmt]
        
        if(mismatch(ItrMode.next,tokens,r,TokenType.LPAREN)) return null;
        final Expr c = parseExpr(r,trace,tokens,scope); // Condition
        if(mismatch(ItrMode.next,tokens,r,TokenType.RPAREN))
        {
            skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
            return null;
        }
        
        final Stmt t = parseStmt(r,trace,tokens,scope,ret,false), // ThenStmt
                   f;
        boolean flag = c != null && t != null;
        if(tokens.peek().type() == TokenType.ELSE)
        {
            tokens.next(); // Eat 'else'.
            f = parseStmt(r,trace,tokens,scope,ret,false); // ElseStmt
            flag = flag && f != null;
        }
        else f = null;
        return flag
            ? f != null
                  ? new Stmt(l)
                    {
                        @Override
                        public Literal eval(final Scope<Value> s)
                        {
                            return trace.wrapTrace
                            (line,"if_else::eval",() ->
                                c.evaluate(s).conditional()? t.eval(s) : f.eval(s)
                            );
                        }
                    }
                  : new Stmt(l)
                    {
                        @Override
                        public Literal eval(final Scope<Value> s)
                        {
                            return trace.wrapTrace
                            (line,"if::eval",() ->
                                c.evaluate(s).conditional()? t.eval(s) : null
                            );
                        }
                    }
            : null;
    }
    private static Stmt parseFor(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                 final Type ret)
    {
        // Assume 'for' already eaten.
        final int line = tokens.peek(-1).line();
        if(mismatch(ItrMode.next,tokens,r,TokenType.LPAREN)) return null;
        final int p = tokens.pos();
        //noinspection StatementWithEmptyBody
        while(!matches(ItrMode.next,tokens,TokenType.SEMICOLON,TokenType.COLON));
        final boolean isForEach = matches(ItrMode.prev,tokens,TokenType.COLON);
        tokens.pos(p);
        if(isForEach)
        {
            final Token e = eat(ItrMode.next,tokens,r,TokenType.ID); // Guaranteed not EOF by isForEach.
            if(e.type() != TokenType.ID)
            {
                skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                return null;
            }
            tokens.next(); // Eat ':', guaranteed by isForEach.
            final Expr l = parseExpr(r,trace,tokens,scope);
            if(l == null)
            {
                skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                return null;
            }
            if(l.type.simple() != BaseType.LIST)
            {
                r.report
                (
                    l.line,
                    "For-each construct cannot be used on type "+l.type
                );
                return null;
            }
            scope.pushScope();
            scope.putField(e.value(),l.type.complex());
            if(mismatch(ItrMode.next,tokens,r,TokenType.RPAREN))
            {
                scope.popScope();
                skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
                return null;
            }
            final Stmt body = parseStmt(r,trace,tokens,scope,ret,false);
            scope.popScope();
            return body == null
                ? null
                : new Stmt(line)
                  {
                      @Override
                      public Literal eval(final Scope<Value> s)
                      {
                          return trace.wrapTrace
                          (line,"for_each::eval",() -> {
                              final List<Literal> ll = listData(l.evaluate(s));
                              s.pushScope();
                              s.putField
                              (
                                  e.value(),
                                  new Value(e.line(),l.type.complex())
                                  {
                                      Object v = null;
                                      @Override
                                      protected void setValue(final Scope<Value> s,final Object v)
                                      {
                                          trace.trace(line,"for_each_var::setValue");
                                          this.v = v;
                                      }
                                      @Override
                                      public Literal eval(final Scope<Value> s)
                                      {
                                          return trace.wrapTrace(line,"for_each_var::eval",() -> literal(s2 -> v,s));
                                      }
                                  }
                              );
                              for(final Literal lll : ll)
                              {
                                  s.getField(e.value()).setValue(trace,s,lll.value());
                                  final Literal o = body.eval(s);
                                  if(o != null) {s.popScope(); return o;}
                              }
                              s.popScope();
                              return null;
                          });
                      }
                  };
        }
        // for([Init] ; [Condition] ; [Update]) Statement
        scope.pushScope();
        boolean flag = true;
        
        final Stmt i; // Init
        if(matches(ItrMode.peek,tokens,TokenType.SEMICOLON))
        {
            i = null;
            tokens.next(); // Eat ';'.
        }
        else flag = (i = parseDeclOrExprStmt(r,trace,tokens,scope)) != null;
        
        final Expr c; // Condition
        if(matches(ItrMode.peek,tokens,TokenType.SEMICOLON))
        {
            c = null;
            tokens.next(); // Eat ';'.
        }
        else flag = (c = parseExpr(r,trace,tokens,scope)) != null &&
                    !mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON) &&
                    flag; // No short circuit so that the tokens get eaten.
        
        final Expr u; // Update
        if(matches(ItrMode.peek,tokens,TokenType.RPAREN))
        {
            u = null;
            tokens.next(); // Eat ')'.
        }
        else flag = (u = parseExpr(r,trace,tokens,scope)) != null &&
                    !mismatch(ItrMode.next,tokens,r,TokenType.RPAREN) &&
                    flag; // No short circuit so that the tokens get eaten.
        
        final Stmt b = parseStmt(r,trace,tokens,scope,ret,false); // Statement
        if(b == null) return null;
        
        if(flag)
            return new Stmt(line)
            {
                @Override
                public Literal eval(final Scope<Value> s)
                {
                    return trace.wrapTrace
                    (line,"for::eval",() -> {
                        if(i != null) i.eval(s);
                        while(c == null || c.evaluate(s).conditional())
                        {
                            final Literal l = b.eval(s);
                            if(l != null && !(l instanceof Continue))
                                return l instanceof Break? null : l;
                            if(u != null)
                                u.evaluate(s); // TODO update does not run?
                        }
                        return null;
                    });
                }
            };
        skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
        scope.popScope();
        return null;
    }
    private static Stmt parseWhile(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                   final Type ret)
    {
        // Assume 'while' already eaten.
        final int line = tokens.peek(-1).line();
        if(mismatch(ItrMode.next,tokens,r,TokenType.LPAREN)) return null;
        // while( Condition ) Statement
        
        final Expr c = parseExpr(r,trace,tokens,scope); // Condition
        if(c == null || mismatch(ItrMode.next,tokens,r,TokenType.RPAREN))
        {
            skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
            return null;
        }
        
        final Stmt b = parseStmt(r,trace,tokens,scope,ret,false); // Statement
        if(b == null) return null;
        
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace
                (line,"while::eval",() -> {
                    while(c.evaluate(s).conditional())
                    {
                        final Literal l = b.eval(s);
                        if(l != null && !(l instanceof Continue))
                            return l instanceof Break? null : l;
                    }
                    return null;
                });
            }
        };
    }
    private static Stmt parseDo(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                final Type ret)
    {
        // Assume 'do' already eaten.
        final int line = tokens.peek(-1).line();
        // do Statement while( Condition );
        
        final Stmt b = parseStmt(r,trace,tokens,scope,ret,false); // Statement
        if(b == null) return null;
        
        if(mismatch(ItrMode.next,tokens,r,TokenType.WHILE) ||
           mismatch(ItrMode.next,tokens,r,TokenType.LPAREN)) return null;
        
        final Expr c = parseExpr(r,trace,tokens,scope); // Condition
        if(c == null || mismatch(ItrMode.next,tokens,r,TokenType.RPAREN))
        {
            skip(tokens,TokenType.RPAREN,TokenType.LPAREN);
            return null;
        }
        if(mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON)) return null;
        
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace
                (line,"do::eval",() -> {
                    do
                    {
                        final Literal l = b.eval(s);
                        if(l != null && !(l instanceof Continue))
                            return l instanceof Break? null : l;
                    }
                    while(c.evaluate(s).conditional());
                    return null;
                });
            }
        };
    }
    private static Stmt parseRet(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                 final Type ret)
    {
        // Assume 'return' already eaten.
        final int line = tokens.peek(-1).line();
        // return [Expr] ;
        
        // Expr
        final Expr e = matches(ItrMode.peek,tokens,TokenType.SEMICOLON)
            ? new RET_VOID(trace,tokens.peek().line())
            : parseExpr(r,trace,tokens,scope);
        if(e == null) return null;
        if(!e.type.equals(ret)) // Check return type.
            r.report
            (
                e.line,
                "Expected return type "+ret+
                ", got type "+e.type
            );
        
        tokens.next(); // Eat ';'.
        
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace(line,"return::eval",() -> e.evaluate(s));
            }
        };
    }
    private static Stmt parseThrow(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Assume 'throw' already eaten.
        final int line = tokens.peek(-1).line();
        // throw [Expr] ;
        
        if(matches(ItrMode.peek,tokens,TokenType.SEMICOLON))
            return new Stmt(line)
            {
                @Override
                public Literal eval(final Scope<Value> s)
                {
                    trace.trace(line,"throw<void>::eval");
                    trace.report(line,"");
                    return null;
                }
            };
        // Expr
        final Expr e = parseExpr(r,trace,tokens,scope);
        if(e == null || mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON))
        {
            skip(tokens,TokenType.SEMICOLON,null);
            return null;
        }
        
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                trace.report(line,trace.wrapTrace(line,"throw::eval",() -> Script.toString(e.evaluate(s))));
                return null;
            }
        };
    }
    private static Stmt parseBreak(final ErrReporter r,final Trace trace,final TokenIterator tokens)
    {
        // Assume 'break' already eaten.
        final int line = tokens.peek(-1).line();
        // break ;
        
        if(mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON))
        {
            skip(tokens,TokenType.SEMICOLON,null);
            return null;
        }
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace(line,"break::eval",() -> new Break(trace,line));
            }
        };
    }
    private static Stmt parseContinue(final ErrReporter r,final Trace trace,final TokenIterator tokens)
    {
        // Assume 'continue' already eaten.
        final int line = tokens.peek(-1).line();
        // continue ;
        
        if(mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON))
        {
            skip(tokens,TokenType.SEMICOLON,null);
            return null;
        }
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace(line,"continue::eval",() -> new Continue(trace,line));
            }
        };
    }
    private static Stmt parseExprStmt(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        final Expr e = parseExpr(r,trace,tokens,scope);
        return e == null || mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON)
            ? null
            : new Stmt(e.line)
              {
                  @Override
                  public Literal eval(final Scope<Value> s)
                  {
                      return trace.wrapTrace
                      (line,"exprStmt::eval",() -> {
                          e.evaluate(s);
                          return null;
                      });
                  }
              };
    }
    private static Stmt parseDeclStmt(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // [const] Type Name [= Expr] {, Name [= Expr]} ;
        final int line = nonEOF(ItrMode.peek,tokens,r).line();
        
        // Type
        final Type ct = type(chkConst(tokens),r,trace,tokens,scope);
        if(ct == null) return null;
        
        final Token[] names;
        final Expr[] init;
        {
            record pair(Token n,Expr i) {}
            final ArrayList<pair> pairs = new ArrayList<>();
            do
            {
                // Name
                final Token t = eat(ItrMode.next,tokens,r,TokenType.ID);
                if(t.type() != TokenType.ID)
                {
                    skip(tokens,TokenType.SEMICOLON,null);
                    return null;
                }
                if(scope.getStruct(t.value()) != null)
                {
                    r.report
                    (
                        t.line(),
                        "Cannot use name '"+t.value()+
                        "'; a struct with that name exists"
                    );
                    skip(tokens,TokenType.SEMICOLON,null);
                    return null;
                }
                
                final Expr e; // Expr
                if(nonEOF(ItrMode.peek,tokens,r).type() == TokenType.ASSIGN)
                {
                    tokens.next(); // Eat '='.
                    e = parseExpr(r,trace,tokens,scope);
                }
                else e = null;
                if(mismatch(ItrMode.peek,tokens,r,TokenType.COMMA,TokenType.SEMICOLON))
                {
                    skip(tokens,TokenType.SEMICOLON,null);
                    return null;
                }
                
                pairs.add(new pair(t,e));
            }
            while(!matches(ItrMode.next,tokens,TokenType.SEMICOLON));
            names = new Token[pairs.size()];
            init = new Expr[pairs.size()];
            int i = 0;
            for(final pair p : pairs)
            {
                scope.putField((names[i] = p.n).value(),ct);
                init[i++] = p.i;
            }
        }
        
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace
                (line,"declaration::eval",() -> {
                    for(int i = 0;i < names.length;++i)
                    {
                        final Value v = new Value(names[i].line(),ct)
                        {
                            Object v = null;
                            @Override
                            protected void setValue(final Scope<Value> s2,final Object v)
                            {
                                trace.trace(line,"declaration_val::setValue");
                                this.v = v;
                            }
                            @Override
                            public Literal eval(final Scope<Value> s2)
                            {

                                return trace.wrapTrace(line,"declaration_val::eval",() -> literal(s3 -> v,s2));
                            }
                        };
                        if(init[i] != null) v.setValue(trace,s,init[i].evaluate(s).value());
                        s.putField(names[i].value(),v);
                    }
                    return null;
                });
            }
        };
    }
    private static Stmt parseDeclOrExprStmt(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        return switch(tokens.peek().type())
        {
            case BOOL,INT,FLOAT,STR,FUNC,CONST -> parseDeclStmt(r,trace,tokens,scope);
            case ID -> scope.getStruct(tokens.peek().value()) != null
                           ? parseDeclStmt(r,trace,tokens,scope)
                           : parseExprStmt(r,trace,tokens,scope);
            default -> parseExprStmt(r,trace,tokens,scope);
        };
    }
    private static Stmt parseStructDecl(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Assume 'struct' already eaten.
        final Token id = eat(ItrMode.next,tokens,r,TokenType.ID);
        if(id.type() != TokenType.ID) return null;
        // struct Name \{ [Type Name {, Type Name }] \}
        
        if(mismatch(ItrMode.next,tokens,r,TokenType.LBRACE)) return null;
        final Map<String,Type> fields = new HashMap<>();
        if(matches(ItrMode.peek,tokens,TokenType.RBRACE)) tokens.next(); // Eat '\}'.
        else
        {
            do
            {
                final Type ct = type(chkConst(tokens),r,trace,tokens,scope);
                final Token n = eat(ItrMode.next,tokens,r,TokenType.ID);
                if(n.type() != TokenType.ID)
                {
                    skip(tokens,TokenType.RBRACE,TokenType.LBRACE);
                    return null;
                }
                fields.put(n.value(),ct);
            }
            while(matches(ItrMode.next,tokens,TokenType.COMMA));
            // Ensure that the loop condition broke on a closing brace.
            if(mismatch(ItrMode.prev,tokens,r,TokenType.RBRACE))
            {
                skip(tokens,TokenType.RBRACE,TokenType.LBRACE);
                return null;
            }
        }
        final String name = id.value();
        scope.putStruct(name,fields);
        return new Stmt(id.line())
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace
                (line,"structDecl::eval",() -> {
                    s.putStruct(name,fields);
                    return null;
                });
            }
        };
    }
    private static Module dummy()
    {
        return new Module
        (
            new ScopeEntry<>
            (
                new HashMap<>(),
                new HashMap<>()
            ),
            new ScopeEntry<>
            (
                new HashMap<>(),
                new HashMap<>()
            )
        );
    }
    /**
     * Base directory for imported scripts. Access is synchronized in case the API user decides to change the directory
     * on a different thread for whatever reason.
     */
    private static Path IMPORTS_DIR = Path.of(System.getProperty("user.dir"));
    private static final Object pathLock = new Object();
    public static void setImportsDir(Path path)
    {
        if(path == null) path = Path.of(System.getProperty("user.dir"));
        synchronized(pathLock) {IMPORTS_DIR = path;}
    }
    private static Stmt parseImport(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope)
    {
        // Assume 'import' already eaten.
        // import " Module " ;
        final Token module = eat(ItrMode.next,tokens,r,TokenType.LIT_STR);
        if(module.type() != TokenType.LIT_STR || mismatch(ItrMode.next,tokens,r,TokenType.SEMICOLON)) return null;
        final int line = module.line();
        
        // Skip already loaded modules.
        final String m = module.value();
        if(trace.imports.containsKey(m)) return new DUMMY(trace,line,true);
        
        // Ensure module exists.
        if(!Module.REGISTRY.containsKey(m))
        {
            if(m.endsWith(".prgm"))
            {
                final Path p;
                synchronized(pathLock) {p = IMPORTS_DIR.resolve(Path.of(m));}
                // Try to read another script from file.
                try(final FileReader fr = new FileReader(p.toFile()))
                {
                    final Trace t2 = new Trace(m,trace.out,trace.err);
                    t2.enableLogs = trace.enableLogs;
                    final Module mm = run(fr,t2,m);
                    if(mm != null && mm.register(m))
                    {
                        scope.pushToScope(mm.compileTime());
                        return new Stmt(line)
                        {
                            @Override
                            public Literal eval(final Scope<Value> s)
                            {
                                return trace.wrapTrace
                                (line,"import::eval",() -> {
                                    s.pushToScope(mm.runTime());
                                    return null;
                                });
                            }
                        };
                    }
                }
                catch(final IOException e)
                {
                    r.report
                    (
                        line,
                        "Could not read module '"+m+
                        "': "+e.getMessage()
                    );
                }
            }
            else r.report(line,"Unknown module '"+m+'\'');
            // If there were errors, just create a dummy
            // module so that more errors in the script
            // are caught.
            dummy().register(m);
            return new DUMMY(trace,line,true);
        }
        final Module mm = Module.REGISTRY.get(m);
        scope.pushToScope(mm.compileTime());
        return new Stmt(line)
        {
            @Override
            public Literal eval(final Scope<Value> s)
            {
                return trace.wrapTrace
                (line,"import::eval",() -> {
                    s.pushToScope(mm.runTime());
                    return null;
                });
            }
        };
    }
    private static Stmt parseStmt(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                  final Type ret,final boolean block)
    {
        return switch(tokens.next().type())
        {
            case EOF,RBRACE -> (tokens.peek(-1).type() == TokenType.RBRACE) == block
                                   ? new DUMMY(trace,tokens.peek(-1).line(),false)
                                   : null;
            case STRUCT     -> parseStructDecl(r,trace,tokens,scope);
            case LBRACE     ->
            {
                if(tokens.canAdvance() && tokens.peek(1).type() == TokenType.COLON)
                {
                    final Expr struct = struct(r,trace,tokens,scope);
                    yield struct != null
                        ? new Stmt(tokens.peek(-1).line())
                          {
                              @Override
                              public Literal eval(final Scope<Value> s)
                              {
                                  return trace.wrapTrace
                                  (line,"literalStruct::eval",() -> {
                                      struct.evaluate(s);
                                      return null;
                                  });
                              }
                          }
                        : null;
                }
                yield parseBlock(r,trace,tokens,scope,ret);
            }
            case IF         -> parseIf(r,trace,tokens,scope,ret);
            case FOR        -> parseFor(r,trace,tokens,scope,ret);
            case DO         -> parseDo(r,trace,tokens,scope,ret);
            case WHILE      -> parseWhile(r,trace,tokens,scope,ret);
            case RETURN     -> parseRet(r,trace,tokens,scope,ret);
            case THROW      -> parseThrow(r,trace,tokens,scope);
            case BREAK      -> parseBreak(r,trace,tokens);
            case CONTINUE   -> parseContinue(r,trace,tokens);
            case SEMICOLON  -> parseStmt(r,trace,tokens,scope,ret,block); // No-op.
            case IMPORT     -> parseImport(r,trace,tokens,scope);
            default         ->
            {
                tokens.previous(); // Puke previous token.
                yield parseDeclOrExprStmt(r,trace,tokens,scope);
            }
        };
    }
    private static Stmt[] parseStmts(final ErrReporter r,final Trace trace,final TokenIterator tokens,final Scope<Type> scope,
                                     final Type ret,final boolean block)
    {
        final ArrayList<Stmt> o = new ArrayList<>();
        while(true)
        {
            final Stmt s = parseStmt(r,trace,tokens,scope,ret,block);
            if(s == null) return null;
            if(s instanceof DUMMY d) {if(!d.skip) return o.toArray(Stmt[]::new);}
            else o.add(s);
        }
    }
    
    public static Module run(final Reader reader,final Trace trace,final String name) throws IOException
    {
        final Scope<Type> compileTime = new Scope<>();
        final Stmt[] prgm;
        {
            final ErrReporter r = new ErrReporter(name,trace.err);
            final TokenIterator tokens = Tokenize.tokenize(reader,r);
            if(tokens == null) return null;
            prgm = parseStmts
            (
                r,trace,tokens,
                compileTime,
                Primitives.VOID.type,
                false
            );
            if(r.reportAll()) return null;
        }
        final Scope<Value> runTime = new Scope<>();
        try
        {
            assert prgm != null; // Guaranteed by 'r.reportAll()'.
            for(final Stmt s : prgm)
            {
                final Literal l = s.eval(runTime);
                if(l != null && !(l instanceof Continue) && !(l instanceof Break)) break; //TODO escapes function
            }
            if(trace.enableLogs) trace.out.println();
        }
        catch(final Exception e) {return null;}
        return new Module(compileTime.popScope(),runTime.popScope());
    }
}