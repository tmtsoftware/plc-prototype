package org.tmt.tel.plcprototypehcd;

import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import scala.collection.Iterator;
import scala.collection.JavaConversions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {

    public static Set<Parameter> generateParametersFromTagItemValues(TagItemValue[] tagItemValues) {

        Set<Parameter> parameters = new HashSet<Parameter>();

        if (tagItemValues == null) return parameters;

        for (TagItemValue tagItemValue : tagItemValues) {

            switch (tagItemValue.javaTypeName) {

                case "Integer":
                    Key intKey = JKeyType.IntKey().make(tagItemValue.name);
                    Parameter intParam = intKey.set(new Integer(tagItemValue.value));
                    intParam = addUnits(intParam, tagItemValue.units);
                    parameters.add(intParam);
                    break;

                case "Float":
                    Key floatKey = JKeyType.FloatKey().make(tagItemValue.name);
                    Parameter floatParam = floatKey.set(new Float(tagItemValue.value));
                    floatParam = addUnits(floatParam, tagItemValue.units);
                    parameters.add(floatParam);
                    break;

                case "Boolean":
                    Key booleanKey = JKeyType.BooleanKey().make(tagItemValue.name);
                    Parameter booleanParam = booleanKey.set(new Boolean(tagItemValue.value));
                    booleanParam = addUnits(booleanParam, tagItemValue.units);
                    parameters.add(booleanParam);
                    break;
            }

        }

        return parameters;

    }



    public static TagItemValue[] generateTagItemValuesFromParameters(scala.collection.immutable.Set<Parameter<?>> parameterSet, PlcConfig plcConfig) {
        List<TagItemValue> tagItemValueList = new ArrayList<TagItemValue>();

        Iterator<Parameter<?>> iterator = parameterSet.iterator();

        while (iterator.hasNext()) {

            Parameter parameter = iterator.next();
            tagItemValueList.add(plcConfig.name2TagItemValue.get(parameter.keyName()));
        }

        return tagItemValueList.toArray(new TagItemValue[0]);
    }

    public static TagItemValue[] generateTagItemValuesFromNamesAndValues(String[] names, String[] values, PlcConfig plcConfig) {
        List<TagItemValue> tagItemValueList = new ArrayList<TagItemValue>();


        for (int i=0; i<names.length; i++) {

            TagItemValue tagItemValue = plcConfig.name2TagItemValue.get(names[i]);
            tagItemValue.value = values[i];
            tagItemValueList.add(tagItemValue);

        }

        return tagItemValueList.toArray(new TagItemValue[0]);
    }



    private static Parameter addUnits(Parameter param, String unitsString) {

        switch(unitsString) {

            case "meters": return param.withUnits(JUnits.meter);
            case "counts": return param.withUnits(JUnits.count);
            case "degrees": return param.withUnits(JUnits.degree);
            case "noUnits" : return param.withUnits(JUnits.NoUnits);
            default: return param.withUnits(JUnits.NoUnits);


        }

    }

}
