package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.service.FotoPerfilStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fotos de perfil desde el bucket privado: auth-service las entrega (con
 * sesión: lo exige SecurityConfig, ver SinTokenValidoTest) y solo acepta
 * rutas de fotos, no cualquier archivo del bucket.
 */
class FotoControllerTest {

    private static final String RUTA = "40/0f8fad5b-d9cb-469f-a165-70867728950e.jpeg";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    private FotoPerfilStorage storage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        storage = mock(FotoPerfilStorage.class);
        when(storage.descargar(anyString())).thenReturn(Optional.empty());
        mockMvc = MockMvcBuilders.standaloneSetup(new FotoController(storage)).build();
    }

    @Test
    void entregaLaFotoConSuTipoYUnaCacheSoloDelNavegador() throws Exception {
        when(storage.descargar(RUTA)).thenReturn(Optional.of(new FotoPerfilStorage.Foto(JPEG, MediaType.IMAGE_JPEG)));

        mockMvc.perform(get("/api/auth/fotos/" + RUTA))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", "max-age=86400, private"));
    }

    @Test
    void unaFotoQueNoExisteResponde404() throws Exception {
        mockMvc.perform(get("/api/auth/fotos/40/9b2c1a3e-0000-4000-8000-000000000000.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloSonValidasLasRutasDeFotos() {
        assertTrue(FotoPerfilStorage.rutaValida(RUTA));
        assertTrue(FotoPerfilStorage.rutaValida("7/0f8fad5b-d9cb-469f-a165-70867728950e.webp"));

        assertFalse(FotoPerfilStorage.rutaValida(null));
        assertFalse(FotoPerfilStorage.rutaValida("40/../otro/archivo.jpeg"));
        assertFalse(FotoPerfilStorage.rutaValida("40/foto.jpeg"));
        assertFalse(FotoPerfilStorage.rutaValida("abc/0f8fad5b-d9cb-469f-a165-70867728950e.jpeg"));
        assertFalse(FotoPerfilStorage.rutaValida("40/0f8fad5b-d9cb-469f-a165-70867728950e.svg"));
        assertFalse(FotoPerfilStorage.rutaValida("https://x.supabase.co/storage/v1/object/public/fotos-perfil/" + RUTA));
    }
}
