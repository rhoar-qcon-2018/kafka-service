package com.redhat.qcon.kafka;

import com.redhat.qcon.kafka.services.KafkaService;
import com.redhat.qcon.kafka.services.KafkaServiceImpl;
import io.reactivex.Maybe;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.serviceproxy.ServiceBinder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);
    public static final String FAVORITES_EB_ADDRESS = "insult.favorites";

    Maybe<JsonObject> initConfigRetriever() {
        // Load the default configuration from the classpath
        LOG.info("Configuration store loading.");
        ConfigStoreOptions defaultOpts = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "default-kafka-config.json"));

        // Load container specific configuration from a specific file path inside of the container
        ConfigStoreOptions localConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "/opt/docker_config.json"))
                .setOptional(true);

        // When running inside of Kubernetes, configure the application to also load from a ConfigMap
        // This config is ONLY loaded when the environment variable KUBERNETES_NAMESPACE is set.
        ConfigStoreOptions confOpts = new ConfigStoreOptions()
                .setType("configmap")
                .setConfig(new JsonObject().put("name", "kafka-config"))
                .setOptional(true);

        ConfigStoreOptions sysProps = new ConfigStoreOptions()
                .setType("sys")
                .setOptional(true);

        // Add the default and container config options into the ConfigRetriever
        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
                .addStore(defaultOpts)
                .addStore(localConfig)
                .addStore(confOpts)
                .addStore(sysProps);

        // Create the ConfigRetriever and return the Maybe when complete
        return ConfigRetriever.create(vertx, retrieverOptions).rxGetConfig().toMaybe();
    }

    Maybe<JsonObject> loadKafkaService(JsonObject config) {
        LOG.info(config.encodePrettily());
        KafkaService kafkaService = new KafkaServiceImpl(vertx.getDelegate(), config);
        new ServiceBinder(vertx.getDelegate()).setAddress("kafka.service")
                .register(KafkaService.class, kafkaService);
        return Maybe.just(config);
    }

    Maybe<JsonObject> bridgeKafkaTopic(JsonObject config) {
        LOG.info(new JsonObject().encodePrettily());

        // Convert JSON config to Map<String, String>
        Map<String, String> cfg = config().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));

        KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, cfg);
        consumer.handler(
                r -> vertx.eventBus().publish(FAVORITES_EB_ADDRESS, new JsonObject(r.value()))
        );
        consumer.rxSubscribe("favorites")
            .subscribe(
                    () -> LOG.info("Message received from queue"),
                    e -> LOG.warn("Unable to process message", e)
            );
        return Maybe.just(config);
    }

    @Override
    public void start(Future<Void> startFuture) {
        this.initConfigRetriever()
            .flatMap(this::mergeConfig)
            .flatMap(this::loadKafkaService)
            .flatMap(this::bridgeKafkaTopic)
            .doOnError(startFuture::fail)
            .subscribe(c -> startFuture.complete());
    }

    @NotNull
    private Maybe<JsonObject> mergeConfig(JsonObject config) {
        return Maybe.just(config().mergeIn(config));
    }
}