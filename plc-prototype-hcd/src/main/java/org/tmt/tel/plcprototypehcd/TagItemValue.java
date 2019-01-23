package org.tmt.tel.plcprototypehcd;

public class TagItemValue {


    public enum PlcTypes {
        REAL, INTEGER, BOOLEAN
    }


    String name;
    PlcTypes type;
    String value;


    String tagName;
    int tagMemberNumber;
    String javaTypeName;
    boolean isBoolean;
    int bitPosition;
    String units;



    public TagItemValue(String name, String javaTypeName, String tagName, int tagMemberNumber, int bitPosition, String units) {
        this.name = name;
        this.javaTypeName = javaTypeName;
        this.tagName = tagName;
        this.tagMemberNumber = tagMemberNumber;
        this.bitPosition = bitPosition;
        this.units = units;

        switch (javaTypeName) {
            case "Boolean": this.type = PlcTypes.BOOLEAN; break;
            case "Float": this.type = PlcTypes.REAL; break;
            case "Integer": this.type = PlcTypes.INTEGER; break;
        }

        this.isBoolean = (this.type.equals(PlcTypes.BOOLEAN));

    }

    public String getName() {
        return name;
    }

    public PlcTypes getType() {
        return type;
    }

    public String getPlcTypeString() {
        switch (type) {
            case BOOLEAN:
                return "boolean";
            case REAL:
                return "real";
            case INTEGER:
                return "integer";
            default:
                return "";
        }
    }

    public String getTagName() {
        return tagName;
    }

    public int getTagMemberNumber() {
        return tagMemberNumber;
    }

    public String getJavaTypeName() {
        return javaTypeName;
    }

    public boolean isBoolean() {
        return isBoolean;
    }

    public int getBitPosition() {
        return bitPosition;
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
