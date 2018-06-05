package com.redhat.qcon.kafka.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.RecordMetadata;

@ProxyGen
@VertxGen
public interface KafkaService {

    static KafkaService create(Vertx vertx) {
        return new KafkaServiceImpl(vertx, vertx.getOrCreateContext().config());
    }

    static KafkaService createProxy(Vertx vertx, String address) {
        return new KafkaServiceVertxEBProxy(vertx, address);
    }

    void publish(JsonObject insult, Handler<AsyncResult<Void>> handler);
}
