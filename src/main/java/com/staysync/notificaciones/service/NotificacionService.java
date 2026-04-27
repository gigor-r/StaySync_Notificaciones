package com.staysync.notificaciones.service;

import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.Canal;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import com.staysync.notificaciones.model.PlantillaNotificacion;
import com.staysync.notificaciones.repository.NotificacionRepository;
import com.staysync.notificaciones.repository.PlantillaNotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final PlantillaNotificacionRepository plantillaRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public void procesarEvento(String codigoPlantilla, Long usuarioId,
                               String destinatario, Map<String, Object> variables) {
        PlantillaNotificacion plantilla = plantillaRepository.findByCodigo(codigoPlantilla)
                .orElse(null);

        String asunto = plantilla != null ? renderizar(plantilla.getAsunto(), variables) : "Notificación StaySync";
        String cuerpo = plantilla != null ? renderizar(plantilla.getCuerpo(), variables)
                : variables.toString();

        Notificacion notificacion = Notificacion.builder()
                .plantilla(plantilla)
                .usuarioId(usuarioId)
                .canal(Canal.EMAIL)
                .destinatario(destinatario)
                .asunto(asunto)
                .cuerpo(cuerpo)
                .estado(EstadoNotificacion.PENDIENTE)
                .build();

        notificacion = notificacionRepository.save(notificacion);
        enviarEmail(notificacion);
    }

    @Transactional
    public void enviarEmail(Notificacion notificacion) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(notificacion.getDestinatario());
            mensaje.setSubject(notificacion.getAsunto());
            mensaje.setText(notificacion.getCuerpo());
            mensaje.setFrom("noreply@staysync.com");

            mailSender.send(mensaje);

            notificacion.setEstado(EstadoNotificacion.ENVIADO);
            notificacion.setEnviadoEn(LocalDateTime.now());
            log.info("Email enviado a: {}", notificacion.getDestinatario());
        } catch (Exception e) {
            notificacion.setIntentos(notificacion.getIntentos() + 1);
            notificacion.setErrorMsg(e.getMessage());
            if (notificacion.getIntentos() >= notificacion.getMaxIntentos()) {
                notificacion.setEstado(EstadoNotificacion.FALLIDO);
                log.error("Email fallido (máx. intentos) para: {}", notificacion.getDestinatario());
            }
            log.warn("Error enviando email (intento {}): {}", notificacion.getIntentos(), e.getMessage());
        }
        notificacionRepository.save(notificacion);
    }

    private String renderizar(String plantilla, Map<String, Object> variables) {
        if (plantilla == null) return "";
        String resultado = plantilla;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            resultado = resultado.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return resultado;
    }
}
