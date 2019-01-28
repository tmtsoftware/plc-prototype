package org.tmt.tel.plcprototypeassembly;

import akka.actor.typed.ActorRef;
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
    private Optional<ICommandService> hcd = Optional.empty();
    private ActorContext<TopLevelActorMessage> actorContext;
    private Optional<CurrentStateSubscription> subscription = Optional.empty();

    private ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;



    JPlcprototypeAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());
        this.actorContext = ctx;
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        log.info("Initializing plcprototype assembly...");
        return CompletableFuture.runAsync(() -> {

            commandHandlerActor = actorContext.spawnAnonymous(JCommandHandlerActor.behavior(cswCtx, hcd));

        });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {

        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {


        System.out.println("LOCATION TRACKKING EVENT = " + trackingEvent);

        if (trackingEvent instanceof LocationUpdated) {
            LocationUpdated updated = (LocationUpdated) trackingEvent;
            Location location = updated.location();
            hcd = Optional.of(CommandServiceFactory.jMake((AkkaLocation) (location), actorContext.getSystem()));
            System.out.println("hcd located = " + hcd);

            // update command handler
            commandHandlerActor = actorContext.spawnAnonymous(JCommandHandlerActor.behavior(cswCtx, hcd));

            subscription = Optional.of(hcd.get().subscribeCurrentState(currentState -> {
                    log.info("receiving current state from PLC HCD:" + currentState);
            }));

        } else {

            hcd = Optional.empty();

            // TODO: unsubscribe when HCD location is lost
        }


    }


    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
        return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {

        commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
        return new CommandResponse.Started(controlCommand.runId());

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
