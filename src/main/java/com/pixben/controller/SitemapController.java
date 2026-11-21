package com.pixben.controller;

import com.pixben.repository.ProductoRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {

    private final ProductoRepository productoRepository;

    public SitemapController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping(value = "/seo/sitemap-productos.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapProductos() {
        List<Long> productos = productoRepository.listarIdsParaSitemap();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (Long id : productos) {
            if (id != null && id > 0) {
                xml.append("  <url><loc>https://pixben.netlify.app/htmls/detalles-producto.html?id=")
                        .append(id)
                        .append("</loc></url>\n");
            }
        }

        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(xml.toString());
    }
}
