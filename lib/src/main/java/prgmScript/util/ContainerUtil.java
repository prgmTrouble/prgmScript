package prgmScript.util;

import java.util.Map;
import java.util.Set;

/** A utility class for manipulating containers. */
public final class ContainerUtil
{
    private ContainerUtil() {}
    
    /**
     * @return An immutable version of the specified map. The map's entries
     *         are retained, so any changes to the original map's entries
     *         may cause changes in the resulting map's entries, and vice
     *         versa.
     *
     * @throws NullPointerException if the argument or any of its keys or values are {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static <K,V> Map<K,V> makeImmutable(final Map<K,V> map)
    {
        if(map == null) throw new NullPointerException();
        return Map.ofEntries(map.entrySet().toArray(Map.Entry[]::new));
    }
    /**
     * @return An immutable version of the specified set. The set's entries
     *         are retained, so any changes to the original set's entries
     *         may cause changes in the resulting set's entries, and vice
     *         versa.
     *
     * @throws NullPointerException if the argument or any of its values are {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static <V> Set<V> makeImmutable(final Set<V> set)
    {
        if(set == null) throw new NullPointerException();
        return Set.of((V[])set.toArray());
    }
}