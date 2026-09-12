package com.pixben.service;

import com.pixben.mongo.PushSubscription;
import com.pixben.repository.PushSubscriptionRepository;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WebPushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPushService.class);
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Set<String> HOSTS_EXACTOS = Set.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com",
            "push.services.mozilla.com",
            "web.push.apple.com"
    );
    private static final List<String> SUFIJOS_PERMITIDOS = List.of(
            ".notify.windows.com"
    );

    private final PushSubscriptionRepository repository;
    private final HttpClient httpClient;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushService(
            PushSubscriptionRepository repository,
            @Value("${app.vapid.public-key:}") String publicKey,
            @Value("${app.vapid.private-key:}") String privateKey,
            @Value("${app.vapid.subject:https://pixben.netlify.app}") String subject) {
        this.repository = repository;
        this.publicKey = valor(publicKey);
        this.privateKey = valor(privateKey);
        this.subject = valor(subject).isBlank() ? "https://pixben.netlify.app" : valor(subject);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean configurado() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Async("pushExecutor")
    public void notificarActualizacionPedido(Long usuarioId) {
        if (usuarioId == null || !configurado()) return;
        List<PushSubscription> suscripciones = repository.findByUsuarioId(usuarioId);
        for (PushSubscription suscripcion : suscripciones) {
            enviar(suscripcion);
        }
    }

    private void enviar(PushSubscription suscripcion) {
        if (suscripcion == null || suscripcion.getEndpoint() == null) return;

        try {
            URI endpoint = URI.create(suscripcion.getEndpoint());
            validarEndpoint(endpoint);
            String jwt = crearJwt(endpoint);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .header("Authorization", "vapid t=" + jwt + ", k=" + publicKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                repository.delete(suscripcion);
            } else if (status < 200 || status >= 300) {
                LOGGER.warn("El servicio Push respondió {} para una suscripción de usuario {}", status, suscripcion.getUsuarioId());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Se interrumpió el envío de una notificación Push", ex);
        } catch (Exception ex) {
            LOGGER.warn("No se pudo enviar una notificación Push", ex);
        }
    }

    private String crearJwt(URI endpoint) throws Exception {
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() > 0 && endpoint.getPort() != 443 ? ":" + endpoint.getPort() : "");
        long expiration = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();

        String header = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
        String payload = "{\"aud\":\"" + escaparJson(audience)
                + "\",\"exp\":" + expiration
                + ",\"sub\":\"" + escaparJson(subject) + "\"}";

        String input = codificar(header.getBytes(StandardCharsets.UTF_8))
                + "." + codificar(payload.getBytes(StandardCharsets.UTF_8));

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(crearClavePrivada());
        signature.update(input.getBytes(StandardCharsets.US_ASCII));
        byte[] firmaJose = firmaDerAJose(signature.sign(), 64);
        return input + "." + codificar(firmaJose);
    }

    private PrivateKey crearClavePrivada() throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(agregarPadding(privateKey));
        if (bytes.length != 32) {
            throw new IllegalStateException("VAPID_PRIVATE_KEY debe contener 32 bytes en Base64URL");
        }

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(new BigInteger(1, bytes), ecSpec);
        return KeyFactory.getInstance("EC").generatePrivate(keySpec);
    }

    private byte[] firmaDerAJose(byte[] der, int longitud) {
        int[] posicion = {0};
        if (leerByte(der, posicion) != 0x30) throw new IllegalArgumentException("Firma DER inválida");
        leerLongitud(der, posicion);
        if (leerByte(der, posicion) != 0x02) throw new IllegalArgumentException("Firma DER inválida");
        int longitudR = leerLongitud(der, posicion);
        byte[] r = Arrays.copyOfRange(der, posicion[0], posicion[0] + longitudR);
        posicion[0] += longitudR;
        if (leerByte(der, posicion) != 0x02) throw new IllegalArgumentException("Firma DER inválida");
        int longitudS = leerLongitud(der, posicion);
        byte[] s = Arrays.copyOfRange(der, posicion[0], posicion[0] + longitudS);

        int mitad = longitud / 2;
        byte[] jose = new byte[longitud];
        copiarEntero(r, jose, 0, mitad);
        copiarEntero(s, jose, mitad, mitad);
        return jose;
    }

    private void copiarEntero(byte[] origen, byte[] destino, int offset, int longitud) {
        int inicio = 0;
        while (inicio < origen.length - 1 && origen[inicio] == 0) inicio++;
        int cantidad = origen.length - inicio;
        if (cantidad > longitud) {
            inicio += cantidad - longitud;
            cantidad = longitud;
        }
        System.arraycopy(origen, inicio, destino, offset + longitud - cantidad, cantidad);
    }

    private int leerByte(byte[] datos, int[] posicion) {
        if (posicion[0] >= datos.length) throw new IllegalArgumentException("Firma DER incompleta");
        return datos[posicion[0]++] & 0xff;
    }

    private int leerLongitud(byte[] datos, int[] posicion) {
        int primero = leerByte(datos, posicion);
        if ((primero & 0x80) == 0) return primero;
        int cantidad = primero & 0x7f;
        if (cantidad < 1 || cantidad > 4) throw new IllegalArgumentException("Longitud DER inválida");
        int valor = 0;
        for (int i = 0; i < cantidad; i++) valor = (valor << 8) | leerByte(datos, posicion);
        return valor;
    }

    private void validarEndpoint(URI endpoint) {
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
            throw new IllegalArgumentException("El endpoint Push debe usar HTTPS");
        }
        String host = endpoint.getHost().toLowerCase(Locale.ROOT);
        boolean permitido = HOSTS_EXACTOS.contains(host)
                || SUFIJOS_PERMITIDOS.stream().anyMatch(host::endsWith);
        if (!permitido) {
            throw new IllegalArgumentException("Proveedor Push no permitido");
        }
    }

    private String codificar(byte[] datos) {
        return BASE64_URL.encodeToString(datos);
    }

    private String agregarPadding(String valor) {
        int resto = valor.length() % 4;
        return resto == 0 ? valor : valor + "=".repeat(4 - resto);
    }

    private String escaparJson(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String valor(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
