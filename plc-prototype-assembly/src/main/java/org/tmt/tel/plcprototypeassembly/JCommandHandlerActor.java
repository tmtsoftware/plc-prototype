package org.tmt.tel.plcprototypeassembly;



import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.command.client.messages.CommandMessage;
import csw.framework.models.JCswContext;
import csw.framework.models.JCswContext$;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.ControlCommand;

import java.util.Optional;


public class JCommandHandlerActor extends AbstractBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {}

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }


    private ActorContext<CommandMessage> actorContext;

    private ILogger log;
    private JCswContext cswCtx;
    private Optional<ICommandService> plcHcd;

    private JCommandHandlerActor(ActorContext<JCommandHandlerActor.CommandMessage> actorContext, JCswContext cswCtx, Optional<ICommandService> plcHcd) {
        this.actorContext = actorContext;
        this.log = cswCtx.loggerFactory().getLogger(actorContext, getClass());

        this.cswCtx = cswCtx;
        this.plcHcd = plcHcd;
    }

    public static <CommandMessage> Behavior<JCommandHandlerActor.CommandMessage> behavior(JCswContext cswCtx, Optional<ICommandService> plcHcd) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<JCommandHandlerActor.CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx, cswCtx, plcHcd);
        });
    }


    @Override
    public Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("readPlc"),
                        command -> {
                            log.info("ReadPlc Message Received");
                            handleReadPlc(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("writePlc"),
                        command -> {
                            log.info("Writelc Message Received");
                            handleWritePlc(command.controlCommand);
                            return Behaviors.same();
                        });

        return builder.build();
    }

    private void handleReadPlc(ControlCommand controlCommand) {

        log.info("handleReadPlc = " + controlCommand);

        log.info("plcHcd = " + plcHcd);

        ActorRef<ControlCommand> readPlcCmdActor =
                actorContext.spawnAnonymous(ReadPlcCmdActor.behavior(cswCtx, plcHcd));

        readPlcCmdActor.tell(controlCommand);

        // TODO: when the command is complete, kill the actor
        // ctx.stop(setTargetWavelengthCmdActor)

    }

    private void handleWritePlc(ControlCommand controlCommand) {

        log.info("handleWritePlc = " + controlCommand);


        ActorRef<ControlCommand> writePlcCmdActor =
                actorContext.spawnAnonymous(WritePlcCmdActor.behavior(cswCtx, plcHcd));

        writePlcCmdActor.tell(controlCommand);


        // TODO: when the command is complete, kill the actor
        // ctx.stop(setTargetWavelengthCmdActor)

    }


}