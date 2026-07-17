package com.pixben;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("La prueba de contexto requiere Neon, MongoDB Atlas y Cloudinary")
@SpringBootTest
class PixbenApplicationTests {

    @Test
    void contextLoads() {
    }
}
