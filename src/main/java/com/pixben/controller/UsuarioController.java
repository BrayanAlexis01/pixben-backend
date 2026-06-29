/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pixben.controller;

import com.pixben.model.Usuario;
import com.pixben.repository.UsuarioRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    public Usuario registrar(@RequestBody Usuario usuario) {

        if(usuario.getRol() == null){
            usuario.setRol("cliente");
        }

        return usuarioRepository.save(usuario);
    }
    
    @PostMapping("/login")
public Usuario login(@RequestBody Usuario datos){

    Usuario usuario =
            usuarioRepository.findByCorreo(datos.getCorreo());

    if(usuario != null &&
       usuario.getPassword().equals(datos.getPassword())){

        return usuario;
    }

    return null;
}
}