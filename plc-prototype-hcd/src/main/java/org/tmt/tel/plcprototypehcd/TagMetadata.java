package org.tmt.tel.plcprototypehcd;

import javax.swing.text.html.HTML;

public class TagMetadata {

    String tagName;
    String pcFormat;
    int byteLength;
    int memberCount;

    public TagMetadata(String tagName, String pcFormat, int byteLength, int memberCount) {
        this.tagName = tagName;
        this.pcFormat = pcFormat;
        this.byteLength = byteLength;
        this.memberCount = memberCount;
    }

    public String getTagName() {
        return tagName;
    }

    public String getPcFormat() {
        return pcFormat;
    }

    public int getByteLength() {
        return byteLength;
    }

    public int getMemberCount() {
        return memberCount;
    }
}
