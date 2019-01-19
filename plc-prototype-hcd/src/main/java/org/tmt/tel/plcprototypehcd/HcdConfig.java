package org.tmt.tel.plcprototypehcd;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

import java.util.*;

public class HcdConfig {

    TagItemValue[] telemetryTagItemValues;
    HashMap<String, TagItemValue> name2TagItemValue = new HashMap<String, TagItemValue>();
    Map<String, TagMetadata> tag2meta = new HashMap<String, TagMetadata>();
    Map<String, List<TagItemValue>> tag2ItemValueList = new HashMap<String, List<TagItemValue>>();


    public HcdConfig(Config hcdConfig) throws Exception {

        telemetryTagItemValues = setupTelemetryTagItemValues(hcdConfig);


        // Generate the map of tags to TagMetadata
        for (ConfigObject configObject : hcdConfig.getObjectList("plcHcdConfig.tags")) {

            String tagName = configObject.toConfig().getString("Name");
            String tagPcFormat = configObject.toConfig().getString("PcFormat");
            int byteLength = computeByteLength(tagPcFormat);
            int memberCount = computeMemberCount(tagPcFormat);

            tag2meta.put(tagName, new TagMetadata(tagName, tagPcFormat, byteLength, memberCount));

        }

        // Generate a map of tag value names to tag value objects
        // single class that can be passed instead of Config.
        for (ConfigObject configObject : hcdConfig.getObjectList("plcHcdConfig.variables")) {

            String name = configObject.toConfig().getString("Name");
            String tagName = configObject.toConfig().getString("Tag");
            int tagMemberNumber = configObject.toConfig().getInt("TagMemberNumber");
            String javaTypeName = configObject.toConfig().getString("JavaType");
            boolean isBoolean = configObject.toConfig().getBoolean("IsBoolean");
            int bitPosition = configObject.toConfig().getInt("BitPosition");

            TagItemValue tagItemValue = new TagItemValue(name, javaTypeName, tagName, tagMemberNumber, bitPosition);

            name2TagItemValue.put(name, tagItemValue);


            // add to a list for the tag the item is in
            List itemList = tag2ItemValueList.get(tagName);
            if (itemList == null) {
                itemList = new ArrayList<TagItemValue>();
                tag2ItemValueList.put(tagName, itemList);
            }
            itemList.add(tagItemValue);

        }




    }



    private TagItemValue[] setupTelemetryTagItemValues(Config hcdConfig) {

        // determine from the configuration the list of telemetry items and their tag metadata


        // generate the map of name to tagItemValue
        for (ConfigObject configObject : hcdConfig.getObjectList("plcHcdConfig.variables")) {

            String name = configObject.toConfig().getString("Name");
            String tagName = configObject.toConfig().getString("Tag");
            int tagMemberNumber = configObject.toConfig().getInt("TagMemberNumber");
            String javaTypeName = configObject.toConfig().getString("JavaType");
            boolean isBoolean = configObject.toConfig().getBoolean("IsBoolean");
            int bitPosition = configObject.toConfig().getInt("BitPosition");

            TagItemValue tagItemValue = new TagItemValue(name, javaTypeName, tagName, tagMemberNumber, bitPosition);

            name2TagItemValue.put(name, tagItemValue);

         }

        String telemetryItemListStr = hcdConfig.getString("plcHcdConfig.telemetry");
        List<String> telemetryItemList = Arrays.asList(telemetryItemListStr.replaceAll("\\s","").split(","));

        // construct the array of TagItemValues to read as telemetry
        List telemetryReadList = new ArrayList<TagItemValue>();
        for (String name : telemetryItemList) {
            telemetryReadList.add(name2TagItemValue.get(name));
        }

        return (TagItemValue[])telemetryReadList.toArray(new TagItemValue[0]);
    }

    private int computeByteLength(String pcFormat) throws Exception {

        int count = 0;
        for (int i = 0; i < pcFormat.length(); i++){
            char c = pcFormat.charAt(i);

            switch (c) {
                case 'c': count += 1; break;
                case 'i': count += 2; break;
                case 'j': count += 4; break;
                case 'q': count += 8; break;
                case 'r': count += 4; break;
                case 'd': count += 8; break;
                default: throw new Exception("Bad pcFormat: " + c);
            }
        }
        return count;
    }

    // TODO: this only works with formats like "jjrr", etc, not yet supporting "j2r2"
    private int computeMemberCount(String pcFormat) {
        return pcFormat.length();
    }
}
