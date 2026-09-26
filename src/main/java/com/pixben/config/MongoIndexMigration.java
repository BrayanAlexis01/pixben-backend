package com.pixben.config;

import com.mongodb.client.MongoCollection;
import java.util.Set;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoIndexMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    public MongoIndexMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        limpiarIndiceAntiguoPorProducto("carrito");
        limpiarIndiceAntiguoPorProducto("favoritos");
    }

    private void limpiarIndiceAntiguoPorProducto(String coleccion) {
        try {
            MongoCollection<Document> collection = mongoTemplate.getCollection(coleccion);
            for (Document indice : collection.listIndexes()) {
                String nombre = indice.getString("name");
                Document clave = indice.get("key", Document.class);
                if (nombre == null || "_id_".equals(nombre) || clave == null) continue;

                Set<String> campos = clave.keySet();
                boolean indiceViejo = campos.contains("usuarioId")
                        && campos.contains("productoId")
                        && !campos.contains("talla")
                        && !campos.contains("color");

                if (indiceViejo) {
                    collection.dropIndex(nombre);
                }
            }
        } catch (Exception ex) {
            System.err.println("No se pudo revisar índices antiguos de " + coleccion + ": " + ex.getMessage());
        }
    }
}
