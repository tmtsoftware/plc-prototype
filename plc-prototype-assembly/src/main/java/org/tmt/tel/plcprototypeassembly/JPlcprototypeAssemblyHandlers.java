package org.tmt.tel.plcprototypeassembly;

import akka.actor.typed.javadsl.ActorContext;
import csw.command.api.CurrentStateSubscription;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.command.client.messages.TopLevelActorMessage;
import csw.command.client.models.framework.ComponentInfo;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.javadsl.JComponentType;
import csw.location.api.models.*;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import akka .util.Timeout;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to PlcprototypeHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
public class JPlcprototypeAssemblyHandlers extends JComponentHandlers {

    private final JCswContext cswCtx;
    private final ILogger log;
    ICommandService hcd;
    private ActorContext<TopLevelActorMessage> actorContext;
    private Optional<CurrentStateSubscription> subscription = Optional.empty();


    JPlcprototypeAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx,JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());
        this.actorContext = ctx;
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        log.info("Initializing plcprototype assembly...");
        return CompletableFuture.runAsync(() -> {

        });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {

        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {

        if (trackingEvent instanceof LocationUpdated) {
            LocationUpdated updated = (LocationUpdated) trackingEvent;
            Location location = updated.location();
            hcd = CommandServiceFactory.jMake((AkkaLocation) (location), actorContext.getSystem());
            System.out.println("hcd located = " + hcd);

            // as a test, let's read the PLC

            /*
            Setup sc = new Setup(new Prefix("prototypeAssembly"), new CommandName("read"), Optional.of(new ObsId("Obs001")));
            Timeout timeout = new Timeout(100, TimeUnit.SECONDS);
            CompletableFuture<CommandResponse.SubmitResponse> immediateCommandF =
                    hcd.submit(sc, timeout)
                            .thenApply(
                                    response -> {
                                        if (response instanceof CommandResponse.CompletedWithResult) {
                                            //do something with completed result

                                            System.out.println("hcd command completed!  Result = " + ((CommandResponse.CompletedWithResult) response).result());


                                        } else {
                                            System.out.println("unexpected response = " + response);
                                        }
                                        return response;
                                    }
                            );
            */

            subscription = Optional.of(hcd.subscribeCurrentState(currentState -> {
                    log.info("receiving current state from PLC HCD:" + currentState);
            }));

        }

        // TODO: unsubscribe when HCD location is lost
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
