package org.tmt.tel.plcprototypehcd;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import org.tmt.tel.javaplc.*;

import java.util.*;

/**
 * Cache Actor.
 */
public class JPlcioActor extends AbstractBehavior<JPlcioActor.PlcioMessage> {

    private ActorContext<PlcioMessage> actorContext;
    JCswContext cswCtx;

    private ILogger log;
    IABPlcioMaster master;
    PlcConfig plcConfig;
    private ActorRef<JCacheActor.CacheMessage> cacheActor;
    private boolean connectionOpen;
    private boolean initialized;



    // add messages here
    interface PlcioMessage {
    }

    public static final class WriteMessage implements PlcioMessage {
        public final TagItemValue[] tagItemValues;

        public WriteMessage(TagItemValue[] tagItemValues) {
            this.tagItemValues = tagItemValues;
        }
    }

    public static final class ReadMessage implements PlcioMessage {
        public final TagItemValue[] tagItemValues;
        public final ActorRef replyTo;

        public ReadMessage(ActorRef replyTo, TagItemValue[] tagItemValues) {
            this.tagItemValues = tagItemValues;
            this.replyTo = replyTo;
        }
    }

    public static final class ReadResponseMessage implements PlcioMessage {
        public final TagItemValue[] tagItemValues;

        public ReadResponseMessage(TagItemValue[] tagItemValues) {
            this.tagItemValues = tagItemValues;
        }
    }



    private JPlcioActor(ActorContext<JPlcioActor.PlcioMessage> actorContext, JCswContext cswCtx, PlcConfig plcConfig, ActorRef<JCacheActor.CacheMessage> cacheActor,
                        IABPlcioMaster master, boolean connectionOpen, boolean initialized) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JPlcioActor.class);
        this.plcConfig = plcConfig;
        this.cacheActor = cacheActor;
        this.connectionOpen = connectionOpen;
        this.master = master;
        this.initialized = initialized;


        if (!initialized) {

            // get all the tag item values known to the HCD
            TagItemValue[] tagItemValues = plcConfig.name2TagItemValue.values().toArray(new TagItemValue[0]);

            try {

                readPlcTagItems(tagItemValues);

                // initialize the Cache
                cacheActor.tell(new JCacheActor.UpdateMessage(tagItemValues));


            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }

            this.initialized = true;
        }



        log.info("Cache Actor Created");
    }

    public static <PlcioMessage> Behavior<PlcioMessage> behavior(JCswContext cswCtx, PlcConfig plcConfig, ActorRef<JCacheActor.CacheMessage> cacheActor, IABPlcioMaster master, boolean connectionOpen, boolean initialized) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<PlcioMessage>) new JPlcioActor((ActorContext<JPlcioActor.PlcioMessage>) ctx, cswCtx, plcConfig, cacheActor, master, connectionOpen, initialized);
        });
    }

    /**
     * This method receives messages sent to this worker actor.
     * @return
     */
    @Override
    public Receive<PlcioMessage> createReceive() {

        ReceiveBuilder<PlcioMessage> builder = receiveBuilder()
                .onMessage(WriteMessage.class,
                        message -> {
                            log.debug(() -> "UpdateMessage Received");
                            writePlc(message);
                            return behavior(cswCtx, plcConfig, cacheActor, master, connectionOpen, initialized);
                        })
                .onMessage(ReadMessage.class,
                        message -> {
                            log.debug(() -> "ReadMessage Received");
                            readPlc(message);
                            return behavior(cswCtx, plcConfig, cacheActor, master, connectionOpen, initialized);
                        });
        return builder.build();
    }

    /**
     * This method writes to the plc.
     * @param writeMessage
     */
    private void writePlc(WriteMessage writeMessage) {

        // get the unique set of tag names to write
        Map<String, Set<TagItemValue>> tagInfoMap = new HashMap<String, Set<TagItemValue>>();
        for (TagItemValue tagItemValue : writeMessage.tagItemValues) {
            Set<TagItemValue> itemValueSet = tagInfoMap.get(tagItemValue.tagName);
            if (itemValueSet == null) {
                itemValueSet = new HashSet<TagItemValue>();
                tagInfoMap.put(tagItemValue.tagName, itemValueSet);
            }
            itemValueSet.add(tagItemValue);
        }

        try {


            if (!connectionOpen) {
                // open PLC channel
                PlcioCall plcioCallOpen = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "cip 192.168.1.20",
                        "plcConnection", 0);

                master.plcAccess(plcioCallOpen);

                connectionOpen = true;
            }

            // read the appropriate tags from the PLC
            Map<String, PlcTag> tagName2PlcTag = readTagSet(tagInfoMap, plcConfig);

            // write new values for those tag members being updated
            // this includes setting bits for boolean values while leaving existing member bits alone
            for (TagItemValue tagItemValue : writeMessage.tagItemValues) {

                PlcTag plcTag = tagName2PlcTag.get(tagItemValue.tagName);
                plcTag.setTagItemValue(tagItemValue.name, tagItemValue.value);

            }
            writeTagSet(tagName2PlcTag.values());

            // update the Cache
            cacheActor.tell(new JCacheActor.UpdateMessage(writeMessage.tagItemValues));


        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * This method processes reads to the plc.
     * @param readMessage
     */
    private void readPlc(ReadMessage readMessage) {

        // the read message contains each value to be read.
        // Its better to read the whole tag once than read parts of it over and over.


        try {

            readPlcTagItems(readMessage.tagItemValues);


            // send the response back
            readMessage.replyTo.tell(new ReadResponseMessage(readMessage.tagItemValues));

            // update the Cache
            cacheActor.tell(new JCacheActor.UpdateMessage(readMessage.tagItemValues));



        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }

    }


    private void readPlcTagItems(TagItemValue[] tagItemValues) throws Exception {

        // get the unique set of tag names to read
        Map<String, Set<TagItemValue>> tagInfoMap = new HashMap<String, Set<TagItemValue>>();
        for (TagItemValue tagItemValue : tagItemValues) {
            Set<TagItemValue> itemValueSet = tagInfoMap.get(tagItemValue.tagName);
            if (itemValueSet == null) {
                itemValueSet = new HashSet<TagItemValue>();
                tagInfoMap.put(tagItemValue.tagName, itemValueSet);
            }
            itemValueSet.add(tagItemValue);

        }




        if (!connectionOpen) {
            // open PLC channel
            PlcioCall plcioCallOpen = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "cip 192.168.1.20",
                    "plcConnection", 0);

            master.plcAccess(plcioCallOpen);

            connectionOpen = true;
        }


        Map<String, PlcTag> tagName2PlcTag = readTagSet(tagInfoMap, plcConfig);

        //PlcioCall plcioCallClose = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_CLOSE, "cip 192.168.1.20",
        //        "readPlcConnection", 0);
        //master.plcAccess(plcioCallClose);

        // tagSet now has populated tag item values for each tag that was read


        for (TagItemValue tagItemValue : tagItemValues) {

            PlcTag plcTag = tagName2PlcTag.get(tagItemValue.tagName);

            String value = plcTag.getTagItemValue(tagItemValue.name);

            log.debug("PLCIO ACTOR::readPlc::value = " + value);

            tagItemValue.value = value;

        }

    }



    private Map<String, PlcTag> readTagSet(Map<String, Set<TagItemValue>> tagInfoMap, PlcConfig plcConfig) throws Exception {
        // Read each tag
        Map<String, PlcTag> tagName2PlcTag = new HashMap<String, PlcTag>();
        for (String tagName : tagInfoMap.keySet()) {

            TagMetadata tagMetadata = plcConfig.tag2meta.get(tagName);
            List<TagItemValue> tagItems = plcConfig.tag2ItemValueList.get(tagName);

            // create all the TagItems for the read
            List<TagItem> tagItemList = new ArrayList<TagItem>();
            for (TagItemValue tagItemValue : tagItems) {
                TagItem tagItem = new TagItem(tagItemValue.name, tagItemValue.getPlcTypeString(), tagItemValue.tagMemberNumber,
                        tagMetadata.pcFormat.charAt(tagItemValue.tagMemberNumber), 0, tagItemValue.bitPosition);
                tagItemList.add(tagItem);
            }
            // the API requires an array
            TagItem[] tagItemArray = tagItemList.toArray(new TagItem[0]);


            // create the plcTag that will be passed to the lower level API

            PlcTag plcTag = new PlcTag(tagName, tagMetadata.pcFormat,
                    10000, tagMetadata.memberCount, tagMetadata.byteLength, tagItemArray);

            tagName2PlcTag.put(tagName, plcTag);

            PlcioCall plcioCallRead = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "cip 192.168.1.20",
                    "plcConnection", 0, plcTag);

            master.plcAccess(plcioCallRead);

        }

        return tagName2PlcTag;

    }

    private void writeTagSet(Collection<PlcTag> plcTagCollection) throws Exception {
        // Write each tag

        for (PlcTag plcTag : plcTagCollection) {

            PlcioCall plcioCallWrite = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_WRITE, "cip 192.168.1.20",
                    "plcConnection", 0, plcTag);

            master.plcAccess(plcioCallWrite);

        }

    }




}
