package org.tmt.tel.plcprototypehcd;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.util.Timeout;
import com.typesafe.config.Config;
import csw.command.client.messages.CommandMessage;
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

import csw.params.commands.Result;

import csw.params.javadsl.JKeyType;
import org.tmt.tel.javaplc.ABPlcioMaster;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


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
    ABPlcioMaster master;

    private PlcConfig plcConfig;

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


            log.info("Initializing ABPlcioMaster...");
            master = new ABPlcioMaster();
            log.info("Completed...");


            plcConfig = new PlcConfig(config);

            cacheActor = ctx.spawnAnonymous(JCacheActor.behavior(cswCtx, null));

            plcioActor = ctx.spawnAnonymous(JPlcioActor.behavior(cswCtx, plcConfig, cacheActor, master, false));

            statePublisherActor =
                    ctx.spawnAnonymous(JStatePublisherActor.behavior(cswCtx, plcConfig, plcioActor));

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

            case "readPlc":
                log.debug("handling read command: " + controlCommand);

                // code for read goes here


                // convert from Parameters to tagItemValues (needs PlcConfig)
                TagItemValue[] tagItemValuesDesired = Utils.generateTagItemValuesFromParameters( controlCommand.paramSet(), plcConfig);

                // read from the cache

                TagItemValue[] tagItemValuesRead = readTagValues(cacheActor, actorContext.getSystem(), tagItemValuesDesired);

                // convert from tagItemValues to Parameters

                Set<Parameter> parameterSet = Utils.generateParametersFromTagItemValues(tagItemValuesRead);


                Result result = new Result("PlcHcd");

                for (Parameter parameter : parameterSet) {
                    result = result.add(parameter);
                }

                return new CommandResponse.CompletedWithResult(controlCommand.runId(), result);


            case "writePlc":
                log.debug("handling update command: " + controlCommand);

                String[] writeInfoArray = controlCommand.paramSet().head().jValues().toArray(new String[0]);

                List<String> writeNames = new ArrayList<String>();
                List<String> writeValues = new ArrayList<String>();

                for(String writeInfo: writeInfoArray) {
                    String arr[] = writeInfo.split(" ", 2);
                    String name = arr[0];
                    String value = arr[1];
                    writeNames.add(name);
                    writeValues.add(value);
                }

                TagItemValue[] tagItemValuesToWrite = Utils.generateTagItemValuesFromNamesAndValues(writeNames.toArray(new String[0]),
                        writeValues.toArray(new String[0]), plcConfig);


                plcioActor.tell(new JPlcioActor.WriteMessage(tagItemValuesToWrite));

                // the cache will be updated by the next synchronous read

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

    public static TagItemValue[] readTagValues(ActorRef<JCacheActor.CacheMessage> actorRef, ActorSystem sys, TagItemValue[] tagItemValues) {
        final JCacheActor.ReadResponseMessage readResponse;
        try {
            readResponse = AskPattern.ask(actorRef, (ActorRef<JCacheActor.ReadResponseMessage> replyTo) ->
                            new JCacheActor.ReadMessage(replyTo, tagItemValues)
                    , new Timeout(10, TimeUnit.SECONDS), sys.scheduler()).toCompletableFuture().get();
            //  log.debug(() -> "Got tag values from plcio actor - "
            return readResponse.tagItemValues;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }


    }

}
