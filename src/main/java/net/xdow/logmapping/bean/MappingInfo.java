package net.xdow.logmapping.bean;

public class MappingInfo {

    public enum Type {
        Unknown,
        String,
        Integer,
        Double,
        Null,
        Boolean,
        Char,
        Long,
        Array,
        MethodDecl,
    }

    public Type type;
    public String value;

    public MappingInfo(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return "MappingInfo{" +
                "type=" + type +
                ", value='" + value + '\'' +
                '}';
    }
}
