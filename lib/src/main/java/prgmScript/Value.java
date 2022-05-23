package prgmScript;

/** A {@linkplain ConstableType} with a mutable value attached. */
public final class Value
{
    public final ConstableType type;
    Object value;
    
    Value(final ConstableType type,final Object value)
    {
        this.type = type;
        this.value = value;
    }
    
    @Override
    public boolean equals(final Object o)
    {
        return this == o || (o instanceof final Value o2 && value.equals(o2.value));
    }
    
    public Object getValue() {return value;}
}