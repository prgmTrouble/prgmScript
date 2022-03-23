package prgmScript;

import prgmScript.ast.BaseType;

import java.util.Objects;
import java.util.StringJoiner;

public record Type(boolean isConst,BaseType simple,String customName,Type complex,Type...args)
{
    @Override
    public String toString()
    {
        return (isConst? "const ":"")+switch(simple)
        {
            case LIST -> complex + "[]";
            case STRUCT -> customName;
            case FUNC ->
            {
                final StringJoiner sj = new StringJoiner(",","func<" + complex + ">(",")");
                for(final Type ct : args) sj.add(ct.toString());
                yield sj.toString();
            }
            default -> simple.name().toLowerCase();
        };
    }
    
    @Override
    public boolean equals(final Object o)
    {
        return this == o ||
        (
            o instanceof final Type ct &&
            simple == ct.simple &&
            Objects.equals(customName,ct.customName) &&
            Objects.equals(complex,ct.complex) &&
            Objects.deepEquals(args,ct.args)
        );
    }
}