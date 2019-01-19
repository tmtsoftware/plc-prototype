package org.tmt.tel.plcprototypehcd;

import akka.actor.typed.javadsl.ActorContext;
import com.typesafe.config.Config;
import csw.command.api.CurrentStateSubscription;
import csw.command.client.messages.TopLevelActorMessage;
import csw.config.api.javadsl.IConfigClientService;
import csw.config.client.javadsl.JConfigClientFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.models.TrackingEvent;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;

import akka.actor.typed.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.typed.javadsl.Adapter;

import akka.stream.Materializer;
import csw.framework.exceptions.FailureStop;
//import csw.services.config.api.models.ConfigData;
import csw.config.api.models.ConfigData;
//import csw.services.config.client.internal.ActorRuntime;
import csw.location.server.internal.ActorRuntime;

import java.nio.file.Paths;
import java.nio.file.Path;


import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.Id;
import csw.params.core.models.Prefix;
import org.tmt.tel.javaplc.ABPlcioMaster;
import org.tmt.tel.javaplc.IABPlcioMaster;
import org.tmt.tel.javaplc.IPlcioCall;
import org.tmt.tel.javaplc.PlcioCall;
import org.tmt.tel.javaplc.TagItem;
import org.tmt.tel.javaplc.IPlcTag;
import org.tmt.tel.javaplc.PlcTag;
import org.tmt.tel.javaplc.PlcioPcFormat;
import org.tmt.tel.javaplc.TagItem;

import csw.params.commands.Result;

import csw.params.javadsl.JKeyType;


import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


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
    private ActorContext<TopLevelActorMessage> actorContext;
    private final ILogger log;

    private ActorRef<JCacheActor.CacheMessage> cacheActor;
    private ActorRef<JPlcioActor.PlcioMessage> plcioActor;
    private IConfigClientService clientApi;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    JPlcprototypeHcdHandlers(ActorContext<TopLevelActorMessage> ctx,JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.actorContext = ctx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());

        // Handle to the config client service
        clientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), cswCtx.locationService());

        // Load the configuration from the configuration service
        Config config = getHcdConfig();

        try {

            HcdConfig hcdConfig = new HcdConfig(config);

            cacheActor = ctx.spawnAnonymous(JCacheActor.behavior(cswCtx, null));

            plcioActor = ctx.spawnAnonymous(JPlcioActor.behavior(cswCtx, hcdConfig));

            statePublisherActor =
                    ctx.spawnAnonymous(JStatePublisherActor.behavior(cswCtx, hcdConfig, plcioActor));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
    log.info("Initializing plcprototype HCD...");
    return CompletableFuture.runAsync(() -> {


        JStatePublisherActor.StartMessage message = new JStatePublisherActor.StartMessage();

        statePublisherActor.tell(message);

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

        // here is where we do the next step
        switch (controlCommand.commandName().name()) {

            case "read":
                log.debug("handling read command: " + controlCommand);

                // code for read goes here

                String value = "6.4";


                Key<Double> basePosKey = JKeyType.DoubleKey().make("basePos");

                Parameter<Double> basePosParam = basePosKey.set(new Double(value));

                Result result = new Result("PlcHcd").add(basePosParam);

                return new CommandResponse.CompletedWithResult(controlCommand.runId(), result);


            case "update":
                log.debug("handling update command: " + controlCommand);

                // code for write goes here

                return new CommandResponse.Completed(controlCommand.runId());


            default:
                log.error("unhandled message in onSubmit: " + controlCommand);
                // maintain actor state
                return new CommandResponse.Error(controlCommand.runId(), "unhandled message type");

        }



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







    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }

    private Config getHcdConfig() {

        try {
            ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getHcdConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new ConfigNotAvailableException();
        }

    }

    private ConfigData getHcdConfigData() throws ExecutionException, InterruptedException {

        log.info("loading assembly configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/plcHcdConfig.conf");

        ConfigData activeFile = clientApi.getActive(filePath).get().get();

        return activeFile;
    }


}
