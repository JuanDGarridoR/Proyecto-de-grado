package com.proyectogrado.donacion_service.controller;

import com.proyectogrado.donacion_service.model.Catalogo;
import com.proyectogrado.donacion_service.model.Donacion;
import com.proyectogrado.donacion_service.model.DonacionArticulo;
import com.proyectogrado.donacion_service.model.InventarioArticulo;
import com.proyectogrado.donacion_service.model.InventarioMovimiento;
import com.proyectogrado.donacion_service.repository.DonacionRepository;
import com.proyectogrado.donacion_service.repository.InventarioArticuloRepository;
import com.proyectogrado.donacion_service.repository.InventarioMovimientoRepository;
import com.proyectogrado.donacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import com.proyectogrado.donacion_service.service.DonacionMapper;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Donaciones del lado de la organización (RF-64 y RF-65): al recibirlas, lo que llegó entra
 * al inventario (sumándose a lo que ya había); solo se procesan una vez.
 */
class OrganizacionDonacionControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;

    private DonacionRepository donacionRepository;
    private InventarioArticuloRepository articuloRepository;
    private InventarioMovimientoRepository movimientoRepository;
    private MockMvc mockMvc;
    private Donacion donacion;

    @BeforeEach
    void setUp() {
        donacionRepository = mock(DonacionRepository.class);
        articuloRepository = mock(InventarioArticuloRepository.class);
        movimientoRepository = mock(InventarioMovimientoRepository.class);
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        OrganizacionLookupRepository organizacionLookupRepository = mock(OrganizacionLookupRepository.class);
        VinculosService vinculosService = mock(VinculosService.class);

        when(vinculosService.idOrganizacionDeCuenta(CUENTA_ORGANIZACION)).thenReturn(ORGANIZACION);
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articuloRepository.save(any(InventarioArticulo.class))).thenAnswer(inv -> inv.getArgument(0));

        donacion = new Donacion(40, VinculosService.VOLUNTARIO, ORGANIZACION, LocalDate.now());
        donacion.agregarArticulo(articulo(1, "ALIMENTOS", "Arroz", "10", "kg"));
        donacion.agregarArticulo(articulo(2, "ROPA", "Chaquetas", "4", "unidades"));
        when(donacionRepository.findById(8)).thenReturn(Optional.of(donacion));

        InventarioService inventarioService = new InventarioService(articuloRepository, movimientoRepository);
        DonacionMapper mapper = new DonacionMapper(
                usuarioLookupRepository, organizacionLookupRepository, vinculosService);

        mockMvc = MockMvcBuilders.standaloneSetup(new OrganizacionDonacionController(
                donacionRepository, usuarioLookupRepository, vinculosService, inventarioService, mapper)).build();
    }

    private static DonacionArticulo articulo(int id, String categoria, String descripcion, String cantidad,
                                             String unidad) {
        DonacionArticulo articulo = new DonacionArticulo(
                categoria, descripcion, new BigDecimal(cantidad), unidad, null);
        ReflectionTestUtils.setField(articulo, "idArticulo", id);
        return articulo;
    }

    @Test
    void recibirSumaAlInventarioLoQueLlegoYCreaLoQueNoExistia() throws Exception {
        InventarioArticulo arrozGuardado = new InventarioArticulo(ORGANIZACION, "Arroz", "ALIMENTOS", "kg");
        arrozGuardado.sumar(new BigDecimal("5"), null);
        when(articuloRepository.findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                ORGANIZACION, "ALIMENTOS", "Arroz", "kg")).thenReturn(Optional.of(arrozGuardado));
        when(articuloRepository.findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                ORGANIZACION, "ROPA", "Chaquetas", "unidades")).thenReturn(Optional.empty());

        // Del arroz llegaron 8 kg de los 10 anunciados; de las chaquetas no se dice: llegaron las 4.
        mockMvc.perform(put("/api/organizacion/donaciones/8/recibir")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articulos": [{"idArticulo": 1, "cantidad": 8}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.articulos[0].cantidadRecibida").value(8));

        assertEquals(0, new BigDecimal("13").compareTo(arrozGuardado.getCantidad()));

        ArgumentCaptor<InventarioArticulo> guardados = ArgumentCaptor.forClass(InventarioArticulo.class);
        verify(articuloRepository, times(2)).save(guardados.capture());
        InventarioArticulo chaquetas = guardados.getAllValues().get(1);
        assertEquals("Chaquetas", chaquetas.getNombre());
        assertEquals(0, new BigDecimal("4").compareTo(chaquetas.getCantidad()));

        ArgumentCaptor<InventarioMovimiento> movimientos = ArgumentCaptor.forClass(InventarioMovimiento.class);
        verify(movimientoRepository, times(2)).save(movimientos.capture());
        assertEquals(Catalogo.MOTIVO_DONACION, movimientos.getValue().getMotivo());
        assertEquals(Catalogo.ENTRADA, movimientos.getValue().getTipo());
    }

    @Test
    void siNoLlegoNadaHayQueMarcarlaNoEntregada() throws Exception {
        mockMvc.perform(put("/api/organizacion/donaciones/8/recibir")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articulos": [{"idArticulo": 1, "cantidad": 0}, {"idArticulo": 2, "cantidad": 0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Si no llegó nada, marca la donación como no entregada"));

        verify(articuloRepository, never()).save(any());
        assertEquals(Donacion.PROGRAMADA, donacion.getEstado());
    }

    @Test
    void unaCantidadInvalidaNoDejaCambiosAMedias() throws Exception {
        mockMvc.perform(put("/api/organizacion/donaciones/8/recibir")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articulos": [{"idArticulo": 1, "cantidad": 8}, {"idArticulo": 2, "cantidad": -1}]}
                                """))
                .andExpect(status().isBadRequest());

        assertNull(donacion.getArticulos().get(0).getCantidadRecibida());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void unaDonacionSoloSeRecibeUnaVez() throws Exception {
        donacion.responder(Donacion.RECIBIDA);

        mockMvc.perform(put("/api/organizacion/donaciones/8/recibir").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta donación ya fue procesada"));

        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void otraOrganizacionNoPuedeTocarLaDonacion() throws Exception {
        VinculosService otraOrganizacion = mock(VinculosService.class);
        when(otraOrganizacion.idOrganizacionDeCuenta(31)).thenReturn(6);

        mockMvc = MockMvcBuilders.standaloneSetup(new OrganizacionDonacionController(
                donacionRepository, mock(UsuarioLookupRepository.class), otraOrganizacion,
                new InventarioService(articuloRepository, movimientoRepository),
                new DonacionMapper(mock(UsuarioLookupRepository.class),
                        mock(OrganizacionLookupRepository.class), otraOrganizacion))).build();

        mockMvc.perform(put("/api/organizacion/donaciones/8/no-entregada").header("X-User-Id", 31))
                .andExpect(status().isNotFound());

        assertEquals(Donacion.PROGRAMADA, donacion.getEstado());
    }

    @Test
    void noEntregadaGuardaLaNotaYNoTocaElInventario() throws Exception {
        mockMvc.perform(put("/api/organizacion/donaciones/8/no-entregada")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota": " Nadie vino ese día "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_ENTREGADA"))
                .andExpect(jsonPath("$.notaOrganizacion").value("Nadie vino ese día"));

        verify(articuloRepository, never()).findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                anyInt(), anyString(), anyString(), eq("kg"));
        verify(movimientoRepository, never()).save(any());
    }
}
