package experimental;

import experimental.token.Token;
import experimental.token.TokenType;
import experimental.token.Tokenize;
import experimental.token.Tokenize.TokenIterator;
import experimental.util.ContainerUtil;
import experimental.util.ErrReporter;
import experimental.util.Stack;

import java.awt.event.WindowStateListener;
import java.io.*;
import java.util.*;

public final class Script
{
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
    private static boolean matchThenEat(final TokenIterator tokens,final TokenType...types)
    {
        final boolean out = matches(ItrMode.peek,tokens,types);
        if(out) tokens.next();
        return out;
    }
    private static boolean exitCondition(final TokenIterator tokens,final ErrReporter reporter,
                                         final TokenType good,final TokenType exit)
    {
        return eat(ItrMode.next,tokens,reporter,good,exit).type() == good;
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
            case BITOR         -> (byte)0;
            case BITXOR        -> (byte)1;
            case BITAND        -> (byte)2;
            case EQ,NEQ        -> (byte)3;
            case LT,GT,LEQ,GEQ -> (byte)4;
            case LSH,RSH,LRSH  -> (byte)5;
            case ADD,SUB       -> (byte)6;
            case MUL,DIV,MOD   -> (byte)7;
            
            default            -> (byte)-1;
        };
    }
    /**
     * @return The precedence of the specified operator. Lower values
     *         represent lower precedence.
     */
    private static byte precedence(final InstructionType op)
    {
        return switch(op)
        {
            case OR            -> (byte)0;
            case XOR           -> (byte)1;
            case AND           -> (byte)2;
            case EQ,NEQ        -> (byte)3;
            case LT,GT,LEQ,GEQ -> (byte)4;
            case LSH,RSH,LRSH  -> (byte)5;
            case ADD,SUB       -> (byte)6;
            case MUL,DIV,MOD   -> (byte)7;
            
            default            -> (byte)-1;
        };
    }
    
    /* ========== ~ I'm Very Lazy ~ ========== */
    private static long dtol(final double o) {return Double.doubleToRawLongBits(o);}
    private static double ltod(final long o) {return Double.longBitsToDouble(o);}
    
    private enum Status {OK,BAD,END}
    /** An object which holds the compiler's state. */
    private static class Context
    {
        /**
         * The current file's {@linkplain TokenIterator}.
         * @see Tokenize
         */
        final TokenIterator tokens;
        /** This module's name. */
        final String module;
        /** The {@linkplain ErrReporter}. */
        final ErrReporter reporter;
        final Scope scope;
        BlockBuilder bb;
        final Map<String,Integer> bbNames = new HashMap<>();
        final List<BlockBuilder> bbList = new ArrayList<>();
        final List<Func> functions = new ArrayList<>();
        final String path;
        int subID = 0;
        
        /** Initializes the context by running {@linkplain Tokenize#tokenize(Reader,ErrReporter)}. */
        Context(final String module,final Reader reader,final PrintStream err) throws IOException
        {
            tokens = Tokenize.tokenize(reader,reporter = new ErrReporter(this.module = module,err));
            scope = new Scope();
            bbList.add(bb = new BlockBuilder(module,"entry"));
            path = "main";
        }
        @SuppressWarnings("CopyConstructorMissesField")
        Context(final Context other)
        {
            tokens = other.tokens;
            module = other.module;
            reporter = other.reporter;
            scope = new Scope(other.scope);
            bbList.add(bb = new BlockBuilder(other.module,"entry"));
            path = other.path+'.'+other.subID++;
        }
        
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
        
        void nextBB(final String name)
        {
            final int i = bbNames.getOrDefault(name,0);
            final BlockBuilder nbb = new BlockBuilder(module,i != 0? name + i : name);
            bbList.add(nbb);
            bbNames.put(name,i+1);
            bb = nbb;
        }
    }
    private static final record Func(Context ctx,TemplateType ret) {}
    
    //TODO at template spec:
    // - if complex expression, compile for all possible outcomes
    // - let the compiler prune unreachable functions during optimization passes
    
    //TODO need a way to identify which function(s) to compile at template spec
    // - This function adds the definition to a list of possible expression results in 'ctx'
    // - When the expression result is assigned, the assignee is associated with the list
    //   and must compile all of them when it reaches a template spec
    //    - List in 'ctx' is cleared
    //    - This includes variable assignment, list instantiation, list functions
    //      (push,insert,etc), and list-index assignment
    // - When the expression result is added to a list, all function elements in the list
    //   must be compiled together (regardless of dimension)
    // - If a list-index expression is the operand of an assignment expression, the assignee
    //   inherits its compilation list
    // - Struct field access is compile-time, which means it does not need the same protections
    //   as lists
    // - Assigning never removes any function from compilation lists
    private static TemplateType typeSubstitution(final int line,final ErrReporter reporter,TemplateType tt,
                                                 final Map<String,Type> templateSpec)
    {// TODO cache list result for performance increase
        int indirection = 0;
        while(tt.st != null)
        {
            ++indirection;
            tt = tt.st;
        }
        final Type base;
        if(tt.t == null)
        {
            if((base = templateSpec.get(tt.n)) == null)
                reporter.report(line,"Undeclared type '"+tt.n+'\'');
        }
        else base = tt.t;
        Type out = base;
        while(indirection != 0)
        {
            out = Type.ofList(TemplateType.of(out));
            --indirection;
        }
        return TemplateType.of(out);
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
            ast = ast.subType.t;
            bst = bst.subType.t;
            if(ast.base == BaseType.VOID) return true;
        }
        // Compare if void list not found.
        return ast.equals(bst);
    }
    /** @return {@code true} iff the two arrays represent the same function arguments. */
    private static boolean compareArgs(final ConstableTemplateType[] a,final Type[] b)
    {
        if(a != null && b != null && a.length == b.length)
        {
            for(int i = 0;i < a.length;++i)
                if(!compareOrVoidList(a[i].type.t,b[i]))
                    return false;
            return true;
        }
        return false;
    }
    /** @return {@code true} iff the two arrays represent the same function arguments. */
    private static boolean compareArgs(final ConstableTemplateType[] a,final ConstableTemplateType[] b)
    {
        if(a != null && b != null && a.length == b.length)
        {
            for(int i = 0;i < a.length;++i)
                if(!compareOrVoidList(a[i].type.t,b[i].type.t))
                    return false;
            return true;
        }
        return false;
    }
    /** @return {@code true} iff an object of type {@code from} can be converted to an object of type {@code to}. */
    private static boolean convertible(final Type to,final Type from)
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
                             to.subType.t.base == BaseType.VOID ||
                             convertible(to.subType.t,from.subType.t)
                         );
            default -> to.equals(from);
        };
    }
    private static void compile(final Context ctx,final TemplateType ret,final Map<String,Type> templateSpec)
    {
        // TODO do type substitution on all instructions
        // decl, conversions, template specs
        for(final BlockBuilder bb : ctx.bbList)
            for(int ii = 0;ii < bb.instructions.size();++ii)
            {
                final Instruction i = bb.instructions.get(ii);
                switch(i.type)
                {
                    case DECL ->
                    {// TODO cache list result for performance increase
                        final Decl data = (Decl)i.data;
                        final ConstableTemplateType ct = data.type;
                        data.type = ConstableTemplateType.of(data.type.isConst,typeSubstitution(i.line,ctx.reporter,ct.type,templateSpec));
                    }
                    case CONVERT -> i.data = typeSubstitution(i.line,ctx.reporter,(TemplateType)i.data,templateSpec);
                    case GENERATE_TEMPLATE ->
                    {
                        final TemplateType[] template = (TemplateType[])i.data;
                        for(int iii = 0;iii < template.length;++iii)
                            template[iii] = typeSubstitution(i.line,ctx.reporter,template[iii],templateSpec);
                        // TODO get functions associated with this instruction and compile
                    }
                    case TYPE_COMPARISON ->
                    {
                        // Compile-time type comparisons.
                        final TypeComparison tc = (TypeComparison)i.data;
                        final TemplateType a = typeSubstitution(i.line,ctx.reporter,tc.a(),templateSpec),
                                           b = typeSubstitution(i.line,ctx.reporter,tc.b(),templateSpec);
                        bb.instructions.set(ii,new Instruction(i.line,InstructionType.PUSH,tc.eq() == a.equals(b)));
                    }
                }
            }
        // TODO do type checking
        {
            final Stack<ConstableTemplateType> stk = new Stack<>(ConstableTemplateType[]::new);
            final class Symbol
            {
                final ConstableType type;
                boolean assigned = false;
                Symbol(final ConstableType type) {this.type = type;}
            }
            final Stack<Map<String,Symbol>> symbols = new Stack<Map<String,Symbol>>(HashMap[]::new);
            symbols.push(new HashMap<>());
            for(final BlockBuilder bb : ctx.bbList)
                for(final ListIterator<Instruction> ii = bb.instructions.listIterator();ii.hasNext();)
                {
                    final Instruction i = ii.next();
                    // TODO get types on stack at the time of this instruction
                    switch(i.type)
                    {
                        case ADD ->
                        {
                            final Type b = stk.pop().type.t,a = stk.pop().type.t;
                            // TODO verify
                            final Type tt = a.base == BaseType.STR || b.base == BaseType.STR
                                ? Type.STR
                                : switch(a.base)
                                  {
                                      case BOOL,INT -> switch(b.base)
                                      {
                                          case BOOL,INT -> Type.INT;
                                          case FLOAT -> Type.FLOAT;
                                          default -> null;
                                      };
                                      case FLOAT -> switch(b.base)
                                      {
                                          case BOOL,INT,FLOAT -> Type.FLOAT;
                                          default -> null;
                                      };
                                      case LIST -> a.equals(b)? a : null;
                                      default -> null;
                                  };
                            if(tt == null)
                            {
                                ctx.reporter.report(i.line,"Cannot add types " + a + " and " + b);
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(a)));
                            }
                            else
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(tt)));
                        }
                        case AND,DIV,LRSH,LSH,MOD,
                             MUL,OR,RSH,SUB,XOR ->
                        {
                            final Type b = stk.pop().type.t,a = stk.top().type.t;
                            final Type tt = switch(a.base)
                            {
                                case FLOAT -> switch(b.base)
                                {
                                    case BOOL,INT,FLOAT -> Type.FLOAT;
                                    default -> null;
                                };
                                case BOOL,INT -> switch(b.base)
                                {
                                    case BOOL,INT -> Type.INT;
                                    case FLOAT -> Type.FLOAT;
                                    default -> null;
                                };
                                default -> null;
                            };
                            if(tt == null)
                            {
                                ctx.reporter.report
                                (
                                    i.line,
                                    "Cannot " + switch(i.type)
                                    {
                                        case AND  -> "compute bitwise and of";
                                        case DIV  -> "divide";
                                        case LRSH -> "unsigned right shift";
                                        case LSH  -> "left shift";
                                        case MOD  -> "compute the remainder of";
                                        case MUL  -> "multiply";
                                        case OR   -> "compute bitwise or of";
                                        case RSH  -> "right shift";
                                        case SUB  -> "subtract";
                                        default   -> "compute bitwise exclusive or of";
                                    } +
                                    " types "+a+" and "+b
                                );
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(a)));
                            }
                            else
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(tt)));
                        }
                        case BOOL_TO_NUM ->
                        {
                            final Type a = stk.pop().type.t,tt = switch(a.base)
                            {
                                case BOOL,INT -> Type.INT;
                                case FLOAT -> Type.FLOAT;
                                default -> null;
                            };
                            if(tt == null)
                            {
                                ctx.reporter.report(i.line,"Expected boolean or numeric type, got "+a);
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(a)));
                            }
                            else
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(tt)));
                        }
                        case CALL ->
                        {
                            final int nArgs = (Integer)i.data;
                            final Type[] args = new Type[nArgs];
                            final int caller = stk.pos()-nArgs-2;
                            for(int k = 0,d = caller;k < nArgs;++k)
                                args[k] = stk.data()[++d].type.t;
                            final Type f = stk.data()[caller].type.t;
                            stk.pos(caller);
                            if(f.base != BaseType.FUNC)
                            {
                                ctx.reporter.report(i.line,"Cannot use a call expression on type "+f);
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(f)));
                            }
                            else if(!compareArgs(f.args,args))
                            {
                                final StringJoiner sj = new StringJoiner(",","(",")");
                                for(final Type t : args) sj.add(t.toString());
                                ctx.reporter.report(i.line,"Cannot call function of type "+f+" with arguments of type "+sj);
                            }
                            else if(f.subType.t.base != BaseType.VOID) stk.push(ConstableTemplateType.of(false,f.subType));
                        }
                        case CONVERT ->
                        {
                            final Type a = stk.pop().type.t,b = ((TemplateType)i.data).t;
                            if(!convertible(a,b))
                                ctx.reporter.report(i.line,"Cannot convert from type "+a+" to type "+b);
                            stk.push(ConstableTemplateType.of(false,TemplateType.of(b)));
                        }
                        case DECL ->
                        {
                            final Decl d = (Decl)i.data;
                            stk.push(d.type);
                            symbols.top().put(d.name,new Symbol(ConstableType.of(d.type.type.t,d.type.isConst)));
                        }
                        case EQ,NEQ ->
                        {
                            final Type b = stk.pop().type.t,
                                       a = stk.pop().type.t;
                            if
                            (
                                switch(a.base)
                                {
                                    case VOID -> true;
                                    case BOOL -> false;
                                    case FUNC -> b.base == BaseType.FUNC;
                                    case INT,FLOAT -> b.base == BaseType.BOOL ||
                                                      b.base == BaseType.INT ||
                                                      b.base == BaseType.FLOAT;
                                    default -> a.equals(b);
                                }
                            )
                                ctx.reporter.report(i.line,"Cannot compare types "+a+" and "+b);
                        }
                        case FIELD ->
                        {
                            final String id = (String)i.data;
                            final Type struct = stk.pop().type.t;
                            final Map<String,Scope.StructEntry> s = ctx.scope.getStruct(struct.structName);
                            assert s != null;
                            final Scope.StructEntry e = s.get(id);
                            if(e == null)
                            {
                                ctx.reporter.report(i.line,"Struct type " + struct + " does not have a member " + id);
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(struct)));
                            }
                            else stk.push(e.type());
                        }
                        case FOR_ITR_NEXT,FOR_ITR_TEST,GENERATE_TEMPLATE,
                             IMPORT -> {/* No type checking needed. */}
                        case FOR_ITR_START ->
                        {
                            final Decl d = (Decl)i.data;
                            final Type t = stk.pop().type.t;
                            if(t.subType == null)
                                ctx.reporter.report(i.line,"Cannot iterate over type "+t);
                            else if(!d.type.type.t.equals(t.subType.t))
                                ctx.reporter.report(i.line,"Iterating type "+d.type+" is not equal to list of type "+t.subType.t);
                            stk.push(ConstableTemplateType.of(d.type.isConst,d.type.type));
                        }
                        case GEQ,GT,LEQ,LT ->
                        {
                            final Type b = stk.pop().type.t,
                                       a = stk.pop().type.t;
                            if
                            (
                                switch(a.base)
                                {
                                    case BOOL,INT,FLOAT -> switch(b.base)
                                    {
                                        case BOOL,INT,FLOAT -> false;
                                        default -> true;
                                    };
                                    case STR -> b.base != BaseType.STR;
                                    default -> true;
                                }
                            )
                                ctx.reporter.report(i.line,"Cannot compare types "+a+" and "+b);
                            stk.push(ConstableTemplateType.of(false,TemplateType.of(Type.BOOL)));
                        }
                        case LISTIDX ->
                        {
                            final Type b = stk.pop().type.t,
                                       a = stk.pop().type.t;
                            if
                            (
                                switch(b.base)
                                {
                                    case BOOL,INT,FLOAT -> false;
                                    default -> true;
                                }
                            )
                                ctx.reporter.report(i.line,"Cannot index list using type "+b);
                            if(a.base != BaseType.LIST)
                            {
                                ctx.reporter.report(i.line,"Cannot index type " + a);
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(a)));
                            }
                            else stk.push(ConstableTemplateType.of(false,a.subType));
                        }
                        case LOAD ->
                        {
                            final int  ptr = (Integer)i.data,
                                      prev = ii.previousIndex(),
                                         k = prev-ptr;
                            // 'k' should not be negative; before a load there should
                            // be a push instruction which provides an ID in the same
                            // block.
                            assert k >= 0;
                            final Instruction push = bb.instructions.get(k);
                            // The compiler should only generate a load instruction
                            // that corresponds to a push instruction.
                            assert push.type == InstructionType.PUSH;
                            // The data contained in the push instruction should have
                            // information about the declared variable.
                            assert push.data instanceof Decl;
                            final Decl decl = (Decl)i.data;
                            Symbol s = null;
                            for(int l = symbols.pos();l-- != 0;)
                                if((s = symbols.data()[l].get(decl.name)) != null)
                                    break;
                            // 's' should never be null because name checking was
                            // already done.
                            assert s != null;
                            stk.push(ConstableTemplateType.of(s.type.isConst,TemplateType.of(s.type.type)));
                        }
                        case NEG,NOT ->
                        {
                            final Type a = stk.pop().type.t,
                                       t = switch(a.base)
                                       {
                                           case BOOL,INT -> Type.INT;
                                           case FLOAT    -> Type.FLOAT;
                                           default       -> null;
                                       };
                            if(t == null)
                            {
                                ctx.reporter.report(i.line,"Cannot "+(i.type == InstructionType.NEG? "negate":"compute bitwise not of")+" type "+a);
                                stk.push(ConstableTemplateType.of(false,TemplateType.of(a)));
                            }
                            else stk.push(ConstableTemplateType.of(false,TemplateType.of(t)));
                        }
                        case POP -> stk.pop();
                        case PUSH ->
                        {
                            // TODO verify
                            final Type t;
                            if(i.data instanceof Boolean) t = Type.BOOL;
                            else if(i.data instanceof Integer) t = Type.INT;
                            else if(i.data instanceof Double) t = Type.FLOAT;
                            else if(i.data instanceof String) t = Type.STR;
                            
                        }
                        case RET ->
                        {
                            // TODO verify
                        }
                        case RET_VOID -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case SP -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case STORE -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case STRCAT -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case STRLEN -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case SUBSTR -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case THROW -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case THROW_VOID -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                        case TYPE_COMPARISON -> //noinspection DuplicateBranchesInSwitch
                        {
                            // TODO verify
                        }
                    }
                }
        }
        while(true)
        {
            // TODO do constant propagation
            // TODO do branch folding
        }
        // TODO check cflow
        
        // If a compiled version of this language is made in the future,
        // the register allocator and assembly code generation should go
        // here.
    }
    
    /** @return {@code true} iff a const token was eaten. */
    private static boolean chkConst(final TokenIterator tokens) {return matchThenEat(tokens,TokenType.CONST);}
    private static TemplateType type(final Context ctx)
    {
        // Type := ('void'|'bool'|'int'|'float'|'str'|StructType|FuncType|TemplateName) {'[' ']'}
        final Token t = nonEOF(ItrMode.next,ctx.tokens,ctx.reporter);
        TemplateType out = switch(t.type())
        {
            case VOID  -> TemplateType.of(Type.VOID);
            case BOOL  -> TemplateType.of(Type.BOOL);
            case INT   -> TemplateType.of(Type.INT);
            case FLOAT -> TemplateType.of(Type.FLOAT);
            case STR   -> TemplateType.of(Type.STR);
            case ID    ->
            {
                // Check to see if ID is a declared struct.
                if(ctx.scope.getStruct(t.value()) != null)
                    yield TemplateType.of(Type.ofStruct(t.value()));
                // Check to see if ID is a declared template.
                if(ctx.scope.templateDefined(t.value()))
                    ctx.reporter.report(t.line(),"Type name '"+t.value()+"' was not declared.");
                yield TemplateType.of(t.value());
            }
            case FUNC  ->
            {
                // TemplateArg  := [TemplateName '='] TypeSet
                // TemplArgList := '{' TemplateArg {',' TemplateArg} '}'
                // ArgTList     := Type {',' Type}
                // FuncType     := 'func' '<' [TemplArgList] [Type] '>' '(' [ArgTList] ')'
                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LT);
                // Get template.
                final Type.TemplateEntry[] tmpl;
                if(matchThenEat(ctx.tokens,TokenType.LBRACE))
                {
                    // TemplArgList := '{' TemplateArg {',' TemplateArg} '}'
                    final List<Type.TemplateEntry> template = new ArrayList<>();
                    if(!matchThenEat(ctx.tokens,TokenType.RBRACE))
                        do
                        {
                            // TemplateArg  := [TemplateName '='] TypeSet
                            final int line;
                            final String name;
                            // Get name.
                            if(matches(ItrMode.next,ctx.tokens,TokenType.LBRACKET))
                            {
                                name = null;
                                line = ctx.tokens.peek(-1).line();
                            }
                            else
                            {
                                name = eat(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.ID).value();
                                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ASSIGN);
                                line = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LBRACKET).line();
                            }
                            // Get typeset.
                            final Set<TemplateType> typeset = new HashSet<>();
                            if(!matchThenEat(ctx.tokens,TokenType.RBRACKET))
                                do
                                {
                                    final TemplateType tt = type(ctx);
                                    if(tt != null) typeset.add(tt);
                                }
                                while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACKET));
                            if(typeset.isEmpty())
                                ctx.reporter.report(line,"Empty typeset");
                            else if(typeset.size() == 1)
                                ctx.reporter.warn(line,"Singleton typeset");
                            template.add(new Type.TemplateEntry(name,typeset.toArray(TemplateType[]::new)));
                        }
                        while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACE));
                    tmpl = template.toArray(Type.TemplateEntry[]::new);
                }
                else tmpl = null;
                // Get return type.
                final TemplateType ret;
                if(matches(ItrMode.peek,ctx.tokens,TokenType.GT))
                    ret = TemplateType.of(Type.VOID);
                else ret = type(ctx);
                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.GT); // Eat '>'.
                // Get args.
                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN);
                final List<ConstableTemplateType> argt = new ArrayList<>();
                if(!matchThenEat(ctx.tokens,TokenType.RPAREN))
                    do
                    {
                        final boolean isConst = chkConst(ctx.tokens);
                        final TemplateType tt = type(ctx);
                        if(tt != null) argt.add(ConstableTemplateType.of(isConst,tt));
                    }
                    while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RPAREN));
                final ConstableTemplateType[] argtt = argt.toArray(ConstableTemplateType[]::new);
                yield ret == null? null : TemplateType.of(Type.ofFunc(ret,tmpl,argtt));
            }
            default -> null;
        };
        while(matchThenEat(ctx.tokens,TokenType.LBRACKET))
        {
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RBRACKET);
            out = TemplateType.of(out);
        }
        return out;
    }
    private static Set<TemplateType> typeSetElement(final Context ctx,final boolean copy)
    {
        if(matchThenEat(ctx.tokens,TokenType.LBRACKET))
        {
            // Eat list.
            if(!matchThenEat(ctx.tokens,TokenType.RBRACKET))
            {
                final Set<TemplateType> types = new HashSet<>();
                do
                {
                    final TemplateType tt = type(ctx);
                    if(tt != null) types.add(tt);
                }
                while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACKET));
                return types;
            }
        }
        else
        {
            // Eat template name.
            final Token t = eat(ItrMode.prev,ctx.tokens,ctx.reporter,TokenType.ID);
            final Set<TemplateType> types = ctx.scope.getTypeSet(t.value());
            if(types == null) ctx.reporter.report(t.line(),"Unknown typeset '"+t.value()+'\'');
            else return copy? new HashSet<>(types) : types;
        }
        return null;
    }
    private static Set<TemplateType> typeSet(final Context ctx)
    {
        // TypeSetElement := (TemplateName|('[' Type {',' Type} ']'))
        // TypeSet        := TypeListElement {('+'|'-') TypeListElement}
        // Get the first set.
        final Set<TemplateType> types;
        {
            final Set<TemplateType> tt = typeSetElement(ctx,true);
            types = tt == null? new HashSet<>() : tt;
        }
        // Get remaining sets.
        while(matches(ItrMode.peek,ctx.tokens,TokenType.ADD,TokenType.SUB))
        {
            final boolean add = ctx.tokens.next().type() == TokenType.ADD;
            final Set<TemplateType> t = typeSetElement(ctx,false);
            if(t != null)
                if(add) types.addAll(t);
                else types.removeAll(t);
        }
        return types;
    }
    
    private static void conversion(final int line,final Context ctx,final TemplateType to)
    {
        ctx.bb.instruction(line,InstructionType.CONVERT,to);
    }
    private static void suffix(final Context ctx,final Token op)
    {
        switch(op.type())
        {
            case LBRACKET ->
            {
                // Parse list index.
                parseExpr(ctx);
                final int line = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RBRACKET).line();
                conversion(line,ctx,TemplateType.of(Type.INT));
                ctx.bb.instruction(line,InstructionType.LISTIDX,null);
                // The list pointer and index should now be popped, leaving a pointer to the element.
                suffix(ctx,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            case LPAREN ->
            {
                // Parse function call.
                int nArgs = 0;
                if(!matchThenEat(ctx.tokens,TokenType.RPAREN))
                    do {parseExpr(ctx); ++nArgs;}
                    while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RPAREN));
                ctx.bb.instruction(op.line(),InstructionType.CALL,nArgs);
                suffix(ctx,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            case INC,DEC ->
            {
                final int line = op.line();
                // Load value to stack twice, the first time is the original value and the second time is for modification.
                ctx.bb.instruction(line,InstructionType.LOAD,0);
                ctx.bb.instruction(line,InstructionType.LOAD,1);
                // Convert to number, increment/decrement, then convert back.
                ctx.bb.instruction(line,InstructionType.BOOL_TO_NUM,null);
                ctx.bb.instruction(line,InstructionType.PUSH,op.type() == TokenType.INC? 1:-1);
                ctx.bb.instruction(line,InstructionType.ADD,null);
                // Store to variable, last on stack should have previous value as expected.
                ctx.bb.instruction(line,InstructionType.STORE,3);
                ctx.bb.instruction(line,InstructionType.POP,null);
            }
            case DOT ->
            {
                final Token t = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                ctx.bb.instruction(t.line(),InstructionType.FIELD,t.value());
                suffix(ctx,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            default -> ctx.tokens.previous();
        }
    }
    private static void func(final Context ctx)
    {
        // Type           := ('bool'|'int'|'float'|'str'|StructType|FuncType|TemplateName) {'[' ']'}
        // TypeSetElement := (TypeSetName|('[' Type {',' Type} ']'))
        // TypeSet        := TypeSetElement {('+'|'-') TypeSetElement}
        
        // TemplateArg    := [TemplateName '='] TypeSet
        // TemplArgList   := '{' TemplateArg {',' TemplateArg} '}'
        // ArgTList       := Type {',' Type}
        // FuncType       := 'func' '<' [TemplArgList] [Type] '>' '(' [ArgTList] ')'
        
        // TemplateDef    := TemplateName '=' TypeSet
        // TemplDefList   := '{' [TemplateDef {',' TemplateDef}] '}'
        // ArgList        := Type Name {',' Type Name}
        // FuncDef        := 'func' '<' [TemplDefList] [Type] '>' '(' [ArgList] ')' '{' Body '}'
        
        // Assume 'func' already eaten.
        final Context nctx = new Context(ctx);
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LT);
        final boolean hasTemplate = matchThenEat(ctx.tokens,TokenType.LBRACE) && !matchThenEat(ctx.tokens,TokenType.RBRACE);
        if(hasTemplate)
            // Parse template info.
            do
            {
                // Name '=' TypeSet
                final Token n = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ASSIGN);
                if(!nctx.scope.putTemplate(n.value(),typeSet(ctx)))
                    ctx.reporter.report(n.line(),"Template named '"+n.value()+"' was already defined");
            }
            while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACE));
        // Parse return type.
        final TemplateType ret = matchThenEat(ctx.tokens,TokenType.GT)? TemplateType.of(Type.VOID) : type(ctx);
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN);
        // Parse args.
        if(!matchThenEat(ctx.tokens,TokenType.RPAREN))
            do
            {
                final ConstableTemplateType ct = new ConstableTemplateType(chkConst(ctx.tokens),type(ctx));
                final Token n = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                nctx.scope.putField(n.value(),ct);
                nctx.bb.instruction(n.line(),InstructionType.DECL,new Decl(n.value(),ct));
            }
            while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RPAREN));
        // Parse body.
        parseBlock(nctx);
        ctx.functions.add(new Func(nctx,ret));
    }
    private static void list(final Context ctx)
    {
        // '[' Type ':' [Expr {',' Expr}] ']'
        // Assume '[' already eaten.
        final TemplateType type = type(ctx);
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COLON);
        if(!matchThenEat(ctx.tokens,TokenType.RBRACKET))
            do
            {
                parseExpr(ctx);
                // TODO do something with list elements
            }
            while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACKET));
        // TODO pop all values from stack and push list
    }
    private static void struct(final Context ctx)
    {
        // '{' StructName ':' [Name '=' Expr {',' Name '=' Expr}] '}'
        // Assume '{' already eaten.
        final Token structName = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
        final Map<String,Scope.StructEntry> struct = ctx.scope.getStruct(structName.value());
        if(struct == null)
            ctx.reporter.report(structName.line(),"Struct with name '"+structName.value()+"' was not declared");
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COLON);
        if(!matchThenEat(ctx.tokens,TokenType.RBRACE))
            do
            {
                // Parse name.
                final Token field = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ASSIGN);
                parseExpr(ctx);
            }
            while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACE));
        // TODO check number of fields
        // TODO re-order and push to stack
    }
    private static void id(final Context ctx,final Token id)
    {
        final ConstableTemplateType field = ctx.scope.getField(id.value());
        if(field == null)
            ctx.reporter.report(id.line(),"No variable with name '"+id.value()+"' was declared");
        // Eat template spec and generate template functions.
        final int p = ctx.tokens.pos();
        if(matchThenEat(ctx.tokens,TokenType.LT))
        {
            final Token t = nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter);
            if
            (
                switch(t.type())
                {
                    case ID ->
                    {
                        final String type = t.value();
                        yield ctx.scope.getStruct(type) == null && !ctx.scope.templateDefined(type);
                    }
                    case VOID,BOOL,INT,FLOAT,STR,FUNC -> false;
                    default -> true;
                }
            )
            {
                ctx.tokens.pos(p);
                return;
            }
            final List<TemplateType> template = new ArrayList<>();
            do template.add(type(ctx));
            while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.GT));
            ctx.bb.instruction(t.line(),InstructionType.GENERATE_TEMPLATE,template.toArray(TemplateType[]::new));
        }
        ctx.bb.instruction(id.line(),InstructionType.PUSH,new Decl(id.value(),field));
    }
    private static void prefix(final Context ctx,final Token op)
    {
        highPrecedence(ctx);
        final int line = op.line();
        switch(op.type())
        {
            case INC,DEC ->
            {
                ctx.bb.instruction(line,InstructionType.LOAD,0);
                ctx.bb.instruction(line,InstructionType.BOOL_TO_NUM,null);
                ctx.bb.instruction(line,InstructionType.PUSH,op.type() == TokenType.INC? 1:-1);
                ctx.bb.instruction(line,InstructionType.ADD,null);
                ctx.bb.instruction(line,InstructionType.STORE,1);
            }
            case ADD -> ctx.bb.instruction(line,InstructionType.BOOL_TO_NUM,null);
            case SUB ->
            {
                ctx.bb.instruction(line,InstructionType.BOOL_TO_NUM,null);
                ctx.bb.instruction(line,InstructionType.NEG,null);
            }
            case NOT ->
            {
                conversion(line,ctx,TemplateType.of(Type.BOOL));
                ctx.bb.instruction(line,InstructionType.XOR,0);
            }
            case BITNOT ->
            {
                ctx.bb.instruction(line,InstructionType.BOOL_TO_NUM,null);
                ctx.bb.instruction(line,InstructionType.NOT,null);
            }
        }
    }
    private static InstructionType mathOp(final TokenType op)
    {
        return switch(op)
        {
            case ADD,   ADDEQ -> InstructionType.ADD;
            case BITAND,ANDEQ -> InstructionType.AND;
            case BITOR,  OREQ -> InstructionType.OR;
            case BITXOR,XOREQ -> InstructionType.XOR;
            case DIV,   DIVEQ -> InstructionType.DIV;
            case EQ           -> InstructionType.EQ;
            case GEQ          -> InstructionType.GEQ;
            case GT           -> InstructionType.GT;
            case LRSH, LRSHEQ -> InstructionType.LRSH;
            case LSH,   LSHEQ -> InstructionType.LSH;
            case LT           -> InstructionType.LT;
            case MOD,   MODEQ -> InstructionType.MOD;
            case MUL,   MULEQ -> InstructionType.MUL;
            case NEQ          -> InstructionType.NEQ;
            case RSH,   RSHEQ -> InstructionType.RSH;
            case SUB,   SUBEQ -> InstructionType.SUB;
            case LEQ          -> InstructionType.LEQ;
            default           -> null;
        };
    }
    private static void math(final Context ctx)
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
        final Token[] O = new Token[8]; // 8 happens to be the number of different precedence levels. Therefore,
                                        // there can never be more than 8 operators on the stack.
        int i = 0;
        // Loop until a non-operator token is encountered.
        while(precedence(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type()) != (byte)-1)
        {
            // Eat the operator and the expression to the right of it.
            O[i++] = ctx.tokens.next();
            highPrecedence(ctx);
            
            // While there are still operators on the stack and the cursor's operator is less than the stack's operator,
            // combine the operands and operators.
            while(i != 0 && precedence(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type()) <= precedence(O[i-1].type()))
                ctx.bb.instruction(O[--i].line(),mathOp(O[i].type()),null);
        }
        if(i != 0) ctx.reporter.report(O[i-1].line(),"Invalid math expression");
    }
    private static void ternary(final Context ctx)
    {
        // Expr '?' Expr : HighPrecedence
        // Assume 'Expr' already eaten.
        if(matchThenEat(ctx.tokens,TokenType.CONDITION))
        {
            conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
    
            // True branch.
            final BlockBuilder s = ctx.bb;
            s.b0 = ctx.bbList.size();
            ctx.nextBB("condition.true");
            parseExpr(ctx);
            final BlockBuilder t = ctx.bb;
    
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COLON);
    
            // False branch.
            s.b1 = ctx.bbList.size();
            ctx.nextBB("condition.false");
            highPrecedence(ctx);
    
            // End block.
            t.b0 = ctx.bb.b0 = ctx.bbList.size();
            ctx.nextBB("condition.end");
        }
    }
    private static void assignment(final Context ctx)
    {
        if(matches(ItrMode.peek,ctx.tokens,TokenType.ASSIGN,TokenType.ADDEQ,TokenType.ANDEQ,TokenType.DIVEQ,
                                           TokenType.LRSHEQ,TokenType.LSHEQ,TokenType.MODEQ,TokenType.MULEQ,
                                           TokenType.  OREQ,TokenType.RSHEQ,TokenType.SUBEQ,TokenType.XOREQ))
        {
            final Token t = ctx.tokens.next();
            final TokenType tt = t.type();
            final int line = t.line();
            parseExpr(ctx);
            if(tt != TokenType.ASSIGN)
            {
                ctx.bb.instruction(line,InstructionType.LOAD,0);
                ctx.bb.instruction(line,mathOp(tt),null);
            }
            ctx.bb.instruction(line,InstructionType.STORE,1);
            ctx.bb.instruction(line,InstructionType.POP,null);
        }
    }
    private static void typeComparison(final Context ctx)
    {
        final TemplateType a = type(ctx);
        final Token op = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.EQ,TokenType.NEQ);
        ctx.bb.instruction(op.line(),InstructionType.TYPE_COMPARISON,new TypeComparison(op.type() == TokenType.EQ,a,type(ctx)));
    }
    private static void highPrecedence(final Context ctx)
    {
        final Token t = nonEOF(ItrMode.next,ctx.tokens,ctx.reporter);
        final int line = t.line();
        switch(t.type())
        {
            case TRUE,FALSE ->
            {
                final boolean b = Boolean.parseBoolean(t.value());
                ctx.bb.instruction(line,InstructionType.PUSH,b);
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
                ctx.bb.instruction(line,InstructionType.PUSH,l);
            }
            case LIT_FLOAT ->
            {
                final double d = Double.parseDouble(t.value());
                ctx.bb.instruction(line,InstructionType.PUSH,d);
            }
            case LIT_STR ->
            {
                ctx.bb.instruction(line,InstructionType.PUSH,t.value());
                suffix(ctx,nonEOF(ItrMode.next,ctx.tokens,ctx.reporter));
            }
            case FUNC ->
            {
                final boolean isDef;
                {
                    final int pos = ctx.tokens.pos();
                    // Determine if there is a body.
                    skip(ctx.tokens,TokenType.LPAREN,null);
                    skip(ctx.tokens,TokenType.RPAREN,TokenType.LPAREN);
                    isDef = matches(ItrMode.peek,ctx.tokens,TokenType.LBRACE);
                    ctx.tokens.pos(pos);
                }
                if(isDef)
                {
                    func(ctx);
                    if(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type() == TokenType.LPAREN)
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
                        suffix(ctx,tt);
                    }
                }
                else
                {
                    ctx.tokens.previous();
                    typeComparison(ctx);
                }
            }
            case ID ->
            {
                if(ctx.scope.getStruct(t.value()) != null || ctx.scope.templateDefined(t.value()))
                {
                    ctx.tokens.previous();
                    typeComparison(ctx);
                }
                else id(ctx,t);
            }
            case BOOL,INT,FLOAT,STR ->
            {
                ctx.tokens.previous();
                typeComparison(ctx);
            }
            case LBRACKET -> list(ctx);
            case LBRACE -> struct(ctx);
            case INC,DEC,ADD,SUB,NOT,BITNOT -> prefix(ctx,t);
            case LPAREN ->
            {
                // Try to get a sub-expression.
                highPrecedence(ctx);
                math(ctx);
                ternary(ctx);
                assignment(ctx);
                eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN);
            }
        }
    }
    private static void mediumPrecedence(final Context ctx)
    {
        // Get a high-precedence expression.
        highPrecedence(ctx);
        // Parse binary operator, if necessary.
        if(precedence(nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter).type()) != (byte)-1)
            math(ctx);
    }
    private static void parseAnd(final Context ctx)
    {
        // Get a medium-precedence expression.
        mediumPrecedence(ctx);
        // Parse boolean and expression, if necessary.
        if(matchThenEat(ctx.tokens,TokenType.AND))
        {
            conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
            final BlockBuilder bb = ctx.bb;
            bb.b0 = ctx.bbList.size();
            ctx.nextBB("and.rhs");
            mediumPrecedence(ctx);
            conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
            bb.b1 = ctx.bb.b0 = ctx.bbList.size();
            ctx.nextBB("and.end");
        }
    }
    private static void parseOr(final Context ctx)
    {
        // Get a boolean and expression.
        parseAnd(ctx);
        // Parse boolean or expression, if necessary.
        if(matchThenEat(ctx.tokens,TokenType.OR))
        {
            conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
            final BlockBuilder bb = ctx.bb;
            bb.b1 = ctx.bbList.size();
            ctx.nextBB("or.rhs");
            parseAnd(ctx);
            conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
            bb.b0 = ctx.bb.b0 = ctx.bbList.size();
            ctx.nextBB("or.end");
        }
    }
    private static void lowPrecedence(final Context ctx)
    {
        // Get a high-precedence expression.
        parseOr(ctx);
        // Parse ternary expression if necessary.
        if(matches(ItrMode.peek,ctx.tokens,TokenType.CONDITION))
            ternary(ctx);
    }
    private static void parseExpr(final Context ctx)
    {
        // Get low-precedence expression.
        lowPrecedence(ctx);
        // Parse assignment expression if necessary
        if(matches(ItrMode.peek,ctx.tokens,
                   TokenType.ASSIGN,TokenType.ADDEQ,TokenType.ANDEQ,
                   TokenType.DIVEQ,TokenType.LRSHEQ,TokenType.LSHEQ,
                   TokenType.MODEQ,TokenType.MULEQ,TokenType.OREQ,
                   TokenType.RSHEQ,TokenType.SUBEQ,TokenType.XOREQ))
            assignment(ctx);
    }
    
    private static void parseStructDecl(final Context ctx)
    {
        // 'struct' Name (';'|('{' [Type Name {',' Type Name}] '}'))
        // Assume 'struct' already eaten.
        final Token n = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
        final Map<String,Scope.StructEntry> struct;
        if(matchThenEat(ctx.tokens,TokenType.SEMICOLON)) struct = null;
        else
        {
            // Eat '{'.
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LBRACE);
            // Get type-name pairs.
            if(!matchThenEat(ctx.tokens,TokenType.RBRACE))
            {
                final Map<String,Scope.StructEntry> s = new HashMap<>();
                int offset = 0;
                do
                {
                    final boolean isConst = chkConst(ctx.tokens);
                    final TemplateType type = type(ctx);
                    final String name = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID).value();
                    s.put(name,new Scope.StructEntry(ConstableTemplateType.of(isConst,type),offset++));
                }
                while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RBRACE));
                struct = ContainerUtil.makeImmutable(s);
            }
            else
            {
                ctx.reporter.warn(n.line(),"Empty struct declaration");
                struct = Map.of();
            }
        }
        if(ctx.scope.getStruct(n.value()) != null)
            ctx.reporter.report(n.line(),"A struct with the name '"+n.value()+"' was already declared");
        ctx.scope.putStruct(n.value(),struct);
    }
    private static void parseDeclStmt(final Context ctx)
    {
        // ['const'] Type Name ['=' Expr] {',' Name ['=' Expr]} ';'
        final ConstableTemplateType ct = ConstableTemplateType.of(chkConst(ctx.tokens),type(ctx));
        do
        {
            final Token name = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
            ctx.scope.putField(name.value(),ct);
            ctx.bb.instruction(name.line(),InstructionType.DECL,new Decl(name.value(),ct));
            if(matchThenEat(ctx.tokens,TokenType.ASSIGN))
            {
                final int line = ctx.tokens.peek(-1).line();
                parseExpr(ctx);
                ctx.bb.instruction(line,InstructionType.STORE,1);
                ctx.bb.instruction(line,InstructionType.POP,null);
            }
            else ctx.bb.instruction(name.line(),InstructionType.PUSH,null);
        }
        while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.SEMICOLON));
    }
    private static void parseExprStmt(final Context ctx)
    {
        parseExpr(ctx);
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);
    }
    private static void parseDeclOrExprStmt(final Context ctx)
    {
        final Token t = nonEOF(ItrMode.peek,ctx.tokens,ctx.reporter);
        switch(t.type())
        {
            case VOID,BOOL,INT,FLOAT,STR,FUNC,CONST -> parseDeclStmt(ctx);
            case ID ->
            {
                // Check structs.
                if(ctx.scope.getStruct(t.value()) != null || ctx.scope.templateDefined(t.value()))
                    parseDeclStmt(ctx);
                else
                    parseExprStmt(ctx);
            }
            default -> parseExprStmt(ctx);
        }
    }
    private static void parseBlock(final Context ctx)
    {
        // '{' {Statement} '}'
        // Assume '{' already eaten.
        if(!matchThenEat(ctx.tokens,TokenType.RBRACE))
        {
            ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_ENTER,null);
            ctx.scope.pushScope();
            parseStmts(ctx,true);
            ctx.scope.popScope();
            ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_EXIT,null);
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RBRACE);
        }
    }
    private static void parseIf(final Context ctx)
    {
        // 'if' '(' Condition ')' ThenStmt ['else' ElseStmt]
        // Assume 'if' already eaten.
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN);
        
        // Condition
        parseExpr(ctx);
        conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
        final BlockBuilder s = ctx.bb;
        s.b0 = ctx.bbList.size();
        
        // ThenStmt
        ctx.nextBB("if.then");
        parseStmt(ctx,false,true);
        
        // ElseStmt
        if(matchThenEat(ctx.tokens,TokenType.ELSE))
        {
            s.b1 = ctx.bbList.size();
            final BlockBuilder t = ctx.bb;
            ctx.nextBB("if.else");
            parseStmt(ctx,false,true);
            t.b0 = ctx.bbList.size();
        }
        ctx.bb.b0 = ctx.bbList.size();
        
        ctx.nextBB("if.end");
    }
    private static void parseFor(final Context ctx)
    {
        // 'for' '(' [Init] ';' [Cond] ';' [Upd] ')' Body
        // 'for' '(' [const] Type Name ':' Expr ')' Body
        // Assume 'for' already eaten.
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN);
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
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_ENTER,null);
        if(isForEach)
        {
            final ConstableTemplateType ct = ConstableTemplateType.of(chkConst(ctx.tokens),type(ctx));
            final Token e = eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.ID);
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.COLON);
            parseExpr(ctx);
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN);
            
            final int line = ctx.tokens.peek(-1).line();
            ctx.bb.instruction(line,InstructionType.FOR_ITR_START,new Decl(e.value(),ct));
            final int loopTarget = ctx.bb.b0 = ctx.bbList.size();
            ctx.nextBB("foreach.start");
            
            final BlockBuilder c = ctx.bb;
            c.instruction(line,InstructionType.FOR_ITR_TEST,null);
            c.b0 = ctx.bbList.size();
            ctx.nextBB("foreach.condition");
            
            ctx.bb.instruction(line,InstructionType.FOR_ITR_NEXT,null);
            parseStmt(ctx,false,true);
            ctx.bb.b0 = loopTarget;
            c.b1 = ctx.bbList.size();
            ctx.nextBB("foreach.next");
        }
        else
        {
            ctx.scope.pushScope();
            
            // Init
            if(!matchThenEat(ctx.tokens,TokenType.SEMICOLON))
                parseDeclOrExprStmt(ctx);
            final BlockBuilder endOfStart = ctx.bb;
            
            // Cond
            final BlockBuilder endOfCond;
            final int toCond;
            if(matchThenEat(ctx.tokens,TokenType.SEMICOLON))
            {
                toCond = -1;
                endOfCond = null;
            }
            else
            {
                toCond = ctx.bbList.size();
                ctx.nextBB("for.cond");
                parseExprStmt(ctx);
                conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
                endOfCond = ctx.bb;
            }
            
            // Upd
            final BlockBuilder endOfUpd;
            final int toBody,toBodyOrCond,fromBody;
            if(matchThenEat(ctx.tokens,TokenType.RPAREN))
            {
                endOfUpd = null;
                toBody = ctx.bbList.size();
                fromBody = toBodyOrCond = toCond == -1? toBody : toCond;
            }
            else
            {
                fromBody = ctx.bbList.size();
                ctx.nextBB("for.update");
                
                // Parse comma-separated expressions.
                do parseExpr(ctx);
                while(exitCondition(ctx.tokens,ctx.reporter,TokenType.COMMA,TokenType.RPAREN));
                
                toBody = ctx.bbList.size();
                toBodyOrCond = toCond == -1? toBody : toCond;
                // upd -> (body|cond)
                (endOfUpd = ctx.bb).b0 = toBodyOrCond;
            }
            // start -> (body|cond)
            endOfStart.b0 = toBodyOrCond;
            
            // Body
            // (upd|body|cond) -> body
            (endOfCond == null? endOfUpd == null? ctx.bb : endOfUpd : endOfCond).b0 = toBody;
            ctx.nextBB("for.body");
            ctx.scope.enterLoop();
            parseStmt(ctx,false,false);
            final List<Integer>[] cflow = ctx.scope.exitLoop();
            
            final int toEnd = ctx.bbList.size();
            // body -> (upd|body|cond)
            ctx.bb.b0 = toEnd;
            if(endOfCond != null)
                // cond -> end
                endOfCond.b1 = toEnd;
            ctx.nextBB("for.end");
            
            // Direct all control flow to the correct places.
            for(final int bb : cflow[0]) // break
                ctx.bbList.get(bb).b0 = toEnd;
            for(final int bb : cflow[1]) // continue
                ctx.bbList.get(bb).b0 = fromBody;
            
            ctx.scope.popScope();
        }
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_EXIT,null);
    }
    private static void parseWhile(final Context ctx)
    {
        // 'while' '(' Condition ')' Body
        // Assume 'while' already eaten.
    
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_ENTER,null);
        // Condition
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.LPAREN);
        final int toCond = ctx.bbList.size();
        ctx.nextBB("while.cond");
        parseExpr(ctx);
        conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
        final BlockBuilder endOfCond = ctx.bb;
        endOfCond.b0 = ctx.bbList.size();
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.RPAREN);
        
        // Body
        ctx.scope.enterLoop();
        ctx.nextBB("while.body");
        parseStmt(ctx,false,true);
        ctx.bb.b0 = toCond;
        final int toEnd = ctx.bbList.size();
        
        final List<Integer>[] cflow = ctx.scope.exitLoop();
        for(final int b : cflow[0])
            ctx.bbList.get(b).b0 = toEnd;
        for(final int c : cflow[1])
            ctx.bbList.get(c).b0 = toCond;
        
        endOfCond.b1 = ctx.bbList.size();
        ctx.nextBB("while.end");
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_EXIT,null);
    }
    private static void parseDo(final Context ctx)
    {
        // 'do' Body 'while' '(' Condition ')' ';'
        // Assume 'do' already eaten.
        
        // Body
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_ENTER,null);
        ctx.scope.enterLoop();
        final int toBody = ctx.bbList.size();
        ctx.nextBB("do.body");
        parseStmt(ctx,false,true);
        ctx.bb.b0 = ctx.bbList.size();
        final List<Integer>[] cflow = ctx.scope.exitLoop();
        
        // Condition
        ctx.nextBB("do.cond");
        parseExpr(ctx);
        conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.BOOL));
        ctx.bb.b0 = toBody;
        final int toEnd = ctx.bbList.size();
        ctx.bb.b1 = toEnd;
        
        for(final int b : cflow[0])
            ctx.bbList.get(b).b0 = toEnd;
        for(final int c : cflow[1])
            ctx.bbList.get(c).b0 = toBody;
        
        ctx.nextBB("do.end");
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.SC_EXIT,null);
    }
    private static void parseRet(final Context ctx)
    {
        // 'return' [Expr] ';'
        // Assume 'return' already eaten.
        final InstructionType it;
        if(matchThenEat(ctx.tokens,TokenType.SEMICOLON)) it = InstructionType.RET_VOID;
        else {parseExpr(ctx); it = InstructionType.RET; eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);}
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),it,null);
    }
    private static void parseThrow(final Context ctx)
    {
        // 'throw' [Expr] ';'
        // Assume 'throw' already eaten.
        final InstructionType it;
        if(matchThenEat(ctx.tokens,TokenType.SEMICOLON)) it = InstructionType.THROW_VOID;
        else
        {
            it = InstructionType.THROW;
            parseExpr(ctx);
            conversion(ctx.tokens.peek(-1).line(),ctx,TemplateType.of(Type.STR));
            eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);
        }
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),it,null);
    }
    private static void parseBreak(final Context ctx)
    {
        // 'break' ';'
        // Assume 'break' already eaten.
        ctx.scope.putBreak(ctx.bbList.size()-1);
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);
    }
    private static void parseContinue(final Context ctx)
    {
        // 'continue' ';'
        // Assume 'continue' already eaten.
        ctx.scope.putContinue(ctx.bbList.size()-1);
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);
    }
    private static void parseImport(final Context ctx)
    {
        // 'import' String ';'
        // Assume 'import' already eaten.
        ctx.bb.instruction(ctx.tokens.peek(-1).line(),InstructionType.IMPORT,eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.STR).value());
        eat(ItrMode.next,ctx.tokens,ctx.reporter,TokenType.SEMICOLON);
    }
    
    private static Status parseStmt(final Context ctx,final boolean block,final boolean branch)
    {
        final Token t = ctx.tokens.next();
        final TokenType tt = t.type();
        if(tt == TokenType.EOF || tt == TokenType.RBRACE)
        {
            if((tt == TokenType.RBRACE) != block)
            {
                ctx.reporter.report(t.line(),(block? "E":"Une")+"xpected closing brace");
                return Status.BAD;
            }
            return Status.END;
        }
        if(branch) ctx.scope.pushScope();
        switch(tt)
        {
            case STRUCT     -> parseStructDecl(ctx);
            case LBRACE     ->
            {
                if(ctx.tokens.canAdvance() && ctx.tokens.peek(1).type() == TokenType.COLON)
                {
                    ctx.tokens.previous(); // Puke previous token.
                    parseDeclOrExprStmt(ctx);
                }
                else parseBlock(ctx);
            }
            case IF         -> parseIf      (ctx);
            case FOR        -> parseFor     (ctx);
            case DO         -> parseDo      (ctx);
            case WHILE      -> parseWhile   (ctx);
            case RETURN     -> parseRet     (ctx);
            case THROW      -> parseThrow   (ctx);
            case BREAK      -> parseBreak   (ctx);
            case CONTINUE   -> parseContinue(ctx);
            case IMPORT     -> parseImport  (ctx);
            case SEMICOLON  -> {/* No-op. */}
            default         ->
            {
                ctx.tokens.previous(); // Puke previous token.
                parseDeclOrExprStmt(ctx);
            }
        }
        if(branch) ctx.scope.popScope();
        return Status.OK;
    }
    private static Status parseStmts(final Context ctx,final boolean block)
    {
        Status last;
        //noinspection StatementWithEmptyBody
        while((last = parseStmt(ctx,block,false)) == Status.OK);
        return last;
    }
    
    private static void compile(final Context ctx)
    {
        final Status status = parseStmts(ctx,false);
        if(!ctx.reporter.reportAll() && status == Status.END)
        {
            System.out.println(ctx.dump());
            // TODO do second pass
        }
    }
}






































