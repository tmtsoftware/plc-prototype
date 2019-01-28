package org.tmt.tel.plcprototypeclient;


import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.util.Timeout;

import csw.command.client.CommandServiceFactory;
import csw.command.client.internal.JCommandServiceImpl;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
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
import csw.command.api.javadsl.ICommandService;

import java.time.Duration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


import static csw.location.api.javadsl.JComponentType.Assembly;


public class PlcPrototypeClient {

    Prefix source;
    ActorSystem system;
    ILocationService locationService;

    public PlcPrototypeClient(Prefix source, ActorSystem system, ILocationService locationService) throws Exception {

        this.source = source;
        this.system = system;
        this.locationService = locationService;

        commandServiceOptional = getAssemblyBlocking();
    }

    Optional<ICommandService> commandServiceOptional = Optional.empty();

    private Connection.AkkaConnection assemblyConnection = new Connection.AkkaConnection(new ComponentId("TcstemplatejavaAssembly", Assembly));


    private Key<String> targetTypeKey = JKeyType.StringKey().make("targetType");
    private Key<Double> wavelengthKey = JKeyType.DoubleKey().make("wavelength");
    private Key<String> axesKey = JKeyType.StringKey().make("axes");
    private Key<Double> azKey = JKeyType.DoubleKey().make("az");
    private Key<Double> elKey = JKeyType.DoubleKey().make("el");



    /**
     * Gets a reference to the running assembly from the location service, if found.
     */

    private Optional<ICommandService> getAssemblyBlocking() throws Exception {

        Duration duration = Duration.ofSeconds(20);


        Optional<AkkaLocation> resolveResult = locationService.resolve(assemblyConnection, duration).get();

        if (resolveResult.isPresent()) {

            AkkaLocation akkaLocation = resolveResult.get();

            return Optional.of(CommandServiceFactory.jMake(akkaLocation, Adapter.toTyped(system)));

        } else {
            return Optional.empty();
        }
    }


    public void sendWritePlcCommand() {

     Setup setupCommand = new Setup(new Prefix("client"), new CommandName("writePlc"), Optional.of(new ObsId("Obs001")));
        Parameter intParam = JKeyType.IntKey().make("myRealValue").set(new Integer("3333"));

        setupCommand = setupCommand.add(intParam);



     CompletableFuture<CommandResponse.SubmitResponse> immediateCommandF =
            commandServiceOptional.get()
                    .submit(setupCommand, new Timeout(20, TimeUnit.SECONDS))
                    .thenApply(
                    response -> {
                        if (response instanceof CommandResponse.Completed) {
                            System.out.println("response = " + response);
                        } else {
                            System.out.println("error = " + response);
                        }
                        return response;
                    }
            );

    }
}
