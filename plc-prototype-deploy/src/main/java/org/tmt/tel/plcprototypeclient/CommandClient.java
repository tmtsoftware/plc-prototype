package org.tmt.tel.plcprototypeclient;

import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.event.api.javadsl.IEventService;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
import csw.location.client.ActorSystemFactory;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.location.server.commons.ClusterAwareSettings;
import csw.logging.internal.LoggingSystem;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.logging.javadsl.JLoggingSystemFactory;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.Id;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;

import scala.concurrent.duration.FiniteDuration;


import java.net.InetAddress;
import java.time.Duration;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.TimeUnit;

import static csw.location.api.javadsl.JComponentType.Assembly;


public class CommandClient {

    Prefix source;
    ActorSystem system;
    ILocationService locationService;
    public static ILogger log;
    Optional<ICommandService> commandServiceOptional;
    IEventService eventService;


    public CommandClient(Prefix source, ActorSystem system, ILocationService locationService) throws Exception {
        this.source = source;
        this.system = system;
        this.locationService = locationService;
        commandServiceOptional = getAssemblyBlocking();
    }


    private Connection.AkkaConnection assemblyConnection = new Connection.AkkaConnection(new ComponentId("JPlcprototypeAssembly", Assembly));


    /**
     * Gets a reference to the running assembly from the location service, if found.
     */

    private Optional<ICommandService> getAssemblyBlocking() throws Exception {

        Duration waitForResolveLimit = Duration.ofSeconds(1);

        System.out.println("assemblyConnection = " + assemblyConnection);

        Optional<AkkaLocation> resolveResult = locationService.resolve(assemblyConnection, waitForResolveLimit).get();

        if (resolveResult.isPresent()) {

            AkkaLocation akkaLocation = resolveResult.get();

            return Optional.of(CommandServiceFactory.jMake(resolveResult.get(),Adapter.toTyped(system)));

        } else {
            return Optional.empty();
        }
    }


    /**
     * Sends a read message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse.SubmitResponse> read(Optional<ObsId> obsId, String[] argSets) {

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("readPlc"), obsId);

            for (String arg : argSets) {
                // separate name and type
                String arr[] = arg.split(" ", 2);

                String name = arr[0];
                String type = arr[1];

                switch (type) {
                    case "String":
                        setup = setup.add(JKeyType.StringKey().make(name).set(""));
                        break;
                    case "Int":
                        setup = setup.add(JKeyType.IntKey().make(name).set(0));
                        break;
                    case "Float":
                        setup = setup.add(JKeyType.FloatKey().make(name).set(0.0f));
                        break;
                    default:
                        break;

                }

            }

            log.info("Submitting read command to assembly: " + setup);

            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }
    /**
     * Sends a write message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse.SubmitResponse> write(Optional<ObsId> obsId, String[] argSets) {

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;


            Setup setup = new Setup(source, new CommandName("writePlc"), obsId);

            for (String arg : argSets) {
                // separate name and type
                String arr[] = arg.split(" ", 3);

                String name = arr[0];
                String type = arr[1];
                String value = arr[2];

                switch (type) {
                    case "String":
                        setup = setup.add(JKeyType.StringKey().make(name).set(value));
                        break;
                    case "Int":
                        setup = setup.add(JKeyType.IntKey().make(name).set(new Integer(value)));
                        break;
                    case "Float":
                        setup = setup.add(JKeyType.FloatKey().make(name).set(new Float(value)));
                        break;
                    default:
                        break;

                }

            }

            log.debug("Submitting read command to assembly...");

            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }

    }


    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        //ActorSystem system = ClusterAwareSettings.system();
        //Materializer mat = ActorMaterializer.create(system);
        //ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);


        ActorSystem system = ActorSystemFactory.remote();
        ActorMaterializer mat = ActorMaterializer.create(system);
        ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);



        CommandClient encClient = new CommandClient(new Prefix("command-client"), system, locationService);

        Optional<ObsId> maybeObsId = Optional.empty();
        String hostName = InetAddress.getLocalHost().getHostName();
        LoggingSystem loggingSystem = JLoggingSystemFactory.start("CommandClient", "0.1", hostName, system);
        log = new JLoggerFactory("client-app").getLogger(CommandClient.class);

        log.info(() -> "TCS Client Starting..");

        boolean keepRunning = true;
        while (keepRunning) {
            log.info(() -> "Type command name [read [var-name type, ...], write [var-name type value, ...]] or type 'exit' to stop client.  \"type\" can be String, Int, Float");

            String command = scanner.nextLine();

            String arr[] = command.split(" ", 2);

            System.out.println("arr[1] = " + arr[1]);

            switch (arr[0]) {
                case "read":
                    log.info(() -> "Commanding read: ");
                    String[] argSets = arr[1].split(",");
                    CompletableFuture<CommandResponse.SubmitResponse> readCmdResponse = encClient.read(maybeObsId, argSets);
                    CommandResponse respReadCmd = readCmdResponse.get();
                    log.info(() -> "Read cmd response: " + respReadCmd);
                    break;
                case "write":
                    log.info(() -> "Commanding write: ");
                    String[] writeInfo = arr[1].split(",");
                    CompletableFuture<CommandResponse.SubmitResponse> writeCmdResponse = encClient.write(maybeObsId, writeInfo);
                    CommandResponse respWriteCmd = writeCmdResponse.get();
                    log.info(() -> "Write cmd response: " + respWriteCmd);
                    break;
                case "exit":
                    keepRunning = false;
                    break;
                default:
                    log.info(arr[0] + "   - Is not a valid choice");
            }
        }

        Done done = loggingSystem.javaStop().get();
        system.terminate();

    }


}






