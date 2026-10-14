package com.proyectogrado.donacion_service.controller;

import com.proyectogrado.donacion_service.model.Catalogo;
import com.proyectogrado.donacion_service.model.InventarioArticulo;
import com.proyectogrado.donacion_service.model.InventarioMovimiento;
import com.proyectogrado.donacion_service.repository.InventarioArticuloRepository;
import com.proyectogrado.donacion_service.repository.InventarioMovimientoRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import com.proyectogrado.donacion_service.service.InventarioService;
import com.proyectogrado.donacion_service.service.VinculosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inventario de la organización (RF-66 y RF-67): la cantidad solo cambia con movimientos,
 * una salida no puede dejarla negativa y no se duplican artículos.
 */
class InventarioControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;

    private InventarioArticuloRepository articuloRepository;
    private InventarioMovimientoRepository movimientoRepository;
    private MockMvc mockMvc;
    private InventarioArticulo leche;

    @BeforeEach
    void setUp() {
        articuloRepository = mock(InventarioArticuloRepository.class);
        movimientoRepository = mock(InventarioMovimientoRepository.class);
        VinculosService vinculosService = mock(VinculosService.class);

        when(vinculosService.idOrganizacionDeCuenta(CUENTA_ORGANIZACION)).thenReturn(ORGANIZACION);
        when(articuloRepository.save(any(InventarioArticulo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articuloRepository.findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        leche = new InventarioArticulo(ORGANIZACION, "Leche", "ALIMENTOS", "litros");
        ReflectionTestUtils.setField(leche, "idInventario", 3);
        leche.sumar(new BigDecimal("6"), null);
        when(articuloRepository.findById(3)).thenReturn(Optional.of(leche));

        mockMvc = MockMvcBuilders.standaloneSetup(new InventarioController(
                articuloRepository, movimientoRepository, mock(UsuarioLookupRepository.class), vinculosService,
                new InventarioService(articuloRepository, movimientoRepository))).build();
    }

    @Test
    void crearConCantidadInicialRegistraLaPrimeraEntrada() throws Exception {
        mockMvc.perform(post("/api/organizacion/inventario")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": " Pañales ", "categoria": "ASEO", "unidad": "paquetes",
                                 "cantidad": 12, "cantidadMinima": 5, "motivoEntrada": "COMPRA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pañales"))
                .andExpect(jsonPath("$.cantidad").value(12));

        ArgumentCaptor<InventarioMovimiento> movimiento = ArgumentCaptor.forClass(InventarioMovimiento.class);
        verify(movimientoRepository).save(movimiento.capture());
        assertEquals("COMPRA", movimiento.getValue().getMotivo());
        assertEquals(Catalogo.ENTRADA, movimiento.getValue().getTipo());
    }

    @Test
    void noSeDuplicaUnArticuloConElMismoNombreYUnidad() throws Exception {
        when(articuloRepository.findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                ORGANIZACION, "ALIMENTOS", "leche", "litros")).thenReturn(Optional.of(leche));

        mockMvc.perform(post("/api/organizacion/inventario")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "leche", "categoria": "ALIMENTOS", "unidad": "litros", "cantidad": 2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        "Ya tienes leche (litros) en el inventario: registra una entrada en ese artículo"));
    }

    @Test
    void unaSalidaRestaYQuedaEnElHistorial() throws Exception {
        mockMvc.perform(post("/api/organizacion/inventario/3/movimientos")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "SALIDA", "cantidad": 2.5, "motivo": "ENTREGA", "detalle": "Desayuno del lunes"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(3.5));

        ArgumentCaptor<InventarioMovimiento> movimiento = ArgumentCaptor.forClass(InventarioMovimiento.class);
        verify(movimientoRepository).save(movimiento.capture());
        assertEquals(Catalogo.SALIDA, movimiento.getValue().getTipo());
        assertEquals("Desayuno del lunes", movimiento.getValue().getDetalle());
    }

    @Test
    void unaSalidaNoPuedeSuperarLoQueHay() throws Exception {
        mockMvc.perform(post("/api/organizacion/inventario/3/movimientos")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "SALIDA", "cantidad": 7, "motivo": "ENTREGA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Solo hay 6 litros de Leche en el inventario"));

        assertEquals(0, new BigDecimal("6").compareTo(leche.getCantidad()));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void laOrganizacionNoPuedeUsarElMotivoReservadoParaDonaciones() throws Exception {
        mockMvc.perform(post("/api/organizacion/inventario/3/movimientos")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "ENTRADA", "cantidad": 1, "motivo": "DONACION"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Elige un motivo válido"));
    }

    @Test
    void editarNoCambiaLaCantidad() throws Exception {
        mockMvc.perform(put("/api/organizacion/inventario/3")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Leche entera", "categoria": "ALIMENTOS", "unidad": "litros", "cantidad": 99}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Leche entera"))
                .andExpect(jsonPath("$.cantidad").value(6));
    }

    @Test
    void otraOrganizacionNoPuedeEliminarElArticulo() throws Exception {
        VinculosService otra = mock(VinculosService.class);
        when(otra.idOrganizacionDeCuenta(31)).thenReturn(6);
        mockMvc = MockMvcBuilders.standaloneSetup(new InventarioController(
                articuloRepository, movimientoRepository, mock(UsuarioLookupRepository.class), otra,
                new InventarioService(articuloRepository, movimientoRepository))).build();

        mockMvc.perform(delete("/api/organizacion/inventario/3").header("X-User-Id", 31))
                .andExpect(status().isNotFound());

        verify(articuloRepository, never()).delete(any());
    }
}
