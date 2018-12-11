package org.tmt.tel.plcprototypehcd;

import akka.actor.typed.javadsl.ActorContext;
import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.models.TrackingEvent;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;

import org.tmt.tel.javaplc.ABPlcioMaster;
import org.tmt.tel.javaplc.IABPlcioMaster;
import org.tmt.tel.javaplc.IPlcioCall;
import org.tmt.tel.javaplc.PlcioCall;
import org.tmt.tel.javaplc.TagItem;
import org.tmt.tel.javaplc.IPlcTag;
import org.tmt.tel.javaplc.PlcTag;
import org.tmt.tel.javaplc.PlcioPcFormat;
import org.tmt.tel.javaplc.TagItem;



import java.util.concurrent.CompletableFuture;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to PlcprototypeHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
public class JPlcprototypeHcdHandlers extends JComponentHandlers {

    private final JCswContext cswCtx;
    private final ILogger log;

    JPlcprototypeHcdHandlers(ActorContext<TopLevelActorMessage> ctx,JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
    log.info("Initializing plcprototype HCD...");
    return CompletableFuture.runAsync(() -> {

        try {
            log.info("Initializing ABPlcioMaster...");
            IABPlcioMaster master = new ABPlcioMaster();
            log.info("Completed...");


            // perform a test read to prove it can be done within the HCD
            PlcioCall plcioCallOpen1 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "cip 192.168.1.20",
                    "Scott_R_Conn", 0);
            PlcioCall plcioCallOpen2 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "cip 192.168.1.20",
                    "Scott_D_Conn", 0);


            TagItem tagItem1 = new TagItem("myRealValue", IPlcTag.PropTypes.REAL.getTypeString(), 0,
                    PlcioPcFormat.TYPE_R, 0, 0);

            TagItem tagItem2 = new TagItem("myDecimalValue", IPlcTag.PropTypes.INTEGER.getTypeString(), 0,
                    PlcioPcFormat.TYPE_J, 0, 0);

            TagItem tagItem3 = new TagItem("myBooleanValue", IPlcTag.PropTypes.BOOLEAN.getTypeString(), 0,
                    PlcioPcFormat.TYPE_C, 0, 0);

            TagItem tagItem4 = new TagItem("myRealValue", IPlcTag.PropTypes.REAL.getTypeString(), 0,
                    PlcioPcFormat.TYPE_R, 0, 0);


            TagItem[] tagItems1 = {tagItem1};
            TagItem[] tagItems2 = {tagItem2};
            TagItem[] tagItems3 = {tagItem3};
            TagItem[] tagItems4 = {tagItem4};


            PlcTag plcTag1 = new PlcTag("Scott_R", "" + PlcioPcFormat.TYPE_R,
                    10000, 1, 4, tagItems1);
            PlcTag plcTag2 = new PlcTag("Scott_D", "" + PlcioPcFormat.TYPE_J,
                    10000, 1, 4, tagItems2);
            PlcTag plcTag3 = new PlcTag("Scott_B", "" + PlcioPcFormat.TYPE_C,
                    10000, 1, 1, tagItems3);

            PlcTag plcTag4 = new PlcTag("Scott_R", "" + PlcioPcFormat.TYPE_R,
                    10000, 1, 4, tagItems4);
            String[] newValues = {"5678.0"};
            plcTag4.setMemberValues(newValues);


            PlcioCall plcioCallRead1 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "cip 192.168.1.20",
                    "Scott_R_Conn", 0, plcTag1);

            PlcioCall plcioCallRead2 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "cip 192.168.1.20",
                    "Scott_D_Conn", 1, plcTag2);

            PlcioCall plcioCallRead3 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "cip 192.168.1.20",
                    "Scott_D_Conn", 1, plcTag3);

            PlcioCall plcioCallWrite1 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_WRITE, "cip 192.168.1.20",
                    "Scott_R_Conn", 0, plcTag4);


            PlcioCall plcioCallClose1 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_CLOSE, "cip 192.168.1.20",
                    "Scott_R_Conn", 0);
            PlcioCall plcioCallClose2 = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_CLOSE, "cip 192.168.1.20",
                    "Scott_D_Conn", 1);


            // now lets try to read the compound tag

            TagItem tagItemB = new TagItem("myBooleanValue", IPlcTag.PropTypes.BOOLEAN.getTypeString(), 0,
                    PlcioPcFormat.TYPE_J, 0, 0);

            TagItem tagItemS = new TagItem("myDecimalValue", IPlcTag.PropTypes.INTEGER.getTypeString(), 1,
                    PlcioPcFormat.TYPE_J, 0, 0);

            TagItem tagItemR = new TagItem("myRealValue", IPlcTag.PropTypes.REAL.getTypeString(), 2,
                    PlcioPcFormat.TYPE_R, 0, 0);

            TagItem[] tagItemsU = {tagItemB, tagItemS, tagItemR};

            PlcTag plcTagU = new PlcTag("Scott_U",
                    "" + PlcioPcFormat.TYPE_J + PlcioPcFormat.TYPE_J + PlcioPcFormat.TYPE_R,
                    10000, 3, 12, tagItemsU);

            PlcioCall plcioCallReadU = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "cip 192.168.1.20",
                    "Scott_R_Conn", 0, plcTagU);


            master.plcAccess(plcioCallOpen1);
            master.plcAccess(plcioCallOpen2);
            //master.plcAccess(plcioCallRead1);
            //master.plcAccess(plcioCallRead2);
            //master.plcAccess(plcioCallRead3);
            master.plcAccess(plcioCallReadU);

            master.plcAccess(plcioCallWrite1);


            master.plcAccess(plcioCallClose1);
            master.plcAccess(plcioCallClose2);

            //String readValue1 = plcTag1.getMemberValue("myRealValue");
            //String readValue2 = plcTag2.getMemberValue("myDecimalValue");
            //String readValue3 = plcTag3.getMemberValue("myBooleanValue");

            //System.out.println("readValue1 read by client: " + readValue1);
            //System.out.println("readValue2 read by client: " + readValue2);
            //System.out.println("readValue3 read by client: " + readValue3);


            String readValueU1 = plcTagU.getTagItemValue("myRealValue");
            String readValueU2 = plcTagU.getTagItemValue("myDecimalValue");
            String readValueU3 = plcTagU.getTagItemValue("myBooleanValue");

            log.info("readValueU1 read by client: " + readValueU1);
            log.info("readValueU2 read by client: " + readValueU2);
            log.info("readValueU3 read by client: " + readValueU3);

        } catch (Exception e) {
            log.error("" + e);
            e.printStackTrace();
        }








    });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {

        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {

    }

    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
        return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {
        return new CommandResponse.Completed(controlCommand.runId());
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {

    }

    @Override
    public void onGoOffline() {

    }

    @Override
    public void onGoOnline() {

    }
}
