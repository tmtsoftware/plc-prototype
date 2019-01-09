package org.tmt.tel.plcprototypehcd;

public class Attribute {


    public enum PlcTypes {
        REAL, INTEGER, BOOLEAN
    }


    String name;
    PlcTypes type;
    String value;

    public Attribute(String name, Attribute.PlcTypes type, String value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public PlcTypes getType() {
        return type;
    }

    public float getFloatValue() throws Exception {
        if (type.equals(PlcTypes.REAL)) {
            return new Float(value).floatValue();
        }
        throw new Exception("Can only get float values for REAL types. Type for " + name + " is " + type);
    }

    public int getIntegerValue() throws Exception {
        if (type.equals(PlcTypes.INTEGER)) {
            return new Integer(value).intValue();
        }
        throw new Exception("Can only get float values for INTEGER types. Type for " + name + " is " + type);
    }


    public boolean getBooleanValue() throws Exception {
        if (type.equals(PlcTypes.BOOLEAN)) {
            return new Boolean(value).booleanValue();
        }
        throw new Exception("Can only get float values for BOOLEAN types. Type for " + name + " is " + type);
    }

}
