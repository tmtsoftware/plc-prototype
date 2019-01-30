package org.tmt.tel.plcprototypeassembly;


import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.models.Id;
import csw.params.core.models.Prefix;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class WritePlcCmdActor extends AbstractBehavior<ControlCommand> {

    private ActorContext<ControlCommand> actorContext;
    private ILogger log;
    private JCswContext cswCtx;
    private Optional<ICommandService> plcHcd;

    private WritePlcCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx, Optional<ICommandService> plcHcd) {
        this.actorContext = actorContext;
        this.log = cswCtx.loggerFactory().getLogger(actorContext, getClass());
        this.cswCtx = cswCtx;
        this.plcHcd = plcHcd;
    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, Optional<ICommandService> plcHcd) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new WritePlcCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx, plcHcd);
        });
    }


    @Override
    public Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug(() -> "Write Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }



    private Prefix templateHcdPrefix = new Prefix("plcAssembly.writeActor");


    private void handleSubmitCommand(ControlCommand message) {
        CompletableFuture<CommandResponse.SubmitResponse> writeFuture = write(message);

        writeFuture.thenAccept((response) -> {

            log.debug(() -> "response = " + response);
            log.debug(() -> "runId = " + message.runId());

            cswCtx.commandResponseManager().addSubCommand(message.runId(), response.runId());

            cswCtx.commandResponseManager().updateSubCommand(response);

            log.debug(() -> "write command message handled");


        });
    }

    CompletableFuture<CommandResponse.SubmitResponse> write (ControlCommand message){

        if (plcHcd.isPresent()) {

                CompletableFuture<CommandResponse.SubmitResponse> commandResponse = plcHcd.get()
                        .submit(
                                message,
                                Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS))
                        );

                return commandResponse;



        } else {
            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate HCD"));

        }
    }




}