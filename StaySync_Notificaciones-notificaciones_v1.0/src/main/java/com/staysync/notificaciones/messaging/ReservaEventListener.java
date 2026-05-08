package com.staysync.notificaciones.messaging;

import com.staysync.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaEventListener {

    private final NotificacionService notificacionService;

    @RabbitListener(queues = "q.notificacion.email")
    public void onEvento(Map<String, Object> evento) {
        try {
            String routingKey = (String) evento.getOrDefault("_routingKey", "");
            Long usuarioId = Long.valueOf(evento.getOrDefault("usuarioId", "0").toString());
            String email = (String) evento.getOrDefault("email", "");

            if (email.isBlank()) {
                log.warn("Evento sin email de destinatario: {}", evento);
                return;
            }

            String codigoPlantilla = resolverPlantilla(routingKey);
            notificacionService.procesarEvento(codigoPlantilla, usuarioId, email, evento);

            log.info("Notificación procesada para evento: {} → {}", routingKey, email);
        } catch (Exception e) {
            log.error("Error procesando evento de notificación: {}", e.getMessage());
        }
    }

    private String resolverPlantilla(String routingKey) {
        return switch (routingKey) {
            case "reserva.confirmada" -> "RESERVA_CONFIRMADA";
            case "reserva.cancelada"  -> "RESERVA_CANCELADA";
            case "pago.completado"    -> "PAGO_EXITOSO";
            case "reserva.checkin"    -> "CHECKIN_RECORDATORIO";
            default -> "NOTIFICACION_GENERICA";
        };
    }
}
