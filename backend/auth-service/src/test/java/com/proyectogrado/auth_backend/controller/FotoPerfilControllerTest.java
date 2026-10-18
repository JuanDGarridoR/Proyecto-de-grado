package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.FotoPerfilStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Foto de perfil (RF-61): cualquier rol la sube, solo se aceptan imágenes reales y
 * al reemplazarla se borra la anterior de Storage.
 */
class FotoPerfilControllerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final String URL_NUEVA = "40/0f8fad5b-d9cb-469f-a165-70867728950e.jpeg";
    private static final String URL_ANTERIOR = "40/9b2c1a3e-0000-4000-8000-000000000000.jpeg";

    private UsuarioRepository usuarioRepository;
    private FotoPerfilStorage storage;
    private MockMvc mockMvc;
    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        storage = mock(FotoPerfilStorage.class);

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(40, "PERSONA_MAYOR");

        usuario = new Usuario();
        usuario.setIdUsuario(40);
        usuario.setNombreUsuario("Rosa Díaz");
        usuario.setFotoPerfil(URL_ANTERIOR);

        when(usuarioRepository.findById(40)).thenReturn(Optional.of(usuario));
        when(storage.estaConfigurado()).thenReturn(true);
        when(storage.subir(eq(40), any(), any())).thenReturn(URL_NUEVA);

        mockMvc = MockMvcBuilders.standaloneSetup(new FotoPerfilController(
                usuarioRepository, jwtService, storage)).build();
    }

    private static MockMultipartFile foto(byte[] bytes) {
        return new MockMultipartFile("foto", "foto.jpg", "image/jpeg", bytes);
    }

    @Test
    void subirGuardaLaUrlNuevaYBorraLaAnterior() throws Exception {
        mockMvc.perform(multipart("/api/auth/informacion/foto").file(foto(JPEG))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfil").value(URL_NUEVA));

        assertEquals(URL_NUEVA, usuario.getFotoPerfil());
        verify(usuarioRepository).save(usuario);
        verify(storage).subir(40, JPEG, MediaType.IMAGE_JPEG);
        verify(storage).borrar(URL_ANTERIOR);
    }

    @Test
    void subirRechazaUnArchivoQueNoEsImagenAunqueDigaSerlo() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        mockMvc.perform(multipart("/api/auth/informacion/foto").file(foto(html))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La imagen debe ser JPG, PNG o WebP"));

        verify(storage, never()).subir(anyInt(), any(), any());
    }

    @Test
    void siLaSubidaNoEstaConfiguradaNoSeIntenta() throws Exception {
        when(storage.estaConfigurado()).thenReturn(false);

        mockMvc.perform(multipart("/api/auth/informacion/foto").file(foto(JPEG))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());

        verify(storage, never()).subir(anyInt(), any(), any());
    }

    @Test
    void siStorageFallaSeConservaLaFotoAnterior() throws Exception {
        when(storage.subir(eq(40), any(), any())).thenThrow(new RuntimeException("caído"));

        mockMvc.perform(multipart("/api/auth/informacion/foto").file(foto(JPEG))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway());

        assertEquals(URL_ANTERIOR, usuario.getFotoPerfil());
        verify(storage, never()).borrar(any());
    }

    @Test
    void quitarBorraLaFotoYDejaLaUrlEnNull() throws Exception {
        mockMvc.perform(delete("/api/auth/informacion/foto").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfil").doesNotExist());

        assertNull(usuario.getFotoPerfil());
        verify(storage).borrar(URL_ANTERIOR);
    }

    @Test
    void reconoceLosTiposDeImagenPorSusPrimerosBytes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertEquals(MediaType.IMAGE_JPEG, FotoPerfilStorage.tipoDeImagen(JPEG));
        assertEquals(MediaType.IMAGE_PNG, FotoPerfilStorage.tipoDeImagen(png));
        assertEquals("image/webp", FotoPerfilStorage.tipoDeImagen(webp).toString());
        assertNull(FotoPerfilStorage.tipoDeImagen("GIF89a".getBytes()));
    }
}
