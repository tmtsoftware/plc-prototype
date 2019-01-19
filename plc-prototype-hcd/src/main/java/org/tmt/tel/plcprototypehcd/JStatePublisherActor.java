package org.tmt.tel.plcprototypehcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;

import akka.actor.typed.javadsl.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import csw.framework.models.JCswContext;

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
    interface StatePublisherMessage {}

    public static final class StartMessage implements StatePublisherMessage { }
    public static final class StopMessage implements StatePublisherMessage { }
    public static final class PublishMessage implements StatePublisherMessage { }


    private CurrentStatePublisher currentStatePublisher;
    private ILogger log;
    private TimerScheduler<StatePublisherMessage> timer;
    JCswContext cswCtx;
    ActorRef plcioActor;
    HcdConfig hcdConfig;



    //prefix
    Prefix prefix = new Prefix("tcs.test");

    //keys
    Key timestampKey    = JKeyType.TimestampKey().make("timestampKey");

    Key azPosKey        = JKeyType.DoubleKey().make("azPosKey");
    Key azPosErrorKey   = JKeyType.DoubleKey().make("azPosErrorKey");
    Key elPosKey        = JKeyType.DoubleKey().make("elPosKey");
    Key elPosErrorKey   = JKeyType.DoubleKey().make("elPosErrorKey");
    Key azInPositionKey = JKeyType.BooleanKey().make("azInPositionKey");
    Key elInPositionKey = JKeyType.BooleanKey().make("elInPositionKey");

    private static final Object TIMER_KEY = new Object();



    private JStatePublisherActor(TimerScheduler<JStatePublisherActor.StatePublisherMessage> timer, JCswContext cswCtx, HcdConfig hcdConfig, ActorRef plcioActor) {
        this.timer = timer;
        this.log = cswCtx.loggerFactory().getLogger(JCacheActor.class);
        this.currentStatePublisher = cswCtx.currentStatePublisher();
        this.cswCtx = cswCtx;
        this.plcioActor = plcioActor;
        this.hcdConfig = hcdConfig;

    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(JCswContext cswCtx, HcdConfig hcdConfig, ActorRef plcioActor) {
        return Behaviors.withTimers(timers -> {
            return (AbstractBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>)timers, cswCtx, hcdConfig, plcioActor);
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

        JPlcioActor.PlcioMessage readMessage = new JPlcioActor.ReadMessage(hcdConfig.telemetryTagItemValues);
        plcioActor.tell(readMessage);


        // example parameters for a current state

        Parameter azPosParam        = azPosKey.set(35.34).withUnits(degree);
        Parameter azPosErrorParam   = azPosErrorKey.set(0.34).withUnits(degree);
        Parameter elPosParam        = elPosKey.set(46.7).withUnits(degree);
        Parameter elPosErrorParam   = elPosErrorKey.set(0.03).withUnits(degree);
        Parameter azInPositionParam = azInPositionKey.set(false);
        Parameter elInPositionParam = elInPositionKey.set(true);

        Parameter timestamp = timestampKey.set(Instant.now());

        //create CurrentState and use sequential add
        CurrentState currentState = new CurrentState(prefix, new StateName("a state"))
                .add(azPosParam)
                .add(elPosParam)
                .add(azPosErrorParam)
                .add(elPosErrorParam)
                .add(azInPositionParam)
                .add(elInPositionParam)
                .add(timestamp);

        currentStatePublisher.publish(currentState);


    }


}
