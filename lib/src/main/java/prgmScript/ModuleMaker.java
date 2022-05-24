package prgmScript;

import prgmScript.util.ContainerUtil;

import java.util.*;
import java.util.function.Function;

/**
 * A factory class which builds a {@linkplain Module}. Using this class is the only intended way to
 * write libraries for prgmScript using Java.
 */
public final class ModuleMaker
{
    private final Map<String,Map<String,ConstableType>> structs = new HashMap<>();
    private final Map<String,ConstableType> compileTime = new HashMap<>();
    private final Map<String,Value> runTime = new HashMap<>();
    
    /** @return A boolean {@linkplain Value}. */
    public static Value createBool(final boolean value,final boolean isConst)
    {
        return new Value(Types.constableType(Type.BOOL,isConst),value);
    }
    /** @return An integer {@linkplain Value}. */
    public static Value createInt(final long value,final boolean isConst)
    {
        return new Value(Types.constableType(Type.INT,isConst),value);
    }
    /** @return A floating-point {@linkplain Value}. */
    public static Value createFloat(final double value,final boolean isConst)
    {
        return new Value(Types.constableType(Type.FLOAT,isConst),value);
    }
    /**
     * @return A string {@linkplain Value}.
     *
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    public static Value createStr(final String value,final boolean isConst)
    {
        if(value == null) throw new NullPointerException();
        return new Value(Types.constableType(Type.STR,isConst),Script.strStruct(value));
    }
    /** A class representing the data needed to create a function. */
    @SuppressWarnings("ClassCanBeRecord")
    public static final class FuncInitializer
    {
        final Type funcType;
        final Function<Value[],Object> func;
        FuncInitializer(Type funcType,Function<Value[],Object> func)
        {
            this.funcType = funcType;
            this.func = func;
        }
    }
    private static void funcInitHelper(final Type returnType,final ConstableType...args)
    {
        if(returnType == null) throw new NullPointerException();
        for(final ConstableType ct : args)
        {
            if(ct == null) throw new NullPointerException();
            if(ct.type.base == BaseType.VOID) throw new IllegalArgumentException("Void typed argument");
        }
    }
    /**
     * @param func       The statement's body.
     * @param returnType The {@linkplain Type} of object returned by the function.
     * @param args       The {@linkplain Type} of each argument, if any.
     *
     * @return A {@linkplain FuncInitializer}.
     *
     * @throws NullPointerException if any of the arguments are {@code null}.
     * @throws IllegalArgumentException if any of the elements in {@code args} are void.
     *
     * @apiNote Since the actual body of the function argument ({@code func}) cannot be verified in this function,
     *          it is the user's responsibility to ensure that the function always returns the correct values.
     *          Functions whose return types are {@code void} will ignore the return value of said argument.
     *          Otherwise, the function argument must always return a non-null object of the correct type.
     */
    public static FuncInitializer createFuncInitializer(final Function<Value[],Object> func,final Type returnType,
                                                        final ConstableType...args)
    {
        if(func == null) throw new NullPointerException();
        funcInitHelper(returnType,args);
        return new FuncInitializer(Types.funcType(returnType,args),func);
    }
    /**
     * @return An array of {@linkplain FuncInitializer}s.
     *
     * @throws NullPointerException if any of the arguments or elements in {@code func} are {@code null}.
     * @throws IllegalArgumentException if any of the elements in {@code args} are void.
     *
     * @see ModuleMaker#createFuncInitializer(Function,Type,ConstableType...)
     *
     * @apiNote Since the actual body of the function argument ({@code func}) cannot be verified in this function,
     *          it is the user's responsibility to ensure that the function always returns the correct values.
     *          Functions whose return types are {@code void} will ignore the return value of said argument.
     *          Otherwise, the function argument must always return a non-null object of the correct type.
     */
    public static FuncInitializer[] createFuncInitializers(final Function<Value[],Object>[] func,final Type returnType,
                                                           final ConstableType...args)
    {
        if(func == null) throw new NullPointerException();
        funcInitHelper(returnType,args);
        for(final ConstableType ct : args)
        {
            if(ct == null) throw new NullPointerException();
            if(ct.type.base == BaseType.VOID) throw new IllegalArgumentException("Void typed argument");
        }
        final FuncInitializer[] fi = new FuncInitializer[func.length];
        final Type ft = Types.funcType(returnType,args);
        for(int i = 0;i < fi.length;++i)
        {
            final Function<Value[],Object> f = func[i];
            if(f == null) throw new NullPointerException();
            fi[i] = new FuncInitializer(ft,f);
        }
        return fi;
    }
    private static Value createFuncHelper(final Type funcType,final Function<Value[],Object> func,final boolean isConst)
    {
        final String[] argn = new String[funcType.args.length];
        final RuntimeScope rts = new RuntimeScope();
        for(int i = 0;i < funcType.args.length;++i)
            rts.putField(argn[i] = Character.toString(i),new Value(funcType.args[i],null));
        final Value[] args = new Value[argn.length];
        return new Value
        (
            Types.constableType(funcType,isConst),
            new Script.Func
            (
                rts,argn,
                funcType.args.length == 0
                    ? funcType.subType.base == BaseType.VOID
                          ? new Block(Type.VOID) {@Override Object exec(final RuntimeScope s) {func.apply(args); return Script.RET_VOID;}}
                          : new Block(funcType.subType) {@Override Object exec(final RuntimeScope s) {return func.apply(args);}}
                    : funcType.subType.base == BaseType.VOID
                          ? new Block(Type.VOID)
                            {
                                @Override
                                Object exec(final RuntimeScope s)
                                {
                                    {
                                        int i = 0;
                                        for(final String n : argn) args[i++] = s.getField(n);
                                    }
                                    func.apply(args);
                                    return Script.RET_VOID;
                                }
                            }
                          : new Block(funcType.subType)
                            {
                                @Override
                                Object exec(final RuntimeScope s)
                                {
                                    {
                                        int i = 0;
                                        for(final String n : argn) args[i++] = s.getField(n);
                                    }
                                    return func.apply(args);
                                }
                            }
            )
        );
    }
    /**
     * @return A function {@linkplain Value}.
     *
     * @throws NullPointerException if {@code func} is {@code null}.
     *
     * @see ModuleMaker#createFuncInitializer(Function,Type,ConstableType...)
     */
    public static Value createFunc(final FuncInitializer func,final boolean isConst)
    {
        if(func == null) throw new NullPointerException();
        return createFuncHelper(func.funcType,func.func,isConst);
    }
    /**
     * @return A function which creates {@linkplain Value}s from function lambdas.
     *
     * @throws NullPointerException if any of the arguments or elements in {@code args} are {@code null}.
     * @throws IllegalArgumentException if any of the elements in {@code args} are void.
     *
     * @see ModuleMaker#createFunc(FuncInitializer,boolean)
     * @see ModuleMaker#createFuncInitializer(Function,Type,ConstableType...)
     *
     * @apiNote This function is meant to defer function initialization to runtime instead of compile time. As a
     *          result, any errors which occur when instantiating the function will produce undefined results.
     *          Therefore, it is up to the user to properly verify that their code correctly satisfies all the
     *          conditions specified in
     *          {@linkplain ModuleMaker#createFuncInitializer(Function,Type,ConstableType...)}.
     */
    public static Function<Function<Value[],Object>,Value> funcCreator(final boolean isConst,final Type returnType,
                                                                       final ConstableType...args)
    {
        funcInitHelper(returnType,args);
        final Type ft = Types.funcType(returnType,args);
        return f -> createFuncHelper(ft,f,isConst);
    }
    /** A class representing the data needed to create a struct. */
    @SuppressWarnings("ClassCanBeRecord")
    public static final class StructInitializer
    {
        final String name;
        final Map<String,Value> values;
        
        StructInitializer(String name,Map<String,Value> values)
        {
            this.name = name;
            this.values = values;
        }
    }
    private Map<String,Value> structInitHelper(final Map<String,ConstableType> struct,
                                               final Map<String,Object> values)
    {
        if(!struct.keySet().equals(values.keySet()))
            throw new IllegalArgumentException("Argument's keys do not match the struct's keys");
        final Map<String,Value> rt = new HashMap<>(struct.size());
        for(final Map.Entry<String,Object> v : values.entrySet())
        {
            final Object val = v.getValue();
            if(val == null) throw new NullPointerException();
            final ConstableType ct = struct.get(v.getKey()); // Guaranteed by 'struct.keySet().equals(init.values.keySet())'
            final Class<?> cls = val.getClass();
            rt.put
            (
                v.getKey(),
                switch(ct.type.base)
                {
                    case BOOL ->
                    {
                        if(cls == Boolean.class) yield new Value(ct,val);
                        throw new IllegalArgumentException("Type mismatch: "+ct.type+" -> "+cls.getSimpleName());
                    }
                    case INT,FLOAT ->
                    {
                        if(cls == Byte.class || cls == Short.class ||
                           cls == Integer.class || cls == Long.class ||
                           cls == Float.class || cls == Double.class)
                            yield new Value(ct,ct.type.base == BaseType.INT? ((Number)val).longValue() : ((Number)val).doubleValue());
                        else if(cls == Character.class)
                            yield new Value(ct,ct.type.base == BaseType.INT? (long)(char)val : (double)(char)val);
                        else throw new IllegalArgumentException("Type mismatch: "+ct.type+" -> "+cls.getSimpleName());
                    }
                    case STR ->
                    {
                        if(cls == String.class) yield new Value(ct,val);
                        else throw new IllegalArgumentException("Type mismatch: "+ct.type+" -> "+cls.getSimpleName());
                    }
                    case LIST ->
                    {
                        Type t = ct.type;
                        do t = t.subType;
                        while(t.base == BaseType.LIST);
                        yield switch(t.base)
                        {
                            case BOOL,INT,FLOAT,STR -> createPrimitiveList(val,ct.isConst);
                            case FUNC -> createFuncList(val,t,ct.isConst);
                            case STRUCT -> createStructList(val,t.structName,ct.isConst);
                            default -> throw new IllegalArgumentException("Invalid list subtype: "+t);
                        };
                    }
                    case STRUCT ->
                    {
                        if(cls != StructInitializer.class)
                            throw new IllegalArgumentException("Type mismatch: "+ct.type+" -> "+cls.getSimpleName());
                        yield createStruct((StructInitializer)val,ct.isConst);
                    }
                    default /* FUNC */ ->
                    {
                        if(cls != FuncInitializer.class)
                            throw new IllegalArgumentException("Type mismatch: "+ct.type+" -> "+cls.getSimpleName());
                        yield createFunc((FuncInitializer)val,ct.isConst);
                    }
                }
            );
        }
        return ContainerUtil.makeImmutable(rt);
    }
    /**
     * @param name   The name of the struct.
     * @param values The struct instance's fields.
     *
     * @return A {@linkplain StructInitializer}.
     *
     * @throws NullPointerException if {@code name}, {@code values}, or any value in {@code values} are {@code null}.
     * @throws IllegalArgumentException if no struct with the specified name is defined, the key set in {@code values}
     *                                  does not match the key set in the declared struct, or a value in {@code values}
     *                                  does not match the type in the declared struct.
     */
    public StructInitializer createStructInitializer(final String name,final Map<String,Object> values)
    {
        if(name == null || values == null) throw new NullPointerException();
        final Map<String,ConstableType> struct = structs.get(name);
        if(struct == null) throw new IllegalArgumentException("Struct '"+name+"' is undefined");
        return new StructInitializer(name,structInitHelper(struct,values));
    }
    /**
     * @return An array of {@linkplain StructInitializer}s.
     *
     * @throws NullPointerException if {@code name}, {@code values}, or any value in {@code values} are {@code null}.
     * @throws IllegalArgumentException if no struct with the specified name is defined, the key set in {@code values}
     *                                  does not match the key set in the declared struct, or a value in {@code values}
     *                                  does not match the type in the declared struct.
     *
     * @see ModuleMaker#createStructInitializer(String,Map)
     */
    public StructInitializer[] createStructInitializers(final String name,final Map<String,Object>[] values)
    {
        if(name == null || values == null) throw new NullPointerException();
        final Map<String,ConstableType> struct = structs.get(name);
        if(struct == null) throw new IllegalArgumentException("Struct '"+name+"' is undefined");
        final StructInitializer[] si = new StructInitializer[values.length];
        int i = 0;
        for(final Map<String,Object> val : values)
            si[i++] = new StructInitializer(name,structInitHelper(struct,val));
        return si;
    }
    private static Value createStructHelper(final String name,final boolean isConst,final Map<String,Value> values)
    {
        return new Value(Types.constableType(Types.structType(name),isConst),values);
    }
    /**
     * @return A struct {@linkplain Value}.
     *
     * @throws NullPointerException if {@code init} is {@code null}.
     *
     * @see ModuleMaker#createStructInitializer(String,Map)
     */
    public Value createStruct(final StructInitializer init,final boolean isConst)
    {
        if(init == null) throw new NullPointerException();
        return createStructHelper(init.name,isConst,init.values);
    }
    /**
     * @return A function which creates {@linkplain Value}s from struct maps.
     *
     * @throws NullPointerException if {@code structName} is {@code null}.
     * @throws IllegalArgumentException if no struct with the specified name is defined.
     *
     * @see ModuleMaker#createStruct(StructInitializer,boolean)
     * @see ModuleMaker#createStructInitializer(String,Map)
     *
     * @apiNote This function is meant to defer struct instantiation to runtime instead of compile time. As a
     *          result, any errors which occur when instantiating the struct will produce undefined results.
     *          Therefore, it is up to the user to properly verify that their code correctly satisfies all the
     *          conditions specified in {@linkplain ModuleMaker#createStructInitializer(String,Map)}.
     */
    public Function<Map<String,Object>,Value> structCreator(final String structName,final boolean isConst)
    {
        if(structName == null) throw new NullPointerException();
        final Map<String,ConstableType> struct = structs.get(structName);
        if(struct == null) throw new IllegalArgumentException("Struct '"+structName+"' is undefined");
        return m -> createStructHelper(structName,isConst,structInitHelper(struct,m));
    }
    /**
     * @param value An array of any dimension containing primitive, primitive wrapper, or String values.
     *
     * @return A list {@linkplain Value}.
     *
     * @throws NullPointerException if {@code value} or any of its elements are {@code null}.
     * @throws IllegalArgumentException if {@code value} or its component types are invalid.
     */
    public static Value createPrimitiveList(final Object value,final boolean isConst)
    {
        if(value == null) throw new NullPointerException();
        final Class<?> c = value.getClass();
        if(!c.isArray()) throw new IllegalArgumentException("Argument is not an array.");
        final Class<?> c2 = c.componentType();
        final List<Value> l;
        final Type ct;
        if(c2.isArray())
        {
            final Object[] ll = (Object[])value;
            l = new ArrayList<>(ll.length);
            {
                final Value first = createPrimitiveList(ll[0],false);
                l.add(first);
                ct = first.type.type;
            }
            for(int i = 1;i != ll.length;++i)
            {
                final Value e = createPrimitiveList(ll[i],false);
                if(!e.type.type.equals(ct))
                    throw new IllegalArgumentException("Inconsistent element types: expected "+ct+", got "+e.type);
                l.add(e);
            }
        }
        else if(c2 == boolean.class)
        {
            ct = Type.BOOL;
            final boolean[] ll = (boolean[])value;
            l = new ArrayList<>(ll.length);
            for(final boolean b : ll) l.add(new Value(ConstableType.BOOL,b));
        }
        else if(c2 == Boolean.class)
        {
            ct = Type.BOOL;
            final Boolean[] ll = (Boolean[])value;
            l = new ArrayList<>(ll.length);
            for(final boolean b : ll) l.add(new Value(ConstableType.BOOL,b));
        }
        else if(c2 == byte.class)
        {
            ct = Type.INT;
            final byte[] ll = (byte[])value;
            l = new ArrayList<>(ll.length);
            for(final byte b : ll) l.add(new Value(ConstableType.INT,(long)b));
        }
        else if(c2 == Byte.class)
        {
            ct = Type.INT;
            final Byte[] ll = (Byte[])value;
            l = new ArrayList<>(ll.length);
            for(final byte b : ll) l.add(new Value(ConstableType.INT,(long)b));
        }
        else if(c2 == short.class)
        {
            ct = Type.INT;
            final short[] ll = (short[])value;
            l = new ArrayList<>(ll.length);
            for(final short s : ll) l.add(new Value(ConstableType.INT,(long)s));
        }
        else if(c2 == Short.class)
        {
            ct = Type.INT;
            final Short[] ll = (Short[])value;
            l = new ArrayList<>(ll.length);
            for(final short s : ll) l.add(new Value(ConstableType.INT,(long)s));
        }
        else if(c2 == int.class)
        {
            ct = Type.INT;
            final int[] ll = (int[])value;
            l = new ArrayList<>(ll.length);
            for(final int i : ll) l.add(new Value(ConstableType.INT,(long)i));
        }
        else if(c2 == Integer.class)
        {
            ct = Type.INT;
            final Integer[] ll = (Integer[])value;
            l = new ArrayList<>(ll.length);
            for(final int i : ll) l.add(new Value(ConstableType.INT,(long)i));
        }
        else if(c2 == long.class)
        {
            ct = Type.INT;
            final long[] ll = (long[])value;
            l = new ArrayList<>(ll.length);
            for(final long l1 : ll) l.add(new Value(ConstableType.INT,l1));
        }
        else if(c2 == Long.class)
        {
            ct = Type.INT;
            final Long[] ll = (Long[])value;
            l = new ArrayList<>(ll.length);
            for(final long l1 : ll) l.add(new Value(ConstableType.INT,l1));
        }
        else if(c2 == char.class)
        {
            ct = Type.INT;
            final char[] ll = (char[])value;
            l = new ArrayList<>(ll.length);
            for(final char c1 : ll) l.add(new Value(ConstableType.INT,(long)c1));
        }
        else if(c2 == Character.class)
        {
            ct = Type.INT;
            final Character[] ll = (Character[])value;
            l = new ArrayList<>(ll.length);
            for(final char c1 : ll) l.add(new Value(ConstableType.INT,(long)c1));
        }
        else if(c2 == float.class)
        {
            ct = Type.FLOAT;
            final float[] ll = (float[])value;
            l = new ArrayList<>(ll.length);
            for(final float f : ll) l.add(new Value(ConstableType.FLOAT,(double)f));
        }
        else if(c2 == Float.class)
        {
            ct = Type.FLOAT;
            final Float[] ll = (Float[])value;
            l = new ArrayList<>(ll.length);
            for(final float f : ll) l.add(new Value(ConstableType.FLOAT,(double)f));
        }
        else if(c2 == double.class)
        {
            ct = Type.FLOAT;
            final double[] ll = (double[])value;
            l = new ArrayList<>(ll.length);
            for(final double d : ll) l.add(new Value(ConstableType.FLOAT,d));
        }
        else if(c2 == Double.class)
        {
            ct = Type.FLOAT;
            final Double[] ll = (Double[])value;
            l = new ArrayList<>(ll.length);
            for(final double d : ll) l.add(new Value(ConstableType.FLOAT,d));
        }
        else if(c2 == String.class)
        {
            ct = Type.STR;
            final String[] ll = (String[])value;
            l = new ArrayList<>(ll.length);
            for(final String s : ll) l.add(new Value(ConstableType.STR,Script.strStruct(s)));
        }
        else throw new IllegalArgumentException("Invalid base type: "+c2.getSimpleName());
        return new Value(Types.constableType(Types.listType(ct),isConst),Script.listStruct(ct,l));
    }
    /**
     * @param value    An array of any dimension containing {@linkplain FuncInitializer}s.
     * @param funcType {@linkplain Type} of the function elements.
     *
     * @return A list {@linkplain Value}.
     *
     * @throws NullPointerException if {@code value} or any of its elements are {@code null}.
     * @throws IllegalArgumentException if {@code value} or its component types are invalid.
     *
     * @see ModuleMaker#createFuncInitializer(Function,Type,ConstableType...)
     */
    public static Value createFuncList(final Object value,final Type funcType,final boolean isConst)
    {
        if(value == null || funcType == null) throw new NullPointerException();
        final Class<?> c = value.getClass();
        if(!c.isArray()) throw new IllegalArgumentException("Argument is not an array");
        final Class<?> c2 = c.componentType();
        final List<Value> l;
        final Type ct;
        if(c2.isArray())
        {
            final Object[] ll = (Object[])value;
            l = new ArrayList<>(ll.length);
            {
                final Value first = createFuncList(ll[0],funcType,false);
                l.add(first);
                ct = first.type.type;
            }
            for(int i = 1;i != ll.length;++i)
            {
                final Value e = createFuncList(ll[i],funcType,false);
                if(!e.type.type.equals(ct))
                    throw new IllegalArgumentException("Inconsistent element types: expected "+ct+", got "+e.type);
                l.add(e);
            }
        }
        else if(c2 != FuncInitializer.class)
            throw new IllegalArgumentException("Expected array base type of "+FuncInitializer.class.getSimpleName()+", got "+c2.getSimpleName());
        else
        {
            ct = funcType;
            final FuncInitializer[] ll = (FuncInitializer[])value;
            l = new ArrayList<>(ll.length);
            for(final FuncInitializer f : ll)
            {
                l.add(createFunc(f,false)); // This is put first because it checks for null.
                if(!f.funcType.equals(funcType))
                    throw new IllegalArgumentException("Invalid function type: expected "+funcType+", got "+f.funcType);
            }
        }
        return new Value(Types.constableType(Types.listType(ct),isConst),Script.listStruct(funcType,l));
    }
    /**
     * @param value      An array of any dimension containing {@linkplain StructInitializer}s.
     * @param structName The name of the struct elements' type.
     *
     * @return A list {@linkplain Value}.
     *
     * @throws NullPointerException if {@code value} or any of its elements are {@code null}.
     * @throws IllegalArgumentException if {@code value} or its component types are invalid.
     *
     * @see ModuleMaker#createStructInitializer(String,Map)
     */
    public Value createStructList(final Object value,final String structName,final boolean isConst)
    {
        if(value == null) throw new NullPointerException();
        final Class<?> c = value.getClass();
        if(!c.isArray()) throw new IllegalArgumentException("Argument is not an array");
        final Class<?> c2 = c.componentType();
        final List<Value> l;
        final Type ct;
        if(c2.isArray())
        {
            final Object[] ll = (Object[])value;
            l = new ArrayList<>(ll.length);
            {
                final Value first = createStructList(value,structName,false);
                l.add(first);
                ct = first.type.type;
            }
            for(int i = 1;i != ll.length;++i)
            {
                final Value e = createStructList(ll[i],structName,false);
                if(!e.type.type.equals(ct))
                    throw new IllegalArgumentException("Inconsistent element types: expected "+ct+", got "+e.type);
                l.add(e);
            }
        }
        else if(c2 != StructInitializer.class)
            throw new IllegalArgumentException("Expected array base type of "+StructInitializer.class.getSimpleName()+", got "+c2.getSimpleName());
        else
        {
            ct = Types.structType(structName);
            final StructInitializer[] ll = (StructInitializer[])value;
            l = new ArrayList<>(ll.length);
            for(final StructInitializer s : ll)
            {
                if(s == null) throw new NullPointerException();
                if(!s.name.equals(structName))
                    throw new IllegalArgumentException("Invalid struct name: expected "+structName+", got "+s.name);
                l.add(createStruct(s,false));
            }
        }
        return new Value(Types.constableType(Types.listType(ct),isConst),Script.listStruct(ct,l));
    }
    /**
     * @param list A list {@linkplain Value}.
     *
     * @throws NullPointerException if {@code list} is {@code null}.
     * @throws IllegalArgumentException if {@code list}'s type is invalid.
     *
     * @apiNote Data contained in void lists is inaccessible to scripts. Unless there is a specific
     *          reason why a void list is necessary, avoid using void lists.
     */
    public Value createVoidList(final Value list,final boolean isConst)
    {
        if(list == null) throw new NullPointerException();
        if(list.type.type.base != BaseType.LIST) throw new IllegalArgumentException("Invalid type: "+list.type);
        return new Value
        (
            Types.constableType(Types.listType(Types.VOID),isConst),
            Script.voidList(list.type.type.subType,Script.listData(list.value))
        );
    }
    
    /**
     * Declares the specified symbol and assigns its value.
     *
     * @param name Name of the field.
     * @param value The field's {@linkplain Value}.
     *
     * @throws NullPointerException if either {@code name} or {@code value} are {@code null}.
     *
     * @see ModuleMaker#createBool(boolean,boolean)
     * @see ModuleMaker#createInt(long,boolean)
     * @see ModuleMaker#createFloat(double,boolean)
     * @see ModuleMaker#createStr(String,boolean)
     * @see ModuleMaker#createFunc(FuncInitializer,boolean)
     * @see ModuleMaker#createStruct(StructInitializer,boolean)
     * @see ModuleMaker#createPrimitiveList(Object,boolean)
     * @see ModuleMaker#createFuncList(Object,Type,boolean)
     * @see ModuleMaker#createStructList(Object,String,boolean)
     */
    public ModuleMaker declareValue(final String name,final Value value)
    {
        if(name == null || value == null) throw new NullPointerException();
        compileTime.put(name,value.type);
        runTime.put(name,value);
        return this;
    }
    /**
     * Declares a boolean symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createBool(boolean,boolean)
     */
    public ModuleMaker declareBool(final String name,final boolean value,final boolean isConst)
    {
        return declareValue(name,createBool(value,isConst));
    }
    /**
     * Declares a 64-bit integral symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createInt(long,boolean)
     */
    public ModuleMaker declareInt(final String name,final long value,final boolean isConst)
    {
        return declareValue(name,createInt(value,isConst));
    }
    /**
     * Declares a 64-bit floating-point symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createFloat(double,boolean)
     */
    public ModuleMaker declareFloat(final String name,final double value,final boolean isConst)
    {
        return declareValue(name,createFloat(value,isConst));
    }
    /**
     * Declares a string symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createStr(String,boolean)
     */
    public ModuleMaker declareStr(final String name,final String value,final boolean isConst)
    {
        return declareValue(name,createStr(value,isConst));
    }
    /**
     * Declares a function symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createFunc(FuncInitializer,boolean)
     * @see ModuleMaker#createFuncInitializer(Function,Type,ConstableType...)
     */
    public ModuleMaker declareFunc(final String name,final boolean isConst,final Function<Value[],Object> func,
                                   final Type returnType,final ConstableType...args)
    {
        return declareValue(name,createFunc(createFuncInitializer(func,returnType,args),isConst));
    }
    /**
     * Declares a struct symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createStruct(StructInitializer,boolean)
     * @see ModuleMaker#createStructInitializer(String,Map)
     */
    public ModuleMaker declareStructValue(final String name,final String structName,final Map<String,Object> values,
                                          final boolean isConst)
    {
        return declareValue(name,createStruct(createStructInitializer(structName,values),isConst));
    }
    /**
     * Declares a primitive list symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createPrimitiveList(Object,boolean)
     */
    public ModuleMaker declarePrimitiveList(final String name,final Object value,final boolean isConst)
    {
        return declareValue(name,createPrimitiveList(value,isConst));
    }
    /**
     * Declares a function list symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createFuncList(Object,Type,boolean)
     * @see ModuleMaker#createFuncInitializers(Function[],Type,ConstableType...)
     */
    public ModuleMaker declareFuncList(final String name,final Object value,final Type funcType,final boolean isConst)
    {
        return declareValue(name,createFuncList(value,funcType,isConst));
    }
    /**
     * Declares a struct list symbol.
     *
     * @see ModuleMaker#declareValue(String,Value)
     * @see ModuleMaker#createStructList(Object,String,boolean)
     * @see ModuleMaker#createStructInitializers(String,Map[])
     */
    public ModuleMaker declareStructList(final String name,final Object values,final String structName,final boolean isConst)
    {
        return declareValue(name,createStructList(values,structName,isConst));
    }
    
    /**
     * @param structName Name of the new struct.
     * @param fields     A map containing each field's name and {@linkplain ConstableType}.
     *
     * @throws NullPointerException if any argument, field name, or field type is {@code null}.
     * @throws IllegalArgumentException if any field has an invalid type.
     */
    public ModuleMaker declareStructType(final String structName,final Map<String,ConstableType> fields)
    {
        if(structName == null) throw new NullPointerException();
        for(final Map.Entry<String,ConstableType> e : fields.entrySet())
        {
            final ConstableType ct = e.getValue();
            if(e.getKey() == null || ct == null) throw new NullPointerException();
            if(ct.type.base == BaseType.VOID)
                throw new IllegalArgumentException("Void typed field");
            if(ct.type.base == BaseType.STRUCT && !structs.containsKey(ct.type.structName))
                throw new IllegalArgumentException("Struct '"+ct.type.structName+"' is undefined");
        }
        structs.put(structName,ContainerUtil.makeImmutable(fields));
        return this;
    }
    
    /** Creates the {@linkplain Module}. */
    public Module make()
    {
        final Map<String,Map<String,ConstableType>> s = ContainerUtil.makeImmutable(structs);
        return new Module
        (
            new CompilerScopeEntry(ContainerUtil.makeImmutable(compileTime),s,Set.of()),
            new RuntimeScopeEntry(ContainerUtil.makeImmutable(runTime),s)
        );
    }
}