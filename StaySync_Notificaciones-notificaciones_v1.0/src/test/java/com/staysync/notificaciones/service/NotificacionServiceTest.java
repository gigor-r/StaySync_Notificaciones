package com.staysync.notificaciones.service;

import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import com.staysync.notificaciones.model.PlantillaNotificacion;
import com.staysync.notificaciones.repository.NotificacionRepository;
import com.staysync.notificaciones.repository.PlantillaNotificacionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacionService - Tests Unitarios")
class NotificacionServiceTest {

    @Mock private NotificacionRepository notificacionRepository;
    @Mock private PlantillaNotificacionRepository plantillaRepository;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private NotificacionService notificacionService;

    private PlantillaNotificacion plantilla;

    @BeforeEach
    void setUp() {
        plantilla = PlantillaNotificacion.builder()
                .id(1L).codigo("RESERVA_CONFIRMADA")
                .nombre("Reserva Confirmada")
                .canal(Notificacion.Canal.EMAIL)
                .asunto("Confirmación de Reserva #{{codigo}}")
                .cuerpo("<h1>Hola {{nombre}}, tu reserva está confirmada.</h1>")
                .activa(true).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("procesarEvento() - debe crear notificación con plantilla y enviar email")
    void debeCrearNotificacionYEnviarEmail() {
        Map<String, Object> variables = Map.of(
                "codigo", "RES-001",
                "nombre", "Juan García",
                "email",  "juan@test.com"
        );

        Notificacion notificacionGuardada = Notificacion.builder()
                .id(1L).usuarioId(1L)
                .canal(Notificacion.Canal.EMAIL)
                .destinatario("juan@test.com")
                .asunto("Confirmación de Reserva #RES-001")
                .cuerpo("<h1>Hola Juan García, tu reserva está confirmada.</h1>")
                .estado(EstadoNotificacion.PENDIENTE)
                .intentos(0).maxIntentos(3)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(plantillaRepository.findByCodigo("RESERVA_CONFIRMADA")).thenReturn(Optional.of(plantilla));
        when(notificacionRepository.save(any())).thenReturn(notificacionGuardada);

        notificacionService.procesarEvento("RESERVA_CONFIRMADA", 1L, "juan@test.com", variables);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notificacionRepository, atLeast(1)).save(any(Notificacion.class));
    }

    @Test
    @DisplayName("procesarEvento() - debe funcionar sin plantilla (fallback)")
    void debeFuncionarSinPlantilla() {
        Map<String, Object> variables = Map.of("key", "value");

        Notificacion notif = Notificacion.builder()
                .id(2L).usuarioId(1L).canal(Notificacion.Canal.EMAIL)
                .destinatario("test@test.com").cuerpo("{key=value}")
                .estado(EstadoNotificacion.PENDIENTE).intentos(0).maxIntentos(3)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(plantillaRepository.findByCodigo("INEXISTENTE")).thenReturn(Optional.empty());
        when(notificacionRepository.save(any())).thenReturn(notif);

        assertThatNoException().isThrownBy(() ->
                notificacionService.procesarEvento("INEXISTENTE", 1L, "test@test.com", variables));
    }

    @Test
    @DisplayName("enviarEmail() - debe marcar como FALLIDO después de max intentos")
    void debeMarcarFallidoTrasMáxIntentos() {
        Notificacion notif = Notificacion.builder()
                .id(3L).usuarioId(1L).canal(Notificacion.Canal.EMAIL)
                .destinatario("fail@test.com").asunto("Test").cuerpo("Cuerpo")
                .estado(EstadoNotificacion.PENDIENTE).intentos(2).maxIntentos(3)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenReturn(notif);

        notificacionService.enviarEmail(notif);

        assertThat(notif.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(notif.getIntentos()).isEqualTo(3);
    }
}
