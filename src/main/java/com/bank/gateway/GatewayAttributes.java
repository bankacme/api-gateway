package com.bank.gateway;

/** Nombres compartidos entre los filtros y los controladores del Gateway. */
public final class GatewayAttributes {

    /** Encabezado con el identificador de la solicitud (se conserva o se genera). */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Atributo del exchange con el identificador de la solicitud. */
    public static final String REQUEST_ID = "bank.gateway.requestId";

    /**
     * Atributo del exchange con la ruta que pidió el cliente. Un {@code forward:} (fallback o
     * bloqueo) cambia la ruta de la petición, pero comparte los atributos: así el cuerpo de error
     * muestra la ruta original y no {@code /fallback}.
     */
    public static final String ORIGINAL_PATH = "bank.gateway.originalPath";

    private GatewayAttributes() {
    }
}
