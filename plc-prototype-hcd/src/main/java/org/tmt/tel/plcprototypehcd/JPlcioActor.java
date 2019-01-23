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



    private JPlcioActor(ActorContext<JPlcioActor.PlcioMessage> actorContext, JCswContext cswCtx, PlcConfig plcConfig, ActorRef<JCacheActor.CacheMessage> cacheActor) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JPlcioActor.class);
        this.plcConfig = plcConfig;
        this.cacheActor = cacheActor;




        log.info("Initializing ABPlcioMaster...");
        master = new ABPlcioMaster();
        log.info("Completed...");




        log.info("Cache Actor Created");
    }

    public static <PlcioMessage> Behavior<PlcioMessage> behavior(JCswContext cswCtx, PlcConfig plcConfig, ActorRef<JCacheActor.CacheMessage> cacheActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<PlcioMessage>) new JPlcioActor((ActorContext<JPlcioActor.PlcioMessage>) ctx, cswCtx, plcConfig, cacheActor);
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
                            return behavior(cswCtx, plcConfig, cacheActor);
                        })
                .onMessage(ReadMessage.class,
                        message -> {
                            log.debug(() -> "ReadMessage Received");
                            readPlc(message);

                            // how do we return the value to the caller?

                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method writes to the plc.
     * @param writeMessage
     */
    private void writePlc(WriteMessage writeMessage) {


    }

    /**
     * This method processes reads to the plc.
     * @param readMessage
     */
    private void readPlc(ReadMessage readMessage) {

        // the read message contains each value to be read.
        // Its better to read the whole tag once than read parts of it over and over.

        // get the unique set of tag names to read
        Map<String, Set<TagItemValue>> tagInfoMap = new HashMap<String, Set<TagItemValue>>();
        for (TagItemValue tagItemValue : readMessage.tagItemValues) {
            Set<TagItemValue> itemValueSet = tagInfoMap.get(tagItemValue.tagName);
            if (itemValueSet == null) {
                itemValueSet = new HashSet<TagItemValue>();
                tagInfoMap.put(tagItemValue.tagName, itemValueSet);
            }
            itemValueSet.add(tagItemValue);

        }
        try {

            // open PLC channel
            PlcioCall plcioCallOpen = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "cip 192.168.1.20",
                    "readPlcConnection", 0);

            master.plcAccess(plcioCallOpen);

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
                    "readPlcConnection", 0, plcTag);

                master.plcAccess(plcioCallRead);


            }

            PlcioCall plcioCallClose = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_CLOSE, "cip 192.168.1.20",
                    "readPlcConnection", 0);
            master.plcAccess(plcioCallClose);

            // tagSet now has populated tag item values for each tag that was read

            for (TagItemValue tagItemValue : readMessage.tagItemValues) {

                PlcTag plcTag = tagName2PlcTag.get(tagItemValue.tagName);

                String value = plcTag.getTagItemValue(tagItemValue.name);

                log.debug("PLCIO ACTOR::readPlc::value = " + value);

                tagItemValue.value = value;

            }

            // send the response back
            readMessage.replyTo.tell(new ReadResponseMessage(readMessage.tagItemValues));

            // update the Cache
            cacheActor.tell(new JCacheActor.UpdateMessage(readMessage.tagItemValues));



        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }

    }


}
