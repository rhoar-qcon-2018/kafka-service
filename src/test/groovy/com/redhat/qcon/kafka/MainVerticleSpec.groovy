package com.redhat.qcon.kafka

import com.redhat.qcon.kafka.services.KafkaService
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.testcontainers.containers.KafkaContainer
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

class MainVerticleSpec extends Specification {

    @Shared
    KafkaContainer kafka

    def setupSpec() {
        if (System.getenv('OPENSHIFT_BUILD_SOURCE') != null && System.getenv('OPENSHIFT_BUILD_SOURCE').trim() != '') {
            System.setProperty('bootstrap.servers', System.getenv('OPENSHIFT_KAFKA_BOOTSTRAP'))
            println('Building Inside OpenShift, Cannot use TestContainers')
        } else {
            println('Building Locally, using TestContainers')
            kafka = new KafkaContainer().withEmbeddedZookeeper()
            kafka.start()

            System.setProperty('bootstrap.servers', kafka.getBootstrapServers())
            println(System.getProperty('bootstrap.servers'))
        }
    }

    def 'Test MainVerticle deployment'() {
        given: 'An instance of Vert.x'
            def vertx = Vertx.vertx()
        and: 'A message to be published'
            def message = new JsonObject().put('Test', 'true').put('uuid', UUID.randomUUID().toString())
        and: 'An instance of AsyncConditions'
            def async = new AsyncConditions(2)
        when: 'We deploy MainVerticle, then set up a consumer, and then publish to the queue'
            vertx.deployVerticle(MainVerticle.class.getCanonicalName(), { res ->
                println('Completed deployment')
                async.evaluate {
                    res.succeeded()
                }

                vertx.eventBus().consumer(MainVerticle.FAVORITES_EB_ADDRESS, { m ->
                    println('Completed receipt of message')
                    async.evaluate {
                        m.body().getString('Test') == 'true'
                        m.body().getString('uuid') == message.getString('uuid')
                    }
                })

                KafkaService.createProxy(vertx, 'kafka.service').publish(message, { res1 ->
                    println('Completed publish of message')
                })
            })
        then: 'Expect a successful deployment and successful message exchange'
            async.await(30)
    }
}