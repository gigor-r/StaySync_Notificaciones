package com.staysync.notificaciones.messaging;

import com.staysync.notificaciones.config.RabbitMQConfig;
import com.staysync.notificaciones.messaging.event.ReservaCreadaEvent;
import com.staysync.notificaciones.messaging.event.ReservaEstadoCambiadoEvent;
import com.staysync.notificaciones.messaging.event.UsuarioRegistradoEvent;
import com.staysync.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha las 3 colas de notificación declaradas en RabbitMQConfig.
 *
 * Flujo de errores (acknowledge-mode: auto):
 *   - Si el servicio lanza excepción → Spring AMQP reintenta (max-attempts: 3).
 *   - Si los reintentos se agotan   → el mensaje va a la DLQ correspondiente.
 *   - Si el evento es inválido/irrecuperable → AmqpRejectAndDontRequeueException
 *     descarta el mensaje directamente a la DLQ sin reintentar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacionEventListener {

    private final NotificacionService notificacionService;

    @RabbitListener(queues = RabbitMQConfig.Q_USUARIO_REGISTRO)
    public void onUsuarioRegistrado(UsuarioRegistradoEvent evento) {
        log.info("[usuario.registro] userId={} email={}", evento.usuarioId(), evento.email());

        validarEmail(evento.email(), "usuario.registro");

        try {
            notificacionService.procesarRegistroUsuario(evento);
            log.info("[usuario.registro] OK userId={}", evento.usuarioId());
        } catch (Exception e) {
            log.error("[usuario.registro] FALLO userId={} error={}", evento.usuarioId(), e.getMessage());
            throw e; // Spring reintenta → DLQ tras agotar intentos
        }
    }

    @RabbitListener(queues = RabbitMQConfig.Q_RESERVA_CREADA)
    public void onReservaCreada(ReservaCreadaEvent evento) {
        log.info("[reserva.creada] reservaId={} email={}", evento.reservaId(), evento.email());

        validarEmail(evento.email(), "reserva.creada");

        try {
            notificacionService.procesarReservaCreada(evento);
            log.info("[reserva.creada] OK reservaId={}", evento.reservaId());
        } catch (Exception e) {
            log.error("[reserva.creada] FALLO reservaId={} error={}", evento.reservaId(), e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.Q_RESERVA_ESTADO)
    public void onReservaEstadoCambiado(ReservaEstadoCambiadoEvent evento) {
        log.info("[reserva.estado] reservaId={} estado={} email={}",
                evento.reservaId(), evento.estadoNuevo(), evento.email());

        validarEmail(evento.email(), "reserva.estado");

        try {
            notificacionService.procesarCambioEstado(evento);
            log.info("[reserva.estado] OK reservaId={}", evento.reservaId());
        } catch (Exception e) {
            log.error("[reserva.estado] FALLO reservaId={} error={}", evento.reservaId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Rechaza el mensaje inmediatamente a DLQ si el email está vacío.
     * Usar AmqpRejectAndDontRequeueException para errores irrecuperables
     * (datos inválidos que nunca van a funcionar aunque se reintenten).
     */
    private void validarEmail(String email, String cola) {
        if (email == null || email.isBlank()) {
            String msg = "[" + cola + "] Evento sin email — descartado a DLQ sin reintentar";
            log.warn(msg);
            throw new AmqpRejectAndDontRequeueException(msg);
        }
    }
}
