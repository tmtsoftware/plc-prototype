package org.tmt.tel.plcprototypehcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;

import akka.actor.typed.javadsl.*;

import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import csw.framework.models.JCswContext;

import csw.location.server.commons.ClusterAwareSettings;
import csw.params.core.generics.Key;
import csw.params.core.models.Prefix;
import csw.params.core.states.CurrentState;
import csw.params.core.states.StateName;


import csw.params.javadsl.JUnits;
import csw.params.javadsl.JKeyType;
import csw.params.core.generics.Parameter;

import csw.framework.CurrentStatePublisher;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import scala.concurrent.duration.Duration;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static csw.params.javadsl.JUnits.degree;


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
    HcdConfig hcdConfig;
    ActorContext actorContext;


    //prefix
    Prefix prefix = new Prefix("plc.hcd");


    private static final Object TIMER_KEY = new Object();


    private JStatePublisherActor(TimerScheduler<JStatePublisherActor.StatePublisherMessage> timer, JCswContext cswCtx, HcdConfig hcdConfig, ActorRef plcioActor) {
        this.timer = timer;
        this.actorContext = actorContext;
        this.log = cswCtx.loggerFactory().getLogger(JCacheActor.class);
        this.currentStatePublisher = cswCtx.currentStatePublisher();
        this.cswCtx = cswCtx;
        this.plcioActor = plcioActor;
        this.hcdConfig = hcdConfig;

    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(JCswContext cswCtx, HcdConfig hcdConfig, ActorRef plcioActor) {
        return Behaviors.withTimers(timers -> {
            return (AbstractBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>) timers, cswCtx, hcdConfig, plcioActor);
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
        TagItemValue[] tagItemValues = readTagValues(plcioActor,  actorSystem, hcdConfig.telemetryTagItemValues);

        for (TagItemValue tagItemValue : tagItemValues) {
            System.out.println("tagItemValues = " + tagItemValue.name + "::" + tagItemValue.value);
        }
        // example parameters for a current state

        CurrentState currentState = generateCurrentStateFromTagItemValues(tagItemValues);


        System.out.println("Publishing Current State = " + currentState);

        currentStatePublisher.publish(currentState);


    }

    // TODO: need to support "withUnits()" from configuration

    private CurrentState generateCurrentStateFromTagItemValues(TagItemValue[] tagItemValues) {
        CurrentState currentState = new CurrentState(prefix, new StateName("a state"));

        Key timestampKey = JKeyType.TimestampKey().make("timestampKey");
        Parameter timestamp = timestampKey.set(Instant.now());
        currentState.add(timestamp);

        for (TagItemValue tagItemValue : tagItemValues) {

            switch (tagItemValue.javaTypeName) {

                case "Integer":
                    Key intKey = JKeyType.IntKey().make(tagItemValue.name);
                    Parameter intParam = intKey.set(new Integer(tagItemValue.value));
                    currentState.add(intParam);
                    break;

                case "Float":
                    Key floatKey = JKeyType.FloatKey().make(tagItemValue.name);
                    Parameter floatParam = floatKey.set(new Float(tagItemValue.value));
                    currentState.add(floatParam);
                    break;

                case "Boolean":
                    Key booleanKey = JKeyType.BooleanKey().make(tagItemValue.name);
                    Parameter booleanParam = booleanKey.set(new Boolean(tagItemValue.value));
                    currentState.add(booleanParam);
                    break;
            }

        }

        return currentState;
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
