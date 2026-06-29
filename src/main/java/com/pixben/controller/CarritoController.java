package com.pixben.controller;

import com.pixben.mongo.Carrito;
import com.pixben.repository.CarritoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carrito")
@CrossOrigin(origins = "*")
public class CarritoController {

    private final CarritoRepository carritoRepository;

    public CarritoController(CarritoRepository carritoRepository) {
        this.carritoRepository = carritoRepository;
    }

    @PostMapping
    public Carrito agregar(@RequestBody Carrito carrito) {
        return carritoRepository.save(carrito);
    }

    @GetMapping("/{usuario}")
    public List<Carrito> listar(@PathVariable String usuario) {
        return carritoRepository.findByUsuario(usuario);
    }

@DeleteMapping("/{id}")
public String eliminar(@PathVariable String id) {

    carritoRepository.deleteById(id);

    return "Producto eliminado";
}


@PutMapping("/{id}/{cantidad}")
public Carrito actualizarCantidad(
        @PathVariable String id,
        @PathVariable Integer cantidad) {

    Carrito carrito = carritoRepository.findById(id).orElse(null);

    if (carrito != null) {
        carrito.setCantidad(cantidad);
        return carritoRepository.save(carrito);
    }

    return null;
}

@DeleteMapping("/usuario/{usuario}")
public String vaciarCarrito(@PathVariable String usuario){

    List<Carrito> productos =
            carritoRepository.findByUsuario(usuario);

    carritoRepository.deleteAll(productos);

    return "Carrito vaciado";
}

    }


