# Android OCR Streaming con ESP32-CAM

Proyecto Android que implementa detección de texto en tiempo real (OCR) usando cámara local o ESP32-CAM, con visualización de resultados mediante overlays y envío de texto estable a una API.

---

## Características

* Streaming de video desde cámara o ESP32-CAM
* OCR en tiempo real con ML Kit
* Renderizado de bounding boxes sobre texto detectado
* Filtro de estabilidad para reducir ruido
* Envío automático de resultados a API REST en formato JSON
* Control de frecuencia para evitar solicitudes redundantes

---

## Arquitectura

```
Camera / ESP32 Stream
        ↓
   Frame Processing
        ↓
      ML Kit OCR
        ↓
 Bounding Boxes + Texto
        ↓
 StableTextProcessor
        ↓
 Texto Estable
        ↓
   Envío a API
```

---

## Estructura del Proyecto

```
com.testing.esp32_ia/
│
├── StreamFragment.java
├── OCRGraphicOverlay.java
├── StableTextProcessor.java
├── ApiService (opcional)
└── layout/
    └── fragment_stream.xml
```

---

## Lógica de Estabilidad

Debido a que el OCR en streaming no es determinístico, se implementa un mecanismo de estabilización basado en:

* Buffer de múltiples frames
* Normalización de texto
* Conteo por frecuencia
* Comparación por similitud (Levenshtein)
* Umbral mínimo de coincidencias

Ejemplo:

```java
buffer = ["hola", "hola", "hola", "hol4", "hola"]
```

Resultado estable:

```
"hola"
```

---

## Overlay de Detección

Se utiliza una vista personalizada (`OCRGraphicOverlay`) que:

* Dibuja rectángulos sobre el texto detectado
* Escala coordenadas según el tamaño de la vista
* Se actualiza en cada frame procesado

---

## Envío a API

Formato del JSON:

```json
{
  "text": "texto detectado",
  "timestamp": 1710000000000
}
```

Implementación:

* Cliente HTTP con OkHttp
* Ejecución en hilo secundario
* Control de frecuencia mediante cooldown

---

## Requisitos

* Android Studio
* Android SDK 24 o superior
* Dispositivo con cámara o ESP32-CAM

Dependencias:

```gradle
implementation 'com.google.mlkit:text-recognition:16.0.0'
implementation 'com.squareup.okhttp3:okhttp:4.9.3'
```

---

## Uso con ESP32-CAM

El sistema puede consumir:

* Stream MJPEG
* Captura de frames vía HTTP

Ejemplo:

```
http://192.168.4.1/capture
```

---

## Flujo de Uso

1. Iniciar cámara o conexión con ESP32-CAM
2. Activar OCR
3. Visualizar texto detectado en pantalla
4. Esperar estabilización automática
5. Envío del texto a la API

---

## Problemas Comunes

Bounding boxes desalineados

* Verificar escalado de coordenadas

Texto inestable

* Ajustar tamaño del buffer o threshold

Solicitudes duplicadas a API

* Implementar cooldown

---

## Mejoras Futuras

* Tracking por bounding box
* OCR por líneas en lugar de bloques
* Selección táctil de texto
* Definición de región de interés (ROI)
* Integración con procesamiento NLP

---

## Licencia

Uso libre para fines educativos y desarrollo.

---

## Autor

Proyecto desarrollado como implementación de OCR en tiempo real sobre Android.
