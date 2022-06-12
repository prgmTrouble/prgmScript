package experimental.util;

/** A very simple stack which holds integers. */
public class IntStack
{
    private int[] arr = new int[8];
    private int pos = 0;
    
    /** @return {@code true} iff the stack is empty. */
    public boolean empty() {return pos == 0;}
    /** Pushes the integer to the stack. */
    public void push(final int t)
    {
        if(pos == arr.length) System.arraycopy(arr,0,arr = new int[arr.length*2],0,pos);
        arr[pos++] = t;
    }
    /** Pops the integer from the stack. */
    public int pop() {return arr[--pos];}
}