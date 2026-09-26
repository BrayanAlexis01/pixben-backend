package com.pixben.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixben.model.Producto;
import com.pixben.model.Usuario;
import com.pixben.model.VarianteColor;
import com.pixben.mongo.Carrito;
import com.pixben.repository.CarritoRepository;
import com.pixben.repository.ProductoRepository;
import com.pixben.service.AutenticacionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CarritoControllerVariantTest {

    @Mock
    private CarritoRepository carritoRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private AutenticacionService autenticacionService;

    private CarritoController controller;
    private Producto producto;

    @BeforeEach
    void setUp() {
        controller = new CarritoController(carritoRepository, productoRepository, autenticacionService);

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setNombre("Cliente");
        usuario.setCorreo("cliente@pixben.pe");

        producto = new Producto();
        producto.setId(10L);
        producto.setNombre("Polo PixBen");
        producto.setCategoria("Polos");
        producto.setTallasDisponibles("S,M,L");
        producto.setStock(20);
        producto.setColores(List.of());

        when(autenticacionService.requerirUsuario("token")).thenReturn(usuario);
        when(productoRepository.findById(10L)).thenReturn(Optional.of(producto));
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void permiteMismoProductoConTallasDistintas() {
        when(carritoRepository.findFirstByUsuarioIdAndProductoIdAndTallaAndColorAndPersonalizadoFalse(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        controller.agregar("token", item("M", "SIN_COLOR"));
        controller.agregar("token", item("S", "SIN_COLOR"));

        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepository, times(2)).save(captor.capture());

        List<String> tallas = captor.getAllValues().stream().map(Carrito::getTalla).toList();
        assertEquals(List.of("M", "S"), tallas);
    }

    @Test
    void permiteMismoProductoConColoresDistintos() {
        VarianteColor negro = color("Negro", 8);
        VarianteColor blanco = color("Blanco", 8);
        producto.setColores(List.of(negro, blanco));

        when(carritoRepository.findFirstByUsuarioIdAndProductoIdAndTallaAndColorAndPersonalizadoFalse(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        controller.agregar("token", item("M", "Negro"));
        controller.agregar("token", item("M", "Blanco"));

        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepository, times(2)).save(captor.capture());

        List<String> colores = captor.getAllValues().stream().map(Carrito::getColor).toList();
        assertEquals(List.of("Negro", "Blanco"), colores);
    }

    private Carrito item(String talla, String color) {
        Carrito item = new Carrito();
        item.setProductoId(10L);
        item.setCantidad(1);
        item.setTalla(talla);
        item.setColor(color);
        item.setPersonalizado(false);
        return item;
    }

    private VarianteColor color(String nombre, int stock) {
        VarianteColor color = new VarianteColor();
        color.setNombre(nombre);
        color.setStock(stock);
        color.setCodigoHex("#000000");
        return color;
    }
}
