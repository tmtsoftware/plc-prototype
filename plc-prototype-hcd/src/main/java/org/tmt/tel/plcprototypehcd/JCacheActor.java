package org.tmt.tel.plcprototypehcd;


import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache Actor.
 */
public class JCacheActor extends AbstractBehavior<JCacheActor.CacheMessage> {

    private ActorContext<CacheMessage> actorContext;
    JCswContext cswCtx;
    Map<String, TagItemValue> cache;
    private ILogger log;



    // add messages here
    interface CacheMessage {
    }

    public static final class UpdateMessage implements CacheMessage {
        public final TagItemValue[] tagItemValues;

        public UpdateMessage(TagItemValue[] tagItemValues) {
            this.tagItemValues = tagItemValues;
        }
    }

    public static final class ReadMessage implements CacheMessage {
        public final TagItemValue[] tagItemValues;

        public ReadMessage(TagItemValue[] tagItemValues) {
            this.tagItemValues = tagItemValues;
        }
    }



    private JCacheActor(ActorContext<JCacheActor.CacheMessage> actorContext, JCswContext cswCtx, Map cache) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JCacheActor.class);

        this.cache = cache;

        log.info("Cache Actor Created");
    }

    public static <CacheMessage> Behavior<CacheMessage> behavior(JCswContext cswCtx, Map cache) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<CacheMessage>) new JCacheActor((ActorContext<JCacheActor.CacheMessage>) ctx, cswCtx, cache);
        });
    }

    /**
     * This method receives messages sent to this worker actor.
     * @return
     */
    @Override
    public Receive<CacheMessage> createReceive() {

        ReceiveBuilder<CacheMessage> builder = receiveBuilder()
                .onMessage(UpdateMessage.class,
                        message -> {
                            log.debug(() -> "UpdateMessage Received");
                            Map cache = updateCache(message);
                            return behavior(cswCtx, cache);
                        })
                .onMessage(ReadMessage.class,
                        message -> {
                            log.debug(() -> "ReadMessage Received");
                            TagItemValue[] attributes = readCache(message);

                            // how do we return the value to the caller?

                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method processes updates to the cache.
     * @param updateMessage
     */
    private Map updateCache(UpdateMessage updateMessage) {

        // an attribute is a value that contains a String value, String type and String name
        // it also contains methods to extract Float, Int and Boolean values

        // if this.cache == null create it
        if (this.cache == null) {
            cache = new HashMap<String, TagItemValue>();
        }

        // update the cache with the values from the message
        for (TagItemValue attr : updateMessage.tagItemValues) {
            cache.put(attr.name, attr);
        }

        return cache;
    }

    /**
     * This method processes updates to the cache.
     * @param readMessage
     */
    private TagItemValue[] readCache(ReadMessage readMessage) {

        // populate the message attributes with the values from the cache
        // ignore attributes not in the cache

        for (TagItemValue attr : readMessage.tagItemValues) {
            TagItemValue cacheValue = cache.get(attr.name);
            if (cacheValue != null) {
                attr.value = cacheValue.value;
            }
        }


        return readMessage.tagItemValues;
    }


}
