package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.service.FotoPerfilStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Muestra las fotos de perfil, que están en un bucket privado. Solo con
 * sesión iniciada (como todo /api/auth salvo el login): si alguien comparte
 * el enlace, fuera de Vita+ no abre. La ruta incluye un uuid que no se puede
 * adivinar, así que tampoco sirve para recorrer las fotos de todos.
 *
 * El navegador las pide con el token y las muestra desde memoria (ver
 * fotoPerfil en el frontend), porque una etiqueta img no puede mandar el token.
 */
@RestController
@RequestMapping("/api/auth/fotos")
public class FotoController {

    private final FotoPerfilStorage fotoPerfilStorage;

    public FotoController(FotoPerfilStorage fotoPerfilStorage) {
        this.fotoPerfilStorage = fotoPerfilStorage;
    }

    /** La foto, o 404 si la ruta no es válida o no existe. */
    @GetMapping("/{carpeta}/{archivo}")
    public ResponseEntity<byte[]> foto(@PathVariable String carpeta, @PathVariable String archivo) {
        return fotoPerfilStorage.descargar(carpeta + "/" + archivo)
                .map(foto -> ResponseEntity.ok()
                        .contentType(foto.tipo())
                        // private: solo la guarda el navegador de quien la pidió, no un proxy.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                        .body(foto.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
