package com.pixben.service;

import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImagenSeguraService {

    private static final long MAXIMO_BYTES = 8L * 1024L * 1024L;
    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );

    public byte[] validar(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen está vacía");
        }

        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Cada imagen debe pesar como máximo 8 MB"
            );
        }

        String tipo = archivo.getContentType();
        if (tipo == null || !TIPOS_PERMITIDOS.contains(tipo.toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo se permiten imágenes PNG, JPG/JPEG o WEBP. No se acepta SVG."
            );
        }

        byte[] bytes = archivo.getBytes();
        if (!firmaValida(bytes, tipo.toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El contenido del archivo no coincide con una imagen válida"
            );
        }

        return bytes;
    }

    private boolean firmaValida(byte[] bytes, String tipo) {
        if (bytes.length < 12) {
            return false;
        }

        return switch (tipo) {
            case "image/png" ->
                (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47;
            case "image/jpeg" ->
                (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
            case "image/webp" ->
                bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }
}
