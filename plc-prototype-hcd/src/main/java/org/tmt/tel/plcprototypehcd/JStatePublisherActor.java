package org.tmt.tel.plcprototypehcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;

import akka.actor.typed.javadsl.*;

import akka.util.Timeout;
import csw.framework.models.JCswContext;

import csw.location.server.commons.ClusterAwareSettings;
import csw.params.core.generics.Key;
import csw.params.core.models.Prefix;
import csw.params.core.states.CurrentState;
import csw.params.core.states.StateName;


import csw.params.javadsl.JKeyType;
import csw.params.core.generics.Parameter;

import csw.framework.CurrentStatePublisher;
import csw.logging.javadsl.ILogger;


import java.time.Instant;
import java.util.concurrent.TimeUnit;

import csw.params.javadsl.JUnits;



public class JStatePublisherActor extends AbstractBehavior<JStatePublisherActor.StatePublisherMessage> {


    // add messages here
    interface StatePublisherMessage {
    }

    public static final class StartMessage implements StatePublisherMessage {
    }

    public static final class StopMessage implements StatePublisherMessage {
    }

    public static final class PublishMessage implements StatePublisherMessage {
    }


    private CurrentStatePublisher currentStatePublisher;
    private ILogger log;
    private TimerScheduler<StatePublisherMessage> timer;
    JCswContext cswCtx;
    ActorRef plcioActor;
    PlcConfig plcConfig;
    ActorContext actorContext;


    //prefix
    Prefix prefix = new Prefix("plc.hcd");


    private static final Object TIMER_KEY = new Object();


    private JStatePublisherActor(TimerScheduler<JStatePublisherActor.StatePublisherMessage> timer, JCswContext cswCtx, PlcConfig plcConfig, ActorRef plcioActor) {
        this.timer = timer;
        this.actorContext = actorContext;
        this.log = cswCtx.loggerFactory().getLogger(JCacheActor.class);
        this.currentStatePublisher = cswCtx.currentStatePublisher();
        this.cswCtx = cswCtx;
        this.plcioActor = plcioActor;
        this.plcConfig = plcConfig;

    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(JCswContext cswCtx, PlcConfig plcConfig, ActorRef plcioActor) {
        return Behaviors.withTimers(timers -> {
            return (AbstractBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>) timers, cswCtx, plcConfig, plcioActor);
        });
    }


    @Override
    public Receive<StatePublisherMessage> createReceive() {

        ReceiveBuilder<StatePublisherMessage> builder = receiveBuilder()
                .onMessage(StartMessage.class,
                        command -> {
                            log.info("StartMessage Received");
                            onStart(command);
                            return Behaviors.same();
                        })
                .onMessage(StopMessage.class,
                        command -> {
                            log.info("StopMessage Received");
                            onStop(command);
                            return Behaviors.same();
                        })
                .onMessage(PublishMessage.class,
                        command -> {
                            log.info("PublishMessage Received");
                            onPublishMessage(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void onStart(StartMessage message) {

        log.info("Start Message Received ");

        timer.startPeriodicTimer(TIMER_KEY, new PublishMessage(), java.time.Duration.ofSeconds(1));

        log.info("start message completed");


    }

    private void onStop(StopMessage message) {

        log.info("Stop Message Received ");
    }

    private void onPublishMessage(PublishMessage message) {

        log.info("Publish Message Received ");


        // Read PLC
        ActorSystem actorSystem = Adapter.toTyped(ClusterAwareSettings.system());
        TagItemValue[] tagItemValues = readTagValues(plcioActor,  actorSystem, plcConfig.telemetryTagItemValues);

        // example parameters for a current state

        CurrentState currentState = generateCurrentStateFromTagItemValues(tagItemValues);

        log.debug("Publishing Current State = " + currentState);

        currentStatePublisher.publish(currentState);

    }

    // TODO: need to support "withUnits()" from configuration

    private CurrentState generateCurrentStateFromTagItemValues(TagItemValue[] tagItemValues) {
        CurrentState currentState = new CurrentState(prefix, new StateName("PlcTelemetry"));

        Key timestampKey = JKeyType.TimestampKey().make("timestampKey");
        Parameter timestamp = timestampKey.set(Instant.now());
        currentState = currentState.add(timestamp);

        for (TagItemValue tagItemValue : tagItemValues) {

            switch (tagItemValue.javaTypeName) {

                case "Integer":
                    Key intKey = JKeyType.IntKey().make(tagItemValue.name);
                    Parameter intParam = intKey.set(new Integer(tagItemValue.value));
                    intParam = addUnits(intParam, tagItemValue.units);
                    currentState = currentState.add(intParam);
                    break;

                case "Float":
                    Key floatKey = JKeyType.FloatKey().make(tagItemValue.name);
                    Parameter floatParam = floatKey.set(new Float(tagItemValue.value));
                    floatParam = addUnits(floatParam, tagItemValue.units);
                    currentState = currentState.add(floatParam);
                    break;

                case "Boolean":
                    Key booleanKey = JKeyType.BooleanKey().make(tagItemValue.name);
                    Parameter booleanParam = booleanKey.set(new Boolean(tagItemValue.value));
                    booleanParam = addUnits(booleanParam, tagItemValue.units);
                    currentState = currentState.add(booleanParam);
                    break;
            }

        }

        return currentState;
    }

    private Parameter addUnits(Parameter param, String unitsString) {

        switch(unitsString) {

            case "meters": return param.withUnits(JUnits.meter);
            case "counts": return param.withUnits(JUnits.count);
            case "degrees": return param.withUnits(JUnits.degree);
            case "noUnits" : return param.withUnits(JUnits.NoUnits);
            default: return param.withUnits(JUnits.NoUnits);


        }

    }



    public static TagItemValue[] readTagValues(ActorRef<JPlcioActor.ReadMessage> actorRef, ActorSystem sys, TagItemValue[] tagItemValues) {
        final JPlcioActor.ReadResponseMessage readResponse;
        try {
            readResponse = AskPattern.ask(actorRef, (ActorRef<JPlcioActor.ReadResponseMessage> replyTo) ->
                            new JPlcioActor.ReadMessage(replyTo, tagItemValues)
                    , new Timeout(10, TimeUnit.SECONDS), sys.scheduler()).toCompletableFuture().get();
            //  log.debug(() -> "Got tag values from plcio actor - "
            return readResponse.tagItemValues;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }


    }

}
