package com.pixben.dto;

import com.pixben.mongo.Resena;
import java.util.List;

public record ResenaPaginaResponse(
        List<Resena> contenido,
        int pagina,
        int totalPaginas,
        long totalElementos,
        boolean hayMas
) {
}
