package experimental;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
final class ConstableTemplateType
{
    final TemplateType type;
    final boolean isConst;
    
    ConstableTemplateType(final boolean isConst,final TemplateType type)
    {
        this.type = type;
        this.isConst = isConst;
    }
    
    @Override public String toString() {return (isConst? "const ":"") + type;}
    
    @Override
    public boolean equals(final Object o)
    {
        final TemplateType ot;
        if(o instanceof final TemplateType t) ot = t;
        else if(o instanceof final ConstableTemplateType t) ot = t.type;
        else return false;
        return type.equals(ot);
    }
    
    @Override public int hashCode() {return Objects.hash(type,isConst);}
    
    /** A synchronized map containing all non-primitive non-const and const instances, respectively. */
    private static final Map<TemplateType,ConstableTemplateType[]> STORAGE = Collections.synchronizedMap(new HashMap<>());
    
    /**
     * @return A {@linkplain ConstableTemplateType} representing the specified {@linkplain TemplateType} and const-ness.
     *
     * @throws NullPointerException if {@code t} is {@code null}.
     */
    public static ConstableTemplateType of(final boolean isConst,final TemplateType t)
    {
        if(t == null) throw new NullPointerException();
        ConstableTemplateType[] pair = STORAGE.get(t);
        if(pair == null) STORAGE.put(t,pair = new ConstableTemplateType[]
        {
            new ConstableTemplateType(false,t),
            new ConstableTemplateType(true,t)
        });
        return pair[isConst? 1:0];
    }
}