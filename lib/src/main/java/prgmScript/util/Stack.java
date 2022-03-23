package prgmScript.util;

import java.util.function.IntFunction;

public class Stack<T>
{
    private final IntFunction<T[]> arrGen;
    private T[] arr;
    private int pos = 0;
    
    public Stack(final IntFunction<T[]> arrGen)
    {
        this.arrGen = arrGen;
        arr = arrGen.apply(4);
    }
    public Stack(final Stack<T> stk)
    {
        arr = (arrGen = stk.arrGen).apply(stk.arr.length);
        System.arraycopy(stk.arr,0,arr,0,pos = stk.pos);
    }
    
    public boolean empty() {return pos == 0;}
    public void push(final T t)
    {
        if(pos == arr.length) System.arraycopy(arr,0,arr = arrGen.apply(arr.length*2),0,pos);
        arr[pos++] = t;
    }
    public T top() {return arr[pos-1];}
    public T pop()
    {
        final T t = arr[--pos];
        // Check for quarter usage, but only shrink by half so that repeatedly pushing and
        // popping doesn't cause a horrendous amount of resizes.
        if(pos < arr.length/4) System.arraycopy(arr,0,arr = arrGen.apply(arr.length/2),0,pos);
        return t;
    }
    
    public T[] data() {return arr;}
    public int pos() {return pos;}
}