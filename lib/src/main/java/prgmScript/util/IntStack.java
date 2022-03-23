package prgmScript.util;

public class IntStack
{
    private int[] arr = new int[8];
    private int pos = 0;
    
    public boolean empty() {return pos == 0;}
    public void push(final int t)
    {
        if(pos == arr.length) System.arraycopy(arr,0,arr = new int[arr.length*2],0,pos);
        arr[pos++] = t;
    }
    public int top() {return arr[pos-1];}
    public int pop() {return arr[--pos];}
}