package org.tmt.tel.plcprototypehcd;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
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
    HcdConfig hcdConfig;



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



    private JPlcioActor(ActorContext<JPlcioActor.PlcioMessage> actorContext, JCswContext cswCtx, HcdConfig hcdConfig) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JPlcioActor.class);
        this.hcdConfig = hcdConfig;




        log.info("Initializing ABPlcioMaster...");
        master = new ABPlcioMaster();
        log.info("Completed...");




        log.info("Cache Actor Created");
    }

    public static <PlcioMessage> Behavior<PlcioMessage> behavior(JCswContext cswCtx, HcdConfig hcdConfig) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<PlcioMessage>) new JPlcioActor((ActorContext<JPlcioActor.PlcioMessage>) ctx, cswCtx, hcdConfig);
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
                            return behavior(cswCtx, hcdConfig);
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

                TagMetadata tagMetadata = hcdConfig.tag2meta.get(tagName);
                List<TagItemValue> tagItems = hcdConfig.tag2ItemValueList.get(tagName);

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

                System.out.println("PLCIO ACTOR::readPlc::value = " + value);

                tagItemValue.value = value;

            }

            // send the response back
            readMessage.replyTo.tell(new ReadResponseMessage(readMessage.tagItemValues));



        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

    }


    private String readTagItemValue(String tagName, String itemName) {

        try {
            // perform a test read to prove it can be done within the HCD
            PlcioCall plcioCallOpen = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "cip 192.168.1.20",
                    "Scott_Conn", 0);


            // TODO: values here will be derived from the configuration
            TagItem tagItem1 = new TagItem(itemName, IPlcTag.PropTypes.REAL.getTypeString(), 0,
                    PlcioPcFormat.TYPE_R, 0, 0);


            TagItem[] tagItems1 = {tagItem1};


            PlcTag plcTag = new PlcTag(tagName, "" + PlcioPcFormat.TYPE_R,
                    10000, 1, 4, tagItems1);


            PlcioCall plcioCallRead = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "cip 192.168.1.20",
                    "Scott_Conn", 0, plcTag);


            PlcioCall plcioCallClose = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_CLOSE, "cip 192.168.1.20",
                    "Scott_Conn", 0);



            master.plcAccess(plcioCallOpen);
            master.plcAccess(plcioCallRead);
            master.plcAccess(plcioCallClose);

            String readValue = plcTag.getMemberValue(itemName);

            return readValue;

        } catch (Exception e) {
            log.error("" + e);
            e.printStackTrace();
            return null;
        }


    }



}
