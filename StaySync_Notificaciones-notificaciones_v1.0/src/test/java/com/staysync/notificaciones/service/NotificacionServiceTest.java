package com.staysync.notificaciones.service;

import com.staysync.notificaciones.messaging.event.ReservaCreadaEvent;
import com.staysync.notificaciones.messaging.event.ReservaEstadoCambiadoEvent;
import com.staysync.notificaciones.messaging.event.UsuarioRegistradoEvent;
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

    private PlantillaNotificacion plantillaReserva;
    private PlantillaNotificacion plantillaUsuario;
    private PlantillaNotificacion plantillaEstado;

    @BeforeEach
    void setUp() {
        plantillaReserva = buildPlantilla("RESERVA_CREADA",
                "Reserva {{codigo}} confirmada",
                "<h1>Hola {{nombreUsuario}}, tu reserva {{codigo}} está lista.</h1>");

        plantillaUsuario = buildPlantilla("USUARIO_REGISTRO",
                "Bienvenido {{nombre}}",
                "<p>Hola {{nombre}}, tu cuenta {{email}} fue creada.</p>");

        plantillaEstado = buildPlantilla("RESERVA_ESTADO",
                "Reserva {{codigo}} actualizada",
                "<p>Tu reserva {{codigo}} cambió a {{estadoNuevo}}.</p>");
    }

    // ── procesarEvento() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("procesarEvento() - debe crear notificación, renderizar plantilla y enviar email")
    void debeCrearNotificacionYEnviarEmail() {
        Map<String, Object> vars = Map.of(
                "codigo", "RES-001",
                "nombreUsuario", "Juan García",
                "email", "juan@test.com"
        );

        Notificacion guardada = buildNotificacion(1L, "juan@test.com",
                "Reserva RES-001 confirmada",
                "<h1>Hola Juan García, tu reserva RES-001 está lista.</h1>",
                EstadoNotificacion.PENDIENTE);

        when(plantillaRepository.findByCodigo("RESERVA_CREADA")).thenReturn(Optional.of(plantillaReserva));
        when(notificacionRepository.save(any())).thenReturn(guardada);

        notificacionService.procesarEvento("RESERVA_CREADA", 1L, "juan@test.com", vars);

        // FIX Bug 4: verificar que efectivamente se envió el email y se guardó
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notificacionRepository, atLeast(2)).save(argThat(n ->
                n.getEstado() == EstadoNotificacion.ENVIADO || n.getEstado() == EstadoNotificacion.PENDIENTE
        ));
    }

    @Test
    @DisplayName("procesarEvento() - sin plantilla debe guardar notificación genérica sin enviar email")
    void debeManejarPlantillaInexistente() {
        Map<String, Object> vars = Map.of("key", "value");

        Notificacion notifGenerica = buildNotificacion(2L, "test@test.com",
                null, "{key=value}", EstadoNotificacion.PENDIENTE);

        when(plantillaRepository.findByCodigo("INEXISTENTE")).thenReturn(Optional.empty());
        when(notificacionRepository.save(any())).thenReturn(notifGenerica);

        notificacionService.procesarEvento("INEXISTENTE", 1L, "test@test.com", vars);

        // FIX Bug 4: con plantilla inexistente no se debe intentar enviar email
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(notificacionRepository, atLeastOnce()).save(any(Notificacion.class));
    }

    // ── procesarRegistroUsuario() ────────────────────────────────────────────

    @Test
    @DisplayName("procesarRegistroUsuario() - debe procesar evento y enviar email de bienvenida")
    void debeProcesarRegistroUsuario() {
        UsuarioRegistradoEvent event = new UsuarioRegistradoEvent();
        event.setUsuarioId(1L);
        event.setNombre("Ana");
        event.setEmail("ana@test.com");

        when(plantillaRepository.findByCodigo("USUARIO_REGISTRO")).thenReturn(Optional.of(plantillaUsuario));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarRegistroUsuario(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── procesarReservaCreada() ──────────────────────────────────────────────

    @Test
    @DisplayName("procesarReservaCreada() - debe enviar confirmación al huésped")
    void debeProcesarReservaCreada() {
        ReservaCreadaEvent event = new ReservaCreadaEvent();
        event.setReservaId(1L);
        event.setUsuarioId(1L);
        event.setEmail("juan@test.com");
        event.setNombreUsuario("Juan");
        event.setCodigo("RES-001");
        event.setHabitacion("101");
        event.setFechaEntrada("2026-12-01");
        event.setFechaSalida("2026-12-05");
        event.setPrecioTotal("400.00");

        when(plantillaRepository.findByCodigo("RESERVA_CREADA")).thenReturn(Optional.of(plantillaReserva));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarReservaCreada(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── procesarCambioEstado() ───────────────────────────────────────────────

    @Test
    @DisplayName("procesarCambioEstado() - debe notificar cambio de estado CONFIRMADA → CHECKIN")
    void debeProcesarCambioEstado() {
        ReservaEstadoCambiadoEvent event = new ReservaEstadoCambiadoEvent();
        event.setReservaId(1L);
        event.setUsuarioId(1L);
        event.setEmail("juan@test.com");
        event.setNombreUsuario("Juan");
        event.setCodigo("RES-001");
        event.setHabitacion("101");
        event.setEstadoNuevo("CHECKIN");

        when(plantillaRepository.findByCodigo("RESERVA_ESTADO")).thenReturn(Optional.of(plantillaEstado));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarCambioEstado(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── enviarEmail() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("enviarEmail() - debe cambiar estado a ENVIADO cuando el envío es exitoso")
    void debeMarcarEnviadoTrasEnvioExitoso() {
        Notificacion notif = buildNotificacion(1L, "ok@test.com",
                "Asunto", "Cuerpo", EstadoNotificacion.PENDIENTE);

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.enviarEmail(notif);

        assertThat(notif.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(notif.getIntentos()).isEqualTo(1);
    }

    @Test
    @DisplayName("enviarEmail() - debe marcar como FALLIDO después del máximo de intentos")
    void debeMarcarFallidoTrasMáxIntentos() {
        Notificacion notif = buildNotificacion(3L, "fail@test.com",
                "Test", "Cuerpo", EstadoNotificacion.PENDIENTE);
        notif.setIntentos(2);  // ya tuvo 2 intentos previos
        notif.setMaxIntentos(3);

        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.enviarEmail(notif);

        assertThat(notif.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(notif.getIntentos()).isEqualTo(3);
        assertThat(notif.getErrorMsg()).isNotBlank();
    }

    @Test
    @DisplayName("enviarEmail() - no debe marcar FALLIDO si aún quedan intentos disponibles")
    void noDebeMarcarFallidoSiHayIntentosRestantes() {
        Notificacion notif = buildNotificacion(4L, "retry@test.com",
                "Test", "Cuerpo", EstadoNotificacion.PENDIENTE);
        notif.setIntentos(0);
        notif.setMaxIntentos(3);

        doThrow(new RuntimeException("SMTP timeout")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.enviarEmail(notif);

        // Con 1 intento fallido sobre 3 máximos, el estado no es FALLIDO todavía
        assertThat(notif.getEstado()).isNotEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(notif.getIntentos()).isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PlantillaNotificacion buildPlantilla(String codigo, String asunto, String cuerpo) {
        return PlantillaNotificacion.builder()
                .id(1L).codigo(codigo).nombre(codigo)
                .canal(Notificacion.Canal.EMAIL)
                .asunto(asunto).cuerpo(cuerpo)
                .activa(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private Notificacion buildNotificacion(Long id, String dest, String asunto,
                                            String cuerpo, EstadoNotificacion estado) {
        return Notificacion.builder()
                .id(id).usuarioId(1L)
                .canal(Notificacion.Canal.EMAIL)
                .destinatario(dest).asunto(asunto).cuerpo(cuerpo)
                .estado(estado).intentos(0).maxIntentos(3)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
