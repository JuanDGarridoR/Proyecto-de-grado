package com.proyectogrado.mensajeria_backend.controller;

import com.proyectogrado.mensajeria_backend.model.UsuarioLookup;
import com.proyectogrado.mensajeria_backend.repository.UsuarioLookupRepository;
import com.proyectogrado.mensajeria_backend.security.TextBeeOtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de envío y verificación de códigos OTP. TextBeeOtpService está
 * simulado: aquí se prueba qué responde cada endpoint según lo que pase al
 * enviar o verificar.
 */
class OtpControllerTest {

    private TextBeeOtpService textBeeOtpService;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        textBeeOtpService = mock(TextBeeOtpService.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        // Por defecto el celular tiene cuenta.
        when(usuarioLookupRepository.findFirstByCelular(anyString())).thenReturn(Optional.of(mock(UsuarioLookup.class)));
        mockMvc = MockMvcBuilders.standaloneSetup(new OtpController(textBeeOtpService, usuarioLookupRepository)).build();
    }

    @Test
    void aUnCelularSinCuentaNoSeLeEnviaNadaPeroLaRespuestaEsLaMisma() throws Exception {
        when(usuarioLookupRepository.findFirstByCelular("+573009998877")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573009998877\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Código OTP enviado correctamente"));

        verify(textBeeOtpService, never()).enviarCodigo(anyString());
    }

    @Test
    void elCelularSeBuscaSinEspacios() throws Exception {
        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \" +57 300 111 2233 \"}"))
                .andExpect(status().isOk());

        verify(usuarioLookupRepository).findFirstByCelular("+573001112233");
        verify(textBeeOtpService).enviarCodigo("+573001112233");
    }

    @Test
    void enviarElCodigoRespondeOk() throws Exception {
        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573001112233\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(textBeeOtpService).enviarCodigo("+573001112233");
    }

    @Test
    void sinCelularNoSeEnviaNada() throws Exception {
        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El número de celular es obligatorio"));

        verify(textBeeOtpService, never()).enviarCodigo(anyString());
    }

    @Test
    void pedirOtroCodigoMuyProntoResponde429() throws Exception {
        doThrow(new IllegalStateException("Espera unos segundos antes de pedir otro código"))
                .when(textBeeOtpService).enviarCodigo("+573001112233");

        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573001112233\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void siTextBeeFallaRespondeErrorSinDetallesInternos() throws Exception {
        doThrow(new RuntimeException("TextBee respondió 500"))
                .when(textBeeOtpService).enviarCodigo("+573001112233");

        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573001112233\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("No fue posible enviar el código OTP"));
    }

    @Test
    void unCodigoValidoSeConfirma() throws Exception {
        when(textBeeOtpService.verificarCodigo("+573001112233", "123456")).thenReturn(true);

        mockMvc.perform(post("/api/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573001112233\", \"code\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void unCodigoIncorrectoResponde401() throws Exception {
        when(textBeeOtpService.verificarCodigo("+573001112233", "000000")).thenReturn(false);

        mockMvc.perform(post("/api/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573001112233\", \"code\": \"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sinCodigoNoSeVerifica() throws Exception {
        mockMvc.perform(post("/api/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+573001112233\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código OTP es obligatorio"));
    }
}
