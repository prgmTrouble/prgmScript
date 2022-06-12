package experimental;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
final class TemplateType
{
    final TemplateType st;
    final Type t;
    final String n;
    
    TemplateType(final TemplateType st,final Type t,final String n)
    {
        this.st = st;
        this.t = t;
        this.n = n;
    }
    
    @Override public String toString() {return n == null? t == null? st + "[]" : (t + "") : n;}
    
    @Override
    public boolean equals(final Object o)
    {
        return this == o ||
        (
            o instanceof final TemplateType o2 &&
            (
                (t == null && o2.t == null) ||
                (t != null && t.equals(o2.t))
            ) &&
            (
                (n == null && o2.n == null) ||
                (n != null && n.equals(o2.n))
            ) &&
            (
                (st == null && o2.st == null) ||
                (st != null && st.equals(o2.st))
            )
        );
    }
    @Override public int hashCode() {return Objects.hash(t,n);}
    
    private static final Map<TemplateType,TemplateType> TYPES = Collections.synchronizedMap(new HashMap<>());
    private static TemplateType getOrSet(final TemplateType key)
    {
        final TemplateType o = TYPES.putIfAbsent(key,key);
        return o == null? key : o;
    }
    static TemplateType of(final TemplateType st) {return getOrSet(new TemplateType(st,null,null));}
    static TemplateType of(final Type t) {return getOrSet(new TemplateType(null,t,null));}
    static TemplateType of(final String n) {return getOrSet(new TemplateType(null,null,n));}
}