package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.dto.GustoRequest;
import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorGustoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El catálogo de gustos va en caché: pedirlo varias veces consulta la base
 * una sola vez, y al agregar un gusto la caché se borra para que aparezca.
 */
@SpringJUnitConfig(GustoCacheTest.Config.class)
class GustoCacheTest {

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("gustos");
        }

        @Bean
        GustoRepository gustoRepository() {
            return mock(GustoRepository.class);
        }

        @Bean
        GustoController gustoController(GustoRepository gustoRepository) {
            return new GustoController(gustoRepository, mock(PersonaMayorGustoRepository.class));
        }
    }

    @Autowired
    private GustoController controller;

    @Autowired
    private GustoRepository gustoRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("gustos").clear();
        clearInvocations(gustoRepository);
        when(gustoRepository.findAll()).thenReturn(List.of(new Gusto("Ajedrez", "Juegos")));
        when(gustoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void pedirElCatalogoVariasVecesConsultaLaBaseUnaSolaVez() {
        controller.listar(null);
        controller.listar(null);
        controller.listar(null);

        verify(gustoRepository, times(1)).findAll();
    }

    @Test
    void agregarUnGustoBorraLaCache() {
        controller.listar(null);

        GustoRequest nuevo = new GustoRequest();
        nuevo.setNombre("Pintura");
        nuevo.setCategoria("Arte");
        controller.crear(nuevo);

        controller.listar(null);

        verify(gustoRepository, times(2)).findAll();
    }
}
