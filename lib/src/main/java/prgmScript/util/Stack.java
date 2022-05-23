package prgmScript.util;

import java.util.function.IntFunction;

/** A light-weight stack implementation which exposes its backing array. */
public class Stack<T>
{
    private final IntFunction<T[]> arrGen;
    private T[] arr;
    private int pos = 0;
    
    /**
     * Creates a new stack.
     *
     * @param arrGen An {@linkplain IntFunction} which takes a size argument and returns
     *               an array of that size.
     *               (e.g. {@code Stack<Integer> s = new Stack<>(Integer[]::new);})
     */
    public Stack(final IntFunction<T[]> arrGen) {arr = (this.arrGen = arrGen).apply(4);}
    /** Shallow-copies the specified stack. */
    public Stack(final Stack<T> stk) {System.arraycopy(stk.arr,0,arr = (arrGen = stk.arrGen).apply(stk.arr.length),0,pos = stk.pos);}
    
    /** @return {@code true} iff this stack is empty. */
    public boolean empty() {return pos == 0;}
    /** Pushes the specified value onto the stack. */
    public void push(final T t)
    {
        if(pos == arr.length) System.arraycopy(arr,0,arr = arrGen.apply(arr.length*2),0,pos);
        arr[pos++] = t;
    }
    /** @return The value at the top of the stack. */
    public T top() {return arr[pos-1];}
    /** @return The value removed from the top of the stack. */
    public T pop()
    {
        final T t = arr[--pos];
        // Check for quarter usage, but only shrink by half so that repeatedly pushing and
        // popping doesn't cause a horrendous amount of resizes.
        if(4 < pos && pos < arr.length/4) System.arraycopy(arr,0,arr = arrGen.apply(arr.length/2),0,pos);
        return t;
    }
    
    /** @return The backing array for the stack. */
    public T[] data() {return arr;}
    /** @return The stack's current size. */
    public int pos() {return pos;}
    /**
     * Manually sets the size of the stack.
     *
     * @throws IndexOutOfBoundsException if the argument is negative or greater than the current stack size.
     */
    public void pos(final int pos)
    {
        if(pos > this.pos) throw new IndexOutOfBoundsException();
        // Find the smallest power of two that is at least as large as the new stack size.
        final int size = (((this.pos = pos) - 1) & pos) != 0
            ? 1<<(Integer.highestOneBit(pos)+1) // pos is not a power of two, retain the highest bit and shift left once.
            : pos;
        // Reduce the size if necessary.
        if(4 < size && size < arr.length/4) System.arraycopy(arr,0,arr = arrGen.apply(size),0,pos);
    }
}