package experimental;

public final class Decl
{
    final String name;
    ConstableTemplateType type;
    
    Decl(final String name,final ConstableTemplateType type)
    {
        this.name = name;
        this.type = type;
    }
    
    @Override public String toString() {return "Decl{name='"+name+"',type='"+type+"'}";}
}