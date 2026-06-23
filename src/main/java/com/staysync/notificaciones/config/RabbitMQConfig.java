package com.staysync.notificaciones.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Exchanges ─────────────────────────────────────────────────────────────
    public static final String EXCHANGE     = "staysync.notificaciones";
    public static final String DLX_EXCHANGE = "staysync.notificaciones.dlx";

    // ── Routing keys ──────────────────────────────────────────────────────────
    public static final String RK_USUARIO_REGISTRO = "usuario.registro";
    public static final String RK_RESERVA_CREADA   = "reserva.creada";
    public static final String RK_RESERVA_ESTADO   = "reserva.estado";

    // ── Queues ────────────────────────────────────────────────────────────────
    public static final String Q_USUARIO_REGISTRO     = "q.notificacion.usuario.registro";
    public static final String Q_RESERVA_CREADA       = "q.notificacion.reserva.creada";
    public static final String Q_RESERVA_ESTADO       = "q.notificacion.reserva.estado";
    public static final String Q_USUARIO_REGISTRO_DLQ = "q.notificacion.usuario.registro.dlq";
    public static final String Q_RESERVA_CREADA_DLQ   = "q.notificacion.reserva.creada.dlq";
    public static final String Q_RESERVA_ESTADO_DLQ   = "q.notificacion.reserva.estado.dlq";

    // ── Exchanges beans ───────────────────────────────────────────────────────
    @Bean
    TopicExchange notificacionesExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    // ── Queue beans (con DLQ configurada) ────────────────────────────────────
    @Bean
    Queue queueUsuarioRegistro() {
        return QueueBuilder.durable(Q_USUARIO_REGISTRO)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Q_USUARIO_REGISTRO_DLQ)
                .build();
    }

    @Bean
    Queue queueReservaCreada() {
        return QueueBuilder.durable(Q_RESERVA_CREADA)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Q_RESERVA_CREADA_DLQ)
                .build();
    }

    @Bean
    Queue queueReservaEstado() {
        return QueueBuilder.durable(Q_RESERVA_ESTADO)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", Q_RESERVA_ESTADO_DLQ)
                .build();
    }

    @Bean Queue queueUsuarioRegistroDlq() { return QueueBuilder.durable(Q_USUARIO_REGISTRO_DLQ).build(); }
    @Bean Queue queueReservaCreadaDlq()   { return QueueBuilder.durable(Q_RESERVA_CREADA_DLQ).build(); }
    @Bean Queue queueReservaEstadoDlq()   { return QueueBuilder.durable(Q_RESERVA_ESTADO_DLQ).build(); }

    // ── Bindings principales ──────────────────────────────────────────────────
    @Bean
    Binding bindingUsuarioRegistro(Queue queueUsuarioRegistro, TopicExchange notificacionesExchange) {
        return BindingBuilder.bind(queueUsuarioRegistro).to(notificacionesExchange).with(RK_USUARIO_REGISTRO);
    }

    @Bean
    Binding bindingReservaCreada(Queue queueReservaCreada, TopicExchange notificacionesExchange) {
        return BindingBuilder.bind(queueReservaCreada).to(notificacionesExchange).with(RK_RESERVA_CREADA);
    }

    @Bean
    Binding bindingReservaEstado(Queue queueReservaEstado, TopicExchange notificacionesExchange) {
        return BindingBuilder.bind(queueReservaEstado).to(notificacionesExchange).with(RK_RESERVA_ESTADO);
    }

    // ── Bindings DLQ ─────────────────────────────────────────────────────────
    @Bean
    Binding bindingUsuarioRegistroDlq(Queue queueUsuarioRegistroDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(queueUsuarioRegistroDlq).to(dlxExchange).with(Q_USUARIO_REGISTRO_DLQ);
    }

    @Bean
    Binding bindingReservaCreadaDlq(Queue queueReservaCreadaDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(queueReservaCreadaDlq).to(dlxExchange).with(Q_RESERVA_CREADA_DLQ);
    }

    @Bean
    Binding bindingReservaEstadoDlq(Queue queueReservaEstadoDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(queueReservaEstadoDlq).to(dlxExchange).with(Q_RESERVA_ESTADO_DLQ);
    }

    // ── Serialización JSON ────────────────────────────────────────────────────
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
