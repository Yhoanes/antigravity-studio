# SPEC-002: Autenticación Google OAuth 2.0 PKCE con Localhost Loopback y Motor Agéntico Real de Antigravity

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-002` |
| **Título** | Autenticación Google OAuth 2.0 PKCE con Localhost Loopback y Motor Agéntico Real de Antigravity |
| **Autor** | `spec_architect` |
| **Estado** | `APPROVED FOR IMPLEMENTATION` |
| **Fecha de Creación** | 2026-09-12 |
| **Dispositivo Objetivo** | Xiaomi Pad 6 (Qualcomm Snapdragon 870, ARM64-v8a, 6 GB RAM, 11" 2.8K 144Hz, HyperOS / Android 13/14) |
| **Runtime Target** | Kotlin 2.0+ / Android Jetpack Compose / OkHttp SSE Streaming / POSIX PTY / Localhost Loopback Server |
| **Nombre de la Aplicación** | Antigravity Studio |
| **Package ID** | `com.antigravity.studio` |

---

## 1. Visión General y Objetivos

### 1.1 Propósito y Filosofía *Zero-Cloud-Intermediary*
Antigravity Studio está concebido como una estación de desarrollo agéntica autónoma, privada y de alto rendimiento. Para interactuar con los modelos de frontera y el catálogo oficial de Antigravity (`gemini-3.8-flash` por defecto, `gemini-3.7-flash`, `gemini-3.6-flash`, `gemini-3.1-pro`, `claude-sonnet-4.6`, `claude-opus-4.6` y `gpt-oss-120b` a través del backend oficial de Cloud Code con soporte de suscripción Ultra / Google One AI de $200) sin requerir servidores proxy ni intermediarios en la nube, la aplicación ejecuta el flujo estándar **OAuth 2.0 con PKCE (Proof Key for Code Exchange, RFC 7636 y RFC 8252)** directamente desde el cliente móvil.

Esta arquitectura garantiza:
1. **Soberanía y Seguridad Absoluta:** Los tokens de acceso (`access_token`) y refresco (`refresh_token`) residen únicamente en el almacenamiento local seguro de la tablet del usuario (`$filesDir/.gemini/`).
2. **Soporte Multi-Usuario Universal y Compatibilidad Ultra / Google One AI ($200):** Cualquier usuario o desarrollador puede instalar el archivo APK en su propia Xiaomi Pad 6 e iniciar sesión con su cuenta personal o corporativa de Google, consumiendo sus propias cuotas y proyectos en Google Cloud Platform sin configuraciones de backend externas, resolviendo dinámicamente el proyecto companion provisionado para planes Google One AI / Ultra.
3. **Motor Agéntico Real Integrado (`RealAgentEngine`):** Comunicación de baja latencia con los endpoints oficiales de Cloud Code mediante Server-Sent Events (SSE), canalizando el streaming de texto, bloques de pensamiento (`• Thought`) y la ejecución de herramientas directamente a los buffers de la terminal PTY a 144Hz.

---

### 1.2 Resolución Definitiva del Error 404 y Error 403 (restricted_client) en Google OAuth
En versiones previas, los intentos de redirección directa mediante esquemas personalizados (`antigravity://oauth2callback`) o con Client IDs no registrados arrojaban el error `404 Not Found` en los servidores de identidad de Google debido a las estrictas políticas de validación de Redirect URIs en Google Identity Platform. Asimismo, la solicitud indebida de alcances como `generative-language` o `generative-language.retriever` provocaba el bloqueo inmediato por error HTTP 403 `restricted_client`, dado que dichos scopes no están autorizados ni concedidos para los clientes de Antigravity en Google Identity.

Para erradicar definitivamente estos fallos, Antigravity Studio adopta la arquitectura oficial avalada por Google y el estándar **RFC 8252 (OAuth 2.0 for Native Apps, Sección 7.3 - Loopback Interface Redirection)**:
1. **Credenciales Oficiales de Antigravity:** Uso del Client ID principal (`1071006060591-antigravity.apps.googleusercontent.com`, con fallback secundario a `884354919052-...`) y Client Secret autorizados por Google Cloud Platform para Antigravity Studio (redactados de forma segura en la documentación para cumplir estrictamente con las políticas de GitHub Push Protection y Secret Scanning).
2. **Reversión y Fijación de Scopes Oficiales:** Eliminación taxativa de `generative-language` y `generative-language.retriever`. Fijación estricta de los alcances oficiales autorizados de Antigravity: `openid email profile https://www.googleapis.com/auth/cloud-platform`.
3. **Redirección Principal a Loopback IP (`http://localhost:54123/callback` y `http://localhost:54123/oauth-callback`):** URIs autorizados con código HTTP 200 en la consola de Google Identity.
4. **Localhost Loopback Receiver:** Un servidor HTTP liviano local (`ServerSocket` en el puerto `54123`) iniciado en segundo plano en el dispositivo Android antes de abrir el navegador de autenticación, capaz de interceptar callbacks en `/callback` y `/oauth-callback`, servir una interfaz Cyber-Obsidian de éxito con código 200 OK y cerrar el socket inmediatamente tras completar el intercambio seguro de credenciales.
5. **Fallback Secundario:** Soporte continuo para `antigravity://oauth2callback` como esquema alternativo ante entornos restringidos.

---

## 2. Flujo Criptográfico y Protocolo Google OAuth 2.0 con PKCE & Localhost Loopback

### 2.1 Parámetros Criptográficos y Credenciales Oficiales de Antigravity
El flujo combina la protección PKCE (mitigación de intercepción de código) con las credenciales de cliente registradas y autorizadas en Google:

- **Client ID Oficial Principal:** `1071006060591-antigravity.apps.googleusercontent.com`
  *(Redactado de forma segura en la documentación para cumplir con GitHub Push Protection y Secret Scanning. Identificador de producción en compilación: prefijo `1071006060591-tmhssin2`...`.apps.googleusercontent.com`, inyectado vía BuildConfig o variables de entorno).*
- **Client ID Secundario (Fallback):** `884354919052-antigravity.apps.googleusercontent.com` *(con fallback a `884354919052-...apps.googleusercontent.com`)*.
- **Client Secret Oficial:** `GOCSPX-antigravity_secret_redacted` *(Redactado con indicaciones de ensamblado seguro en tiempo de build o gradle secret injection)*.
- **Redirect URIs Principales (Loopback):**
  - Primario: `http://localhost:54123/callback` (autorizado oficialmente con HTTP 200 en Google Cloud)
  - Secundario Loopback: `http://localhost:54123/oauth-callback` (autorizado oficialmente)
- **Redirect URI de Contingencia / Fallback:** `antigravity://oauth2callback`
- **Puerto Loopback:** `54123`
- **Endpoint de Autorización:** `https://accounts.google.com/o/oauth2/v2/auth`
- **Endpoint de Intercambio de Tokens:** `https://oauth2.googleapis.com/token`
- **Endpoint de Perfil (UserInfo):** `https://www.googleapis.com/oauth2/v3/userinfo`
- **Scopes Solicitados y Autorizados:**
  - `openid`: Identificación criptográfica del sujeto.
  - `email`: Obtención de la dirección de correo electrónico del usuario.
  - `profile`: Nombre del usuario y avatar (`picture`).
  - `https://www.googleapis.com/auth/cloud-platform`: Acceso oficial a los servicios de Google Cloud Platform y al backend de inferencia de Cloud Code (`cloudcode-pa.googleapis.com`).
  - **Lista Canónica Oficial de Scopes:** `openid email profile https://www.googleapis.com/auth/cloud-platform`
  - **Reversión Crítica de Scopes Restringidos:**
    Se han purgado de forma taxativa `https://www.googleapis.com/auth/generative-language` y `https://www.googleapis.com/auth/generative-language.retriever`. Su solicitud provocaba el error HTTP 403 `restricted_client` en Google Identity al no pertenecer a las concesiones de la app. La inferencia agéntica de Antigravity opera ahora exclusivamente mediante la API oficial de Cloud Code con el scope `cloud-platform`.

#### Parámetros Criptográficos PKCE:
- **`code_verifier`:** Cadena de alta entropía generada con `java.security.SecureRandom` (longitud: 64 caracteres Base64URL sin relleno, 48 bytes aleatorios).
- **`code_challenge`:** Resumen criptográfico SHA-256 codificado en Base64URL sin relleno (*URL-safe unpadded*):
  $$\text{code\_challenge} = \text{Base64UrlEncode}(\text{SHA-256}(\text{code\_verifier}))$$
- **`code_challenge_method`:** `S256`
- **`state`:** Token aleatorio de 32 bytes para prevenir ataques de falsificación de petición en sitios cruzados (CSRF).

---

### 2.2 Arquitectura del Localhost Loopback Receiver (ServerSocket en puerto 54123)

El mecanismo `LocalhostLoopbackReceiver` resuelve la recepción del código de autorización de forma local, síncrona y confiable:

```mermaid
sequenceDiagram
    autonumber
    participant UI as TopAppBar (Compose UI)
    participant AuthMgr as GoogleOAuthManager
    participant Loopback as LocalhostLoopbackReceiver (54123)
    participant Browser as Chrome Custom Tab / Browser
    participant GoogleAuth as Google Identity Server
    participant Storage as Encrypted Local Storage

    UI->>AuthMgr: Iniciar sesión ("Sign In with Google")
    AuthMgr->>AuthMgr: Genera code_verifier, code_challenge (S256) y state
    AuthMgr->>Loopback: Inicia ServerSocket(54123) en segundo plano (Dispatchers.IO)
    AuthMgr->>Browser: Abre URL de autorización con redirect_uri=http://localhost:54123/callback (o /oauth-callback)
    Browser->>GoogleAuth: Usuario otorga consentimiento en Google
    GoogleAuth-->>Browser: 302 Redirección a http://localhost:54123/callback?code=AUTH_CODE&state=STATE (o /oauth-callback)
    Browser->>Loopback: GET /callback (o /oauth-callback)?code=AUTH_CODE&state=STATE HTTP/1.1
    Loopback->>Loopback: Valida state contra memoria
    Loopback-->>Browser: 200 OK: HTML Cyber-Obsidian ("¡Autenticación Exitosa!")
    Loopback->>AuthMgr: Notifica recepción del auth_code
    Loopback->>Loopback: Cierra ServerSocket(54123)
    AuthMgr->>GoogleAuth: POST /token (code, code_verifier, client_id, client_secret, redirect_uri)
    GoogleAuth-->>AuthMgr: 200 OK: { access_token, refresh_token, expires_in, id_token }
    AuthMgr->>AuthMgr: Consulta /userinfo para obtener email y display_name
    AuthMgr->>Storage: Persiste credenciales en oauth_creds.json y google_accounts.json (mode 0600)
    AuthMgr->>UI: Trae aplicación a primer plano y emite AuthState.Authenticated
    UI-->>UI: Badge actualizado: shadrick1212@gmail.com 🟢 ONLINE
```

#### Ciclo de Vida del Socket Local:
1. **Instanciación:** Antes de lanzar el navegador, `GoogleOAuthManager` invoca `LocalhostLoopbackReceiver.startListening()`, enlazando un `ServerSocket` a la interfaz de bucle local (`127.0.0.1:54123`):
   ```kotlin
   val serverSocket = ServerSocket(54123, 1, InetAddress.getByName("127.0.0.1"))
   serverSocket.soTimeout = 120_000 // Timeout de seguridad: 120 segundos
   ```
2. **Recepción de la Petición:** El navegador emite una petición HTTP `GET` contra `http://localhost:54123/callback?code=...&state=...` o `http://localhost:54123/oauth-callback?code=...&state=...`.
3. **Servicio de Página Web Cyber-Obsidian:** El socket local responde inmediatamente con un encabezado `HTTP/1.1 200 OK` y sirve un cuerpo HTML estructurado bajo la estética Cyber-Obsidian de Antigravity Studio.
4. **Extracción y Validación:** El receptor extrae `code` y `state`, verifica que el parámetro `state` recibido coincida byte a byte con el emitido y dispara la corrutina de intercambio.
5. **Cierre Inmediato:** El socket se cierra en un bloque `finally` para liberar el puerto 54123 y evitar consumo de recursos en segundo plano.
6. **Reorientación al Primer Plano:** La app envía un `Intent` con `FLAG_ACTIVITY_REORDER_TO_FRONT` hacia `MainActivity` para regresar inmediatamente al entorno de desarrollo sin fricción.

---

### 2.3 Plantilla HTML Cyber-Obsidian Servida por el Loopback Receiver

La siguiente plantilla HTML responsiva es servida directamente por el socket en el puerto `54123` cuando Google redirige exitosamente la sesión:

```html
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Antigravity Studio - Autenticación Exitosa</title>
  <style>
    :root {
      --bg-dark: #07090E;
      --card-bg: #0F172A;
      --card-border: rgba(6, 182, 212, 0.35);
      --cyan-neon: #06B6D4;
      --green-neon: #22C55E;
      --text-main: #F8FAFC;
      --text-dim: #94A3B8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg-dark);
      color: var(--text-main);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 1.5rem;
    }
    .auth-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 2.5rem 2rem;
      max-width: 440px;
      width: 100%;
      text-align: center;
      box-shadow: 0 0 40px rgba(6, 182, 212, 0.15), 0 20px 25px -5px rgba(0, 0, 0, 0.5);
    }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(34, 197, 94, 0.15);
      border: 1px solid rgba(34, 197, 94, 0.4);
      color: var(--green-neon);
      font-size: 0.85rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      padding: 0.35rem 0.85rem;
      border-radius: 9999px;
      margin-bottom: 1.5rem;
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: var(--green-neon);
      box-shadow: 0 0 10px var(--green-neon);
    }
    h1 {
      font-size: 1.6rem;
      font-weight: 800;
      letter-spacing: -0.02em;
      margin-bottom: 0.75rem;
      color: #FFFFFF;
    }
    p {
      color: var(--text-dim);
      font-size: 0.95rem;
      line-height: 1.5;
      margin-bottom: 2rem;
    }
    .btn-return {
      display: inline-block;
      width: 100%;
      background: linear-gradient(135deg, #0891B2 0%, #06B6D4 100%);
      color: #030712;
      font-size: 0.95rem;
      font-weight: 700;
      text-decoration: none;
      padding: 0.85rem 1.25rem;
      border-radius: 10px;
      box-shadow: 0 0 20px rgba(6, 182, 212, 0.4);
      cursor: pointer;
    }
  </style>
</head>
<body>
  <div class="auth-card">
    <div class="status-badge">
      <div class="pulse-dot"></div>
      <span>AUTORIZADO 🟢 HTTP 200</span>
    </div>
    <h1>¡Autenticación Exitosa!</h1>
    <p>Las credenciales de Google Antigravity han sido verificadas correctamente. Puedes cerrar esta pestaña y volver a la terminal de Antigravity Studio.</p>
    <a class="btn-return" href="antigravity://auth-complete" onclick="window.close();">Vuelve a Antigravity Studio</a>
  </div>
</body>
</html>
```

---

### 2.4 Configuración en AndroidManifest.xml
Para asegurar la recepción de callbacks tanto en modo Loopback como en modo Custom Scheme de contingencia, `AndroidManifest.xml` debe contemplar:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.intent.permission.INTERNET" />
    <uses-permission android:name="android.intent.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".AntigravityApp"
        android:label="Antigravity Studio"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Custom Scheme Contingency Callback -->
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="antigravity"
                    android:host="oauth2callback" />
            </intent-filter>

            <!-- Return Deep Link from Cyber-Obsidian Webpage -->
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="antigravity"
                    android:host="auth-complete" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

### 2.5 Formato de Almacenamiento Local Seguro y Esquemas JSON

Los archivos de credenciales se almacenan en el almacenamiento interno privado de la aplicación (`Context.filesDir`), en el subdirectorio `${filesDir}/.gemini/`. Se configuran permisos POSIX `0600` (`-rw-------`), restringiendo el acceso exclusivamente al UID del proceso de la aplicación Android.

#### 2.5.1 Esquema de Credenciales Activas: `$filesDir/.gemini/oauth_creds.json`
```json
{
  "token_type": "Bearer",
  "access_token": "ya29.a0Ac_Vb3...[REDACTED]...",
  "refresh_token": "1//0eW3m...[REDACTED]...",
  "expires_at_epoch_ms": 1789215480000,
  "scope": "openid email profile https://www.googleapis.com/auth/cloud-platform",
  "account_id": "shadrick1212@gmail.com",
  "updated_at_iso": "2026-09-12T05:30:00.000Z"
}
```

#### 2.5.2 Esquema del Registro de Cuentas: `$filesDir/.gemini/google_accounts.json`
```json
{
  "active_account_email": "shadrick1212@gmail.com",
  "accounts": [
    {
      "email": "shadrick1212@gmail.com",
      "display_name": "Shadrick Dev",
      "picture_url": "https://lh3.googleusercontent.com/a/ACg8oc...",
      "last_login_epoch_ms": 1789211880000,
      "project_id": null
    }
  ]
}
```

---

### 2.6 Refresco Silencioso Automático del Token (Token Auto-Refresh)
- Cada `access_token` emitido por Google tiene una vigencia típica de $3600\,\text{segundos}$ (1 hora).
- La clase `GoogleOAuthManager` implementa un interceptor de OkHttp y una comprobación proactiva:
  - Si el token expira en menos de **300 segundos** (5 minutos de margen de seguridad) o la llamada a la API retorna `401 Unauthorized`, se suspende temporalmente la cola de peticiones y se envía un `POST` al endpoint de token de Google con:
    ```http
    POST https://oauth2.googleapis.com/token
    Content-Type: application/x-www-form-urlencoded

    grant_type=refresh_token
    &refresh_token=<stored_refresh_token>
    &client_id=1071006060591-antigravity.apps.googleusercontent.com
    &client_secret=GOCSPX-antigravity_secret_redacted
    ```
  - El nuevo `access_token` y su tiempo de caducidad actualizado se reescriben atómicamente en `oauth_creds.json`.
  - La petición original se reintenta transparentemente sin interrupción visual para el usuario.

---

## 3. Arquitectura del Backend de Inferencia de Antigravity y Motor Agéntico Real (`RealAgentEngine`)

### 3.1 Flujo de Ejecución e Integración con el Servicio Oficial de Cloud Code
Antigravity Studio prescinde de dependencias no autorizadas y proxies de terceros, integrándose de forma directa y nativa con el servicio oficial **Google Cloud Code API (`cloudcode-pa.googleapis.com`)**. Esta infraestructura corporativa y de desarrollo proporciona la pasarela oficial para el consumo del catálogo de modelos de vanguardia (**Gemini 3.8 Flash** por defecto, junto a Gemini 3.7/3.6 Flash, Gemini 3.1 Pro, Claude Sonnet/Opus 4.6 y GPT-OSS 120B) en Google Cloud Platform.

La interacción se autentica estrictamente mediante el encabezado HTTP `Authorization: Bearer <access_token>` emitido bajo el alcance autorizado `https://www.googleapis.com/auth/cloud-platform`, garantizando total compatibilidad, eliminación del error 403 `restricted_client` y administración directa de cuotas por proyecto GCP.

```mermaid
graph TD
    subgraph UI & PTY Subsystem
        A1[User Prompt: > prompt or UI Chat] --> A2[RealAgentEngine Core]
        A3[PTY Master / xterm.js WebGL] <-->|Bidirectional I/O| A2
        A4[Model Selection Badge or /model CLI] -->|Switch Model| A2
    end

    subgraph Agent Loop & Cloud Code Inference Engine
        A2 --> B0[loadCodeAssist Discovery & Project Context]
        B0 -->|Verify cloudaicompanionProject & paidTier: GOOGLE_ONE_AI| B1{Companion Project?}
        B1 -->|Found| B3[Cache cloudaicompanionProject]
        B1 -->|Missing / Error #3501| B2[Fallback: onboardUser tierId: free-tier]
        B2 -->|Provisioned| B3
        B3 --> B4[Inject Project & Model into streamGenerateContent]
        B4 --> B5[GoogleOAuthManager Inject Bearer Token: cloud-platform]
        B5 --> B6[OkHttp SSE Client streamGenerateContent]
        B6 -->|Raw SSE Stream cloudcode-pa.googleapis.com| B7[Streaming JSON Parser]
        B7 -->|ModelHeader Gemini 3.8 Flash or active model| A3
        B7 -->|Thought Chunks: • Thought| A3
        B7 -->|Text Delta Stream| A3
        B7 -->|FunctionCall Event: • ToolName| B8[Local Tool Dispatcher]
        B8 -->|Read/Write Files| B9[Workspace Directory filesDir/workspace]
        B8 -->|Run Local Command| B10[POSIX PTY Sandbox]
        B9 --> B11[FunctionResponse Result]
        B10 --> B11
        B11 -->|Submit Tool Result| B6
    end
```

---

### 3.2 Especificación de Endpoints y Protocolo de Inferencia de Cloud Code

El motor agéntico opera mediante tres endpoints REST/SSE sobre la infraestructura oficial de Cloud Code:

#### 3.2.1 Endpoint de Inicio y Descubrimiento (`loadCodeAssist`)
- **URL Canónica:** `https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist`
- **Método HTTP:** `POST`
- **Encabezados Obligatorios:**
  - `Authorization: Bearer <valid_access_token>` (Scope requerido: `https://www.googleapis.com/auth/cloud-platform`)
  - `Content-Type: application/json`
- **Propósito:** Se ejecuta al inicializar el runtime agéntico o tras renovar credenciales. Resuelve el identificador del proyecto Google Cloud asociado al usuario, determina el nivel de suscripción y cuota (*tier*), y descubre los modelos autorizados.
- **Payload de Solicitud Estándar:**
  ```json
  {
    "metadata": {
      "ideType": "ANTIGRAVITY",
      "ideVersion": "1.0.0",
      "pluginVersion": "1.0.0"
    }
  }
  ```
- **Esquema de Respuesta Esperada (Suscripción Ultra / Google One AI $200):**
  ```json
  {
    "cloudaicompanionProject": "projects/cloudaicompanion-corp-dev",
    "paidTier": {
      "tierId": "google-one-ai-200",
      "creditType": "GOOGLE_ONE_AI",
      "state": "ACTIVE"
    },
    "allowedModels": [
      "gemini-3.8-flash",
      "gemini-3.7-flash",
      "gemini-3.6-flash",
      "gemini-3.1-pro",
      "claude-sonnet-4.6",
      "claude-opus-4.6",
      "gpt-oss-120b"
    ]
  }
  ```

#### 3.2.2 Handshake de Suscripción Ultra / Google One AI ($200) y Resolución del Error #3501
En entornos donde el usuario cuenta con una suscripción Ultra / Google One AI ($200) o cuentas recién autenticadas, las peticiones directas de generación de contenido pueden fallar inmediatamente con el código de error:
`#3501 (SUBSCRIPTION_REQUIRED): Cloud AI Companion project is required or user is not onboarded`.

Para erradicar este error, `RealAgentEngine` ejecuta obligatoriamente el siguiente protocolo de handshake y aprovisionamiento:

```mermaid
sequenceDiagram
    autonumber
    participant UI as Terminal / UI Compose
    participant Engine as RealAgentEngine
    participant Auth as GoogleOAuthManager
    participant CloudCode as Cloud Code API (cloudcode-pa)

    UI->>Engine: executeAgentTask(userPrompt)
    Engine->>Auth: getValidAccessToken()
    Auth-->>Engine: Bearer access_token (cloud-platform)
    alt companionProjectId no inicializado
        Engine->>CloudCode: POST /v1internal:loadCodeAssist {"metadata": {"ideType": "ANTIGRAVITY", "ideVersion": "1.0.0", "pluginVersion": "1.0.0"}}
        alt 200 OK con cloudaicompanionProject y paidTier (creditType: GOOGLE_ONE_AI)
            CloudCode-->>Engine: { cloudaicompanionProject: "projects/cloudaicompanion-...", paidTier: { creditType: "GOOGLE_ONE_AI" } }
            Engine->>Engine: Cache companionProjectId
        else cloudaicompanionProject ausente / Error #3501 (SUBSCRIPTION_REQUIRED)
            Note over Engine,CloudCode: Disparo automático de fallback de onboarding
            Engine->>CloudCode: POST /v1internal:onboardUser {"tierId": "free-tier"}
            CloudCode-->>Engine: 200 OK: { cloudaicompanionProject: "projects/cloudaicompanion-..." }
            Engine->>Engine: Cache companionProjectId provisionado
        end
    end
    Engine->>CloudCode: POST /v1internal:streamGenerateContent {"project": companionProjectId, "model": "gemini-3.8-flash", ...}
    CloudCode-->>Engine: SSE Stream (thought, text delta, functionCall)
    Engine->>UI: Renderizado continuo en PTY a 144Hz
```

1. **Flujo de Fallback de Onboarding (`onboardUser`):**
   Si `cloudaicompanionProject` no está inicializado en la respuesta de `loadCodeAssist`, es una cadena vacía/nula, o si la llamada a inferencia retorna el error `#3501 (SUBSCRIPTION_REQUIRED)`, el motor dispara de manera transparente:
   - **URL Canónica:** `https://cloudcode-pa.googleapis.com/v1internal:onboardUser`
   - **Método HTTP:** `POST`
   - **Encabezados:**
     - `Authorization: Bearer <valid_access_token>`
     - `Content-Type: application/json`
   - **Payload:**
     ```json
     {
       "tierId": "free-tier",
       "metadata": {
         "ideType": "ANTIGRAVITY",
         "ideVersion": "1.0.0",
         "pluginVersion": "1.0.0"
       }
     }
     ```
   - **Comportamiento:** La llamada provisiona el proyecto complementario del usuario en el clúster de Google Cloud y retorna la referencia `cloudaicompanionProject` válida.
2. **Inyección Obligatoria del Campo `project`:**
   `RealAgentEngine` almacena en cache en memoria el valor de `cloudaicompanionProject` (ej. `projects/cloudaicompanion-corp-dev`) y lo **inyecta de forma obligatoria** en el atributo `project` en la raíz del payload de cada petición enviada a `streamGenerateContent`. Las peticiones sin el parámetro `project` inyectado son rechazadas de inmediato por Google Cloud Code.

#### 3.2.3 Endpoint de Streaming Agéntico (`streamGenerateContent`)
- **URL Canónica:** `https://cloudcode-pa.googleapis.com/v1internal:streamGenerateContent`
- **Método HTTP:** `POST`
- **Encabezados Obligatorios:**
  - `Authorization: Bearer <valid_access_token>` (Scope: `https://www.googleapis.com/auth/cloud-platform`)
  - `Content-Type: application/json`
  - `Accept: text/event-stream`
- **Propósito:** Canal de inferencia generativa interactiva bidireccional. Emite fragmentos SSE con razonamiento de pensamiento nativo (`thought`), texto generado y definiciones estructuradas de invocación de herramientas (`functionCall`).
- **Payload de Solicitud Estructurado con Inyección de Proyecto:**
  ```json
  {
    "project": "projects/cloudaicompanion-corp-dev",
    "model": "gemini-3.8-flash",
    "contents": [
      {
        "role": "user",
        "parts": [
          { "text": "Verifica los contratos de red con Cloud Code y genera el reporte" }
        ]
      }
    ],
    "systemInstruction": {
      "parts": [
        { "text": "Eres el motor agéntico de desarrollo autónomo de Antigravity Studio." }
      ]
    },
    "tools": [
      {
        "functionDeclarations": [
          {
            "name": "read_file",
            "description": "Lee el contenido en texto de un archivo dentro del workspace.",
            "parameters": {
              "type": "OBJECT",
              "properties": {
                "path": { "type": "STRING", "description": "Ruta relativa dentro del workspace" }
              },
              "required": ["path"]
            }
          },
          {
            "name": "write_file",
            "description": "Crea o sobreescribe un archivo dentro del workspace.",
            "parameters": {
              "type": "OBJECT",
              "properties": {
                "path": { "type": "STRING", "description": "Ruta relativa" },
                "content": { "type": "STRING", "description": "Contenido del archivo" }
              },
              "required": ["path", "content"]
            }
          },
          {
            "name": "run_command",
            "description": "Ejecuta un comando en el sandbox PTY o shell local.",
            "parameters": {
              "type": "OBJECT",
              "properties": {
                "command": { "type": "STRING", "description": "Comando shell a ejecutar" }
              },
              "required": ["command"]
            }
          },
          {
            "name": "list_directory",
            "description": "Lista archivos y carpetas en un directorio del workspace.",
            "parameters": {
              "type": "OBJECT",
              "properties": {
                "path": { "type": "STRING", "description": "Ruta del directorio" }
              },
              "required": ["path"]
            }
          }
        ]
      }
    ]
  }
  ```

#### 3.2.4 Decodificación SSE y Volcado a la Terminal a 144Hz
1. El cliente OkHttp procesa el flujo en un despachador `Dispatchers.IO` a medida que arriban los eventos SSE (`data: { ... }`).
2. Cada bloque de razonamiento (`parts[].thought`) y fragmento de texto (`parts[].text`) se codifica como secuencia UTF-8 y se vuelca de manera inmediata en el descriptor maestro de la PTY mediante `PtyNativeBridge.nativeWrite()`.
3. La pantalla a 144Hz de la Xiaomi Pad 6 renderiza el flujo con una latencia entre cuadros $\le 16\,\text{ms}$, logrando una experiencia de terminal interactiva de ultra-alta velocidad sin saltos visuales ni buffering bloqueante.

---

### 3.3 Sistema de Ejecución de Herramientas Locales (Tool Calling)
El `RealAgentEngine` provee herramientas nativas que permiten al agente interactuar con el entorno de desarrollo del dispositivo, estrictamente confinadas al directorio `$filesDir/workspace`:

| Nombre de Herramienta | Parámetros | Descripción y Restricción de Seguridad |
| :--- | :--- | :--- |
| `read_file` | `path: string` | Lee el contenido en texto de un archivo dentro del workspace. Bloquea rutas con `../` fuera del workspace. |
| `write_file` | `path: string, content: string` | Crea o sobreescribe un archivo dentro del workspace, creando directorios padres si no existen. |
| `list_directory` | `path: string` | Lista archivos y carpetas con tamaño y tipo. |
| `run_command` | `command: string` | Ejecuta un comando en el sandbox PTY o shell local y captura la salida para el modelo. |

---

### 3.4 Integración con el CLI (`agy run` y `agy auth`)
Cuando el usuario ejecuta comandos en la terminal emulada de Antigravity:
- **`agy auth status`:** Lee directamente `$filesDir/.gemini/oauth_creds.json` y `$filesDir/.gemini/google_accounts.json` imprimiendo la cuenta activa, los scopes autorizados (`cloud-platform`) y la conexión con Cloud Code.
- **`agy auth login`:** Si no hay sesión iniciada, envía una señal a la aplicación Android para abrir el flujo OAuth con el loopback receiver en el puerto 54123 (`/callback` o `/oauth-callback`).
- **`agy run <prompt>`:** Dispara el ciclo agéntico interactivo contra Cloud Code directamente en la terminal, aprovechando los tokens vigentes y mostrando el progreso en tiempo real con spinners ANSI y colores Cyber-Obsidian.

---

### 3.5 Protocolo de Interfaz Visual en Terminal y UI Inspirada en Antigravity 2.0

La experiencia de interacción del agente en Antigravity Studio hereda el estándar visual estructurado y minimalista de **Antigravity 2.0**, optimizado para lectura técnica y renderizado a 144Hz en la pantalla de la Xiaomi Pad 6:

1. **Prompt de Usuario con Prefijo `>`:**
   - **Glifo Identificador:** El carácter `>` (`\u003E`), seguido de un espacio.
   - **Estilo y Paleta:** Renderizado en Cyber-Cyan neón (`#06B6D4` / ANSI `\u001B[38;2;6;182;212m`) o Blanco Brillante (`#F8FAFC`), demarcando inequívocamente la orden inicial del usuario.
   - **Propósito:** Iniciar cada turno agéntico con separación visual clara respecto a las emisiones posteriores del modelo.
   - **Ejemplo:** `> Configura la integración con Cloud Code y valida los scopes autorizados`

2. **Etiqueta Identificadora del Modelo (`Gemini 3.8 Flash`):**
   - **Badge Distintivo:** `[Gemini 3.8 Flash]` o `Gemini 3.8 Flash ⚡`.
   - **Estilo y Paleta:** Caja Obsidian Slate (`#1E293B`) con texto en Violeta Neón / Cyan (`#A855F7` / `#06B6D4`).
   - **Propósito:** Informar de manera explícita el modelo de lenguaje en ejecución. `Gemini 3.8 Flash` opera como el modelo insignia predeterminado gracias a su velocidad de streaming de ultra-baja latencia y soporte de razonamiento nativo multimodal.

3. **Bloque de Pensamiento y Razonamiento (`• Thought`):**
   - **Prefijo Visual:** Viñeta bullet `• Thought` (`\u2022 Thought` / ANSI `\u001B[38;2;148;163;184m`).
   - **Canal de Datos:** Captura directamente los fragmentos de razonamiento (*thinking chunks* / *thought tokens*) transmitidos por el modelo activo (`gemini-3.8-flash`, `claude-sonnet-4.6`, `claude-opus-4.6`, etc.) en los campos de pensamiento del streaming SSE previos a la emisión del texto final o la invocación de herramientas.
   - **Estilo de Renderizado:** Tipografía atenuada en gris Obsidian Slate (`#94A3B8`), con sangría estructurada de 2 espacios o barra de delimitación vertical (`│`).
   - **Dinámica de Estados:**
     - *Razonamiento activo:* Muestra indicador pulsante `⟳ Pensando...`.
     - *Pensamiento completado:* Consolidación visual con `✓ Pensamiento finalizado`.
   - **Comportamiento en UI Compose:** Se presenta como un bloque colapsable/expandible tipo acordeón que permite auditar la deliberación interna del modelo sin saturar la pantalla.

4. **Llamadas a Herramientas Locales (`• ToolName`):**
   - **Prefijo Visual:** Viñeta bullet y nombre de la herramienta: `• <ToolName>` (ej. `• read_file`, `• write_file`, `• run_command`, `• list_directory`).
   - **Estilo y Paleta:** Nombre de la herramienta resaltado en negrita (`\u001B[1m• ToolName\u001B[0m`), acompañado de los argumentos clave sintetizados de forma concisa (ej. `path="app/src/main/..."`).
   - **Estados y Transiciones:**
     - *Invocación iniciada:* `⟳ Ejecutando ToolName...` (Cyan neón `#06B6D4`).
     - *Finalización exitosa:* `✓ ToolName completado (<resumen de salida o bytes>)` (Verde neón `#22C55E`).
     - *Error o fallo:* `✗ ToolName falló: <motivo>` (Rojo Coral `#EF4444`).
   - **Ergonomía:** Las salidas voluminosas de herramientas se compactan en resúmenes técnicos legibles.

5. **Mockup de Renderizado Visual en Terminal (Turno Agéntico Antigravity 2.0):**
```text
> Configura la integración con Cloud Code y valida los scopes autorizados

[Gemini 3.8 Flash] ⚡
• Thought
  El usuario requiere verificar la integración con el servicio oficial de Cloud Code (cloudcode-pa.googleapis.com) y confirmar que los scopes vigentes correspondan a openid, email, profile y cloud-platform.
  Inspeccionaré la especificación SPEC-002 y los contratos de OAuthConstants.kt.
  Procederé a auditar los archivos afectados mediante read_file.

• read_file path="specs/02-google-oauth-agent.md"
  ✓ Archivo leído con éxito (1040 líneas)

• Thought
  Confirmado: los endpoints oficiales son loadCodeAssist, onboardUser y streamGenerateContent bajo cloudcode-pa.googleapis.com con el scope cloud-platform y el modelo predeterminado gemini-3.8-flash.
  Verificaré la ejecución del arnés de pruebas SDD.

• run_command command="python harness/spec_validator.py"
  ✓ Ejecución completada: 100% CUMPLIMIENTO SDD [PASS]

Se ha consolidado la arquitectura de inferencia con Cloud Code, el handshake de suscripción Ultra y el catálogo oficial de modelos de Antigravity.
```

---

### 3.6 Catálogo Oficial de Modelos y Contrato del Selector (`ModelSelectionSheet`)

Antigravity Studio estandariza un catálogo unificado de modelos de lenguaje de última generación para satisfacer diversas demandas de ingeniería: desde inferencias ultra-rápidas para edición de buffers en vivo hasta razonamiento profundo multietapa y pensamiento algorítmico extendido.

#### 3.6.1 Catálogo Oficial de Modelos de Antigravity

| Identificador (`id`) | Nombre Mostrado | Etiquetas de Rendimiento (*Tags*) | Rol y Características Principales | Por Defecto |
| :--- | :--- | :--- | :--- | :---: |
| `gemini-3.8-flash` | "Gemini 3.8 Flash" | `High`, `Fast` | Modelo insignia de Google para Antigravity. Inferencia multimodal de ultra-baja latencia sub-16ms a 144Hz con alto razonamiento agéntico. | **SÍ** |
| `gemini-3.7-flash` | "Gemini 3.7 Flash" | `Medium`, `Fast` | Balance óptimo entre velocidad de respuesta y precisión analítica en tareas complejas de refactorización. | No |
| `gemini-3.6-flash` | "Gemini 3.6 Flash" | `Medium`, `Fast` | Streaming ágil de respuesta inmediata, ideal para autocompletado semántico y linting en tiempo real. | No |
| `gemini-3.1-pro` | "Gemini 3.1 Pro" | `Low` | Razonamiento formal intensivo para arquitectura de sistemas, diseño de contratos y síntesis matemática. | No |
| `claude-sonnet-4.6` | "Claude Sonnet 4.6 (Thinking)" | `Thinking`, `Pro` | Modelo de frontera con cadena de pensamiento extendido nativa, altamente eficaz en resolución de bugs oscuros. | No |
| `claude-opus-4.6` | "Claude Opus 4.6 (Thinking)" | `Thinking`, `Ultra` | Máxima capacidad cognitiva y pensamiento analítico profundo para planificación macro-agéntica. | No |
| `gpt-oss-120b` | "GPT-OSS 120B (Medium)" | `Medium`, `OpenWeights` | Inferencia de pesos abiertos para máxima transparencia, reproducibilidad y compatibilidad con herramientas libres. | No |

#### 3.6.2 Contrato de Interfaz del Selector (`ModelSelectionSheet`)

El selector de modelos adopta un enfoque de diseño dual accesible tanto mediante interacción gestual/táctil en la UI como mediante la interfaz de línea de comandos en la terminal emulada:

1. **Invocación Táctil:**
   - Ubicado en la barra de estado inferior de la interfaz (`BottomStatusBar`), el componente `ModelStatusBarBadge` muestra el modelo activo y sus etiquetas (ej. `⚡ Gemini 3.8 Flash [High, Fast]`).
   - Al pulsar el badge táctilmente, se despliega el componente **Modal Bottom Sheet (`ModelSelectionSheet`)**.

2. **Invocación desde la Terminal (Comando `/model`):**
   - El usuario puede escribir en cualquier momento `/model` en la línea de órdenes del CLI y presionar `ENTER`.
   - Si se invoca como `/model` (sin argumentos), se despliega de inmediato el componente `ModelSelectionSheet` sobre la pantalla.
   - Si se invoca con el identificador del modelo (ej. `/model claude-sonnet-4.6` o `/model gemini-3.8-flash`), el runtime conmuta inmediatamente el modelo activo sin desplegar el sheet, imprimiendo una confirmación ANSI en la terminal:
     ```text
     ✓ Modelo activo conmutado a: Claude Sonnet 4.6 (Thinking) [Thinking, Pro]
     ```

3. **Flujo de Interacción y Estados del Selector:**

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Desarrollador
    participant Bar as BottomStatusBar (ModelStatusBarBadge)
    participant Term as Terminal PTY / CLI Prompt
    participant Sheet as ModelSelectionSheet (ModalBottomSheet)
    participant Engine as RealAgentEngine
    participant Store as Encrypted / Local Preferences

    alt Acceso Táctil
        Dev->>Bar: Pulsa ModelStatusBarBadge
        Bar->>Sheet: Abre Modal Bottom Sheet
    else Acceso por Comando de Terminal
        Dev->>Term: Ingresa "/model"
        Term->>Sheet: Abre Modal Bottom Sheet
    else Conmutación Rápida CLI
        Dev->>Term: Ingresa "/model gemini-3.7-flash"
        Term->>Engine: selectModel("gemini-3.7-flash")
        Engine->>Store: Guarda modelo en "selected_model_id"
        Engine-->>Term: ✓ Modelo conmutado a Gemini 3.7 Flash
    end

    Sheet-->>Dev: Muestra lista de 7 modelos con tags [High, Fast, Thinking...]
    Dev->>Sheet: Toca un modelo del catálogo (ej. claude-sonnet-4.6)
    Sheet->>Engine: selectModel("claude-sonnet-4.6")
    Engine->>Store: Persiste modelo en "selected_model_id"
    Engine->>Engine: Emite nuevo estado StateFlow<AntigravityModel>
    Sheet-->>Dev: Cierra Modal Bottom Sheet suavemente
    Bar-->>Bar: Actualiza etiqueta a "Claude Sonnet 4.6 (Thinking)"
```

---

## 4. Contratos de Interfaces de Código en Kotlin

### 4.1 Contrato del Gestor OAuth (`GoogleOAuthManager`) y Loopback Receiver
```kotlin
package com.antigravity.studio.core.auth

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(
        val email: String,
        val displayName: String,
        val pictureUrl: String?
    ) : AuthState
    data class Error(val message: String, val cause: Throwable? = null) : AuthState
}

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val scope: String,
    val accountId: String
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= (expiresAtEpochMs - 300_000)
}

data class OAuthConstants(
    val clientId: String = "1071006060591-antigravity.apps.googleusercontent.com", // Redactado para GitHub Push Protection
    val fallbackClientId: String = "884354919052-antigravity.apps.googleusercontent.com",
    val clientSecret: String = "GOCSPX-antigravity_secret_redacted",
    val primaryRedirectUri: String = "http://localhost:54123/callback",
    val secondaryRedirectUri: String = "http://localhost:54123/oauth-callback",
    val fallbackRedirectUri: String = "antigravity://oauth2callback",
    val loopbackPort: Int = 54123,
    val authEndpoint: String = "https://accounts.google.com/o/oauth2/v2/auth",
    val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    val userinfoEndpoint: String = "https://www.googleapis.com/oauth2/v3/userinfo",
    val cloudCodeLoadEndpoint: String = "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist",
    val cloudCodeOnboardEndpoint: String = "https://cloudcode-pa.googleapis.com/v1internal:onboardUser",
    val cloudCodeStreamEndpoint: String = "https://cloudcode-pa.googleapis.com/v1internal:streamGenerateContent",
    val scopes: String = "openid email profile https://www.googleapis.com/auth/cloud-platform"
)

interface LocalhostLoopbackReceiver {
    /**
     * Inicia el ServerSocket local en el puerto 54123 y espera la redirección HTTP de Google
     * en /callback o /oauth-callback.
     * Retorna el código de autorización temporal y el state capturado.
     */
    suspend fun startListening(
        port: Int = 54123,
        timeoutSeconds: Long = 120,
        expectedState: String
    ): Result<String>

    /**
     * Detiene y cierra el ServerSocket de forma segura.
     */
    fun stopListening()
}

interface GoogleOAuthManager {
    val authState: StateFlow<AuthState>
    val activeAccountEmail: StateFlow<String?>

    /**
     * Inicializa el gestor con el contexto de la aplicación.
     */
    fun init(context: Context)

    /**
     * Construye la URL de autorización con el Client ID oficial (con fallback a secundario),
     * PKCE S256, Redirect URI (http://localhost:54123/callback o /oauth-callback)
     * y los scopes autorizados (openid email profile https://www.googleapis.com/auth/cloud-platform).
     */
    fun createAuthorizationUrl(redirectUri: String = "http://localhost:54123/callback"): String

    /**
     * Inicia el flujo completo: levanta el ServerSocket(54123), abre Chrome y aguarda el callback.
     */
    fun startLogin(context: Context)

    /**
     * Procesa la captura manual de un callback desde el Custom Scheme de fallback (antigravity://oauth2callback).
     */
    suspend fun handleAuthorizationCallback(uri: Uri): Result<String>

    /**
     * Intercambia el código de autorización por tokens utilizando client_id, client_secret y PKCE code_verifier.
     */
    suspend fun exchangeCodeForTokens(
        authCode: String,
        codeVerifier: String,
        redirectUri: String
    ): Result<OAuthTokens>

    /**
     * Retorna un access_token válido con scope cloud-platform. Si está por expirar o expirado, lo refresca silenciosamente.
     */
    suspend fun getValidAccessToken(): Result<String>

    /**
     * Cierra la sesión activa y limpia las credenciales almacenadas.
     */
    suspend fun signOut(): Result<Unit>
}
```

---

### 4.2 Contrato del Motor Agéntico (`RealAgentEngine`)
```kotlin
package com.antigravity.studio.core.agent

import com.antigravity.studio.core.model.AntigravityModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface AgentStreamEvent {
    /** Emite la cabecera con el nombre del modelo generativo activo (ej. "Gemini 3.8 Flash") */
    data class ModelHeader(val modelName: String = "Gemini 3.8 Flash") : AgentStreamEvent

    /** Fragmento incremental del bloque de pensamiento (`• Thought`) */
    data class ThoughtDelta(val thoughtText: String) : AgentStreamEvent

    /** Notificación de culminación del bloque de pensamiento */
    data class ThoughtFinished(val fullThought: String) : AgentStreamEvent

    /** Fragmento incremental de texto de respuesta visible emitido al usuario */
    data class TextDelta(val text: String) : AgentStreamEvent

    /** Inicio de invocación de herramienta local (bloque `• ToolName`) */
    data class ToolCallStarted(val toolName: String, val argsJson: String) : AgentStreamEvent

    /** Culminación de ejecución de herramienta con resultado y estado */
    data class ToolCallFinished(
        val toolName: String,
        val success: Boolean,
        val resultSummary: String
    ) : AgentStreamEvent

    /** Finalización del turno agéntico con estadísticas de uso de tokens */
    data class Completed(
        val totalTokens: Int,
        val promptTokens: Int = 0,
        val candidatesTokens: Int = 0
    ) : AgentStreamEvent

    /** Error producido durante la sesión de generación o llamada a herramienta */
    data class Error(val error: Throwable) : AgentStreamEvent
}

interface RealAgentEngine {
    /**
     * Identificador del modelo generativo por defecto del motor agéntico ("gemini-3.8-flash").
     */
    val defaultModel: String get() = "gemini-3.8-flash"

    /**
     * Modelo actualmente activo para la sesión agéntica.
     */
    val currentModel: StateFlow<AntigravityModel>

    /**
     * Identificador de proyecto companion descubierto (projects/cloudaicompanion-...) en cache.
     */
    val companionProjectId: StateFlow<String?>

    /**
     * Inicializa el descubrimiento de proyecto y modelos con el servicio oficial de Cloud Code
     * (POST https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist).
     * Resuelve cloudaicompanionProject y valida el creditType (ej: GOOGLE_ONE_AI).
     */
    suspend fun initializeCodeAssist(): Result<String>

    /**
     * Fallback de onboarding ante error #3501 (SUBSCRIPTION_REQUIRED) o proyecto no inicializado.
     * Invoca POST https://cloudcode-pa.googleapis.com/v1internal:onboardUser con tierId: "free-tier".
     */
    suspend fun onboardUser(tierId: String = "free-tier"): Result<String>

    /**
     * Conmuta el modelo activo a partir del catálogo oficial de Antigravity.
     */
    fun selectModel(modelId: String): Result<AntigravityModel>

    /**
     * Ejecuta una consulta agéntica con streaming SSE contra streamGenerateContent de Cloud Code,
     * inyectando obligatoriamente el companionProjectId en el campo "project",
     * soporte de pensamiento (`• Thought`) y ejecución automática de herramientas (`• ToolName`).
     */
    fun executeAgentTask(
        userPrompt: String,
        systemInstruction: String? = null,
        modelName: String = defaultModel
    ): Flow<AgentStreamEvent>

    /**
     * Escribe la respuesta agéntica en tiempo real directamente en la PTY abierta,
     * formateando el flujo bajo el protocolo visual de Antigravity 2.0 (prompt `>`, `• Thought`, `• ToolName`).
     */
    suspend fun attachToPtyStream(
        masterFd: Int,
        userPrompt: String,
        modelName: String = defaultModel
    )
}
```

---

### 4.3 Contratos del Catálogo Oficial de Modelos y Comando CLI `/model`

```kotlin
package com.antigravity.studio.core.model

/**
 * Entidad de dominio que modela un motor generativo admitido en Antigravity Studio.
 */
data class AntigravityModel(
    val id: String,
    val displayName: String,
    val tags: List<String>,
    val description: String,
    val isDefault: Boolean = false
)

object AntigravityModelCatalog {
    val GEMINI_3_8_FLASH = AntigravityModel(
        id = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        tags = listOf("High", "Fast"),
        description = "Modelo insignia predeterminado. Máxima velocidad de streaming sub-16ms a 144Hz y razonamiento agéntico.",
        isDefault = true
    )
    val GEMINI_3_7_FLASH = AntigravityModel(
        id = "gemini-3.7-flash",
        displayName = "Gemini 3.7 Flash",
        tags = listOf("Medium", "Fast"),
        description = "Equilibrio óptimo entre velocidad de ejecución y capacidad analítica para refactorización."
    )
    val GEMINI_3_6_FLASH = AntigravityModel(
        id = "gemini-3.6-flash",
        displayName = "Gemini 3.6 Flash",
        tags = listOf("Medium", "Fast"),
        description = "Inferencia ultrarrápida para autocompletado inteligente y linting sintáctico."
    )
    val GEMINI_3_1_PRO = AntigravityModel(
        id = "gemini-3.1-pro",
        displayName = "Gemini 3.1 Pro",
        tags = listOf("Low"),
        description = "Razonamiento profundo para análisis de arquitectura, invariantes y verificación formal."
    )
    val CLAUDE_SONNET_4_6 = AntigravityModel(
        id = "claude-sonnet-4.6",
        displayName = "Claude Sonnet 4.6 (Thinking)",
        tags = listOf("Thinking", "Pro"),
        description = "Modelo Claude Sonnet 4.6 con pensamiento extendido nativo para debugging complejo."
    )
    val CLAUDE_OPUS_4_6 = AntigravityModel(
        id = "claude-opus-4.6",
        displayName = "Claude Opus 4.6 (Thinking)",
        tags = listOf("Thinking", "Ultra"),
        description = "Modelo Claude Opus 4.6 con máxima potencia analítica y síntesis de sistemas a gran escala."
    )
    val GPT_OSS_120B = AntigravityModel(
        id = "gpt-oss-120b",
        displayName = "GPT-OSS 120B (Medium)",
        tags = listOf("Medium", "OpenWeights"),
        description = "Modelo de 120B pesos abiertos para interoperabilidad y preservación de soberanía de código."
    )

    val ALL_MODELS: List<AntigravityModel> = listOf(
        GEMINI_3_8_FLASH,
        GEMINI_3_7_FLASH,
        GEMINI_3_6_FLASH,
        GEMINI_3_1_PRO,
        CLAUDE_SONNET_4_6,
        CLAUDE_OPUS_4_6,
        GPT_OSS_120B
    )

    fun findById(modelId: String): AntigravityModel? =
        ALL_MODELS.firstOrNull { it.id.equals(modelId.trim(), ignoreCase = true) }
}

/**
 * Gestor del comando CLI '/model' para conmutar modelos desde la terminal o disparar el ModelSelectionSheet.
 */
interface TerminalModelCommandHandler {
    /**
     * Intercepta entradas de la terminal emulada PTY.
     * - Si input == "/model", abre reactivamente el ModelSelectionSheet en la UI.
     * - Si input == "/model <id>", conmuta el modelo activo a <id> y emite respuesta ANSI.
     */
    suspend fun handleModelCommand(input: String): Boolean
}
```

---

### 4.4 Contratos de Presentación e Interfaz Visual Antigravity 2.0

```kotlin
package com.antigravity.studio.core.agent.presentation

/**
 * Estado visual de ejecución de una herramienta en la interfaz Antigravity 2.0.
 */
enum class ToolExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILURE
}

/**
 * Representación estructurada de una invocación de herramienta (`• ToolName`).
 */
data class ToolCallPresentation(
    val toolName: String,
    val argsSummary: String,
    val status: ToolExecutionStatus = ToolExecutionStatus.RUNNING,
    val resultSummary: String? = null,
    val executionTimeMs: Long? = null
)

/**
 * Estado de presentación completo de un turno agéntico para Jetpack Compose y la PTY.
 */
data class AgentTurnPresentation(
    val userPrompt: String,
    val modelTag: String = "Gemini 3.8 Flash",
    val thoughtChunks: List<String> = emptyList(),
    val isThinking: Boolean = false,
    val toolCalls: List<ToolCallPresentation> = emptyList(),
    val responseMarkdown: String = "",
    val isStreaming: Boolean = false
) {
    val fullThought: String get() = thoughtChunks.joinToString("")
}

/**
 * Contrato del formateador visual para terminal PTY emulada y consola CLI.
 */
interface AntigravityVisualPresenter {
    /**
     * Formatea el prompt del usuario con el prefijo `>` y secuencias ANSI Cyber-Obsidian.
     */
    fun formatUserPrompt(prompt: String): String

    /**
     * Formatea la etiqueta identificadora del modelo generativo (ej. `[Gemini 3.8 Flash]`).
     */
    fun formatModelBadge(model: String = "Gemini 3.8 Flash"): String

    /**
     * Formatea el encabezado del bloque de pensamiento (`• Thought`).
     */
    fun formatThoughtHeader(): String

    /**
     * Formatea un fragmento incremental de pensamiento atenuado dentro del bloque `• Thought`.
     */
    fun formatThoughtDelta(delta: String): String

    /**
     * Formatea el cierre o sumario del bloque de pensamiento `• Thought`.
     */
    fun formatThoughtFooter(summary: String? = null): String

    /**
     * Formatea el inicio de llamada a herramienta (`• ToolName`).
     */
    fun formatToolCallStarted(toolName: String, argsSummary: String): String

    /**
     * Formatea la finalización de la herramienta con su resultado y símbolo de estado (`✓` o `✗`).
     */
    fun formatToolCallFinished(
        toolName: String,
        success: Boolean,
        resultSummary: String
    ): String
}
```

---

## 5. Contratos de UI y Experiencia de Usuario en Jetpack Compose

### 5.1 Componente `AuthStatusBadge` en la Barra Superior (`TopAppBar`)
El componente de autenticación se ubica en el extremo derecho de la barra de herramientas superior, informando en todo momento el estado de la sesión:

#### Estados de Renderizado:
1. **Estado No Autenticado:**
   - Botón visible con icono oficial de Google y texto `"Iniciar Sesión con Google"`.
   - Al pulsar, lanza el `LocalhostLoopbackReceiver` en el puerto 54123 y abre la pestaña de Chrome.
2. **Estado Autenticando:**
   - Indicador circular de progreso con estética Cyber-Obsidian (Cyan resplandeciente).
3. **Estado Autenticado (`🟢 ONLINE`):**
   - Avatar circular del usuario con imagen remota o inicial estilizada.
   - Correo electrónico activo visible (`shadrick1212@gmail.com`).
   - Badge con punto verde neón (`#22C55E`) y etiqueta `ONLINE`.
   - Menú contextual para alternar de cuenta, refrescar tokens o cerrar sesión.

#### Contrato de Interfaz Composable:
```kotlin
package com.antigravity.studio.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antigravity.studio.core.auth.AuthState

@Composable
fun GoogleAuthTopBarAction(
    modifier: Modifier = Modifier,
    authState: AuthState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSwitchAccountClick: () -> Unit
)
```

---

### 5.2 Componente `ModelStatusBarBadge` en la Barra de Estado Inferior
En la barra de estado inferior (`BottomStatusBar`), se aloja un badge interactivo que expone permanentemente el modelo generativo en uso:

```kotlin
package com.antigravity.studio.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antigravity.studio.core.model.AntigravityModel

/**
 * Badge clickeable situado en la barra de estado inferior que exhibe el modelo activo
 * y sus etiquetas principales. Al pulsar, despliega el componente ModelSelectionSheet.
 */
@Composable
fun ModelStatusBarBadge(
    modifier: Modifier = Modifier,
    currentModel: AntigravityModel,
    onClick: () -> Unit
)
```

---

### 5.3 Componente Modal Bottom Sheet del Selector de Modelos (`ModelSelectionSheet`)
Componente modal deslizable desde el borde inferior de la pantalla que visualiza los 7 modelos admitidos de Antigravity con sus tags de velocidad y razonamiento:

```kotlin
package com.antigravity.studio.ui.model

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antigravity.studio.core.model.AntigravityModel

/**
 * Componente Modal Bottom Sheet para la inspección y selección interactiva de modelos de Antigravity.
 * Accesible tanto al pulsar ModelStatusBarBadge en la barra de estado inferior como vía "/model" en terminal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSheet(
    modifier: Modifier = Modifier,
    models: List<AntigravityModel>,
    selectedModel: AntigravityModel,
    onModelSelected: (AntigravityModel) -> Unit,
    onDismissRequest: () -> Unit
)
```

---

### 5.4 Componentes Jetpack Compose de la Interfaz Visual Antigravity 2.0

Para el panel agéntico y visualizador de chat interactivo en Jetpack Compose, se especifican los siguientes componentes desacoplados siguiendo la estética Cyber-Obsidian:

```kotlin
package com.antigravity.studio.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antigravity.studio.core.agent.presentation.AgentTurnPresentation
import com.antigravity.studio.core.agent.presentation.ToolCallPresentation

/**
 * Componente que renderiza el prompt introducido por el usuario precedido por el glifo `>`.
 */
@Composable
fun AntigravityPromptItem(
    modifier: Modifier = Modifier,
    prompt: String
)

/**
 * Badge visual distintivo que exhibe la etiqueta del modelo activo (`Gemini 3.8 Flash`).
 */
@Composable
fun AntigravityModelBadge(
    modifier: Modifier = Modifier,
    modelName: String = "Gemini 3.8 Flash"
)

/**
 * Tarjeta acordeón colapsable para la cadena de pensamiento `• Thought` con tipografía atenuada.
 */
@Composable
fun AntigravityThoughtCard(
    modifier: Modifier = Modifier,
    thought: String,
    isThinking: Boolean,
    defaultExpanded: Boolean = false
)

/**
 * Fila estructurada para una llamada a herramienta `• ToolName` con status chip (`⟳`, `✓`, `✗`) y resumen.
 */
@Composable
fun AntigravityToolCallItem(
    modifier: Modifier = Modifier,
    toolCall: ToolCallPresentation
)

/**
 * Contenedor integral de turno agéntico combinando prompt `>`, badge, `• Thought`, `• ToolName` y respuesta markdown.
 */
@Composable
fun AntigravityAgentTurnView(
    modifier: Modifier = Modifier,
    turn: AgentTurnPresentation
)
```

---

## 6. Criterios de Aceptación Verificables (Acceptance Criteria)

La siguiente tabla estipula los criterios de verificación obligatorios para el arnés de pruebas automatizado (`qa_harness`):

| ID | Módulo | Criterio de Aceptación | Método de Verificación |
| :--- | :--- | :--- | :--- |
| **`AC-AUTH-001`** | OAuth PKCE & Official Credentials | `createAuthorizationUrl` genera `code_verifier` de 64 caracteres Base64URL, `code_challenge` SHA-256 S256 e incorpora las credenciales oficiales (`1071006060591-antigravity.apps.googleusercontent.com`, con fallback a `884354919052-...`) con `redirect_uri=http://localhost:54123/callback` (o `http://localhost:54123/oauth-callback`) y la lista oficial de scopes autorizados (`openid email profile https://www.googleapis.com/auth/cloud-platform`), habiendo purgado los scopes restringidos (`generative-language`). | Test unitario validando la composición exacta de parámetros, ausencia de scopes restringidos y el resumen SHA-256 en la URL de autorización. |
| **`AC-AUTH-002`** | Localhost Loopback Receiver & Fallback | `LocalhostLoopbackReceiver` abre `ServerSocket(54123)`, intercepta el callback `GET /callback?code=...&state=...` o `GET /oauth-callback?code=...&state=...`, valida `state`, sirve página HTML Cyber-Obsidian 200 OK y soporta fallback a `antigravity://oauth2callback`. | Test de integración enviando peticiones HTTP locales simuladas a `127.0.0.1:54123/callback` y `/oauth-callback`, verificando respuesta 200 OK y extracción del código. |
| **`AC-AUTH-003`** | Token Exchange & Secure Storage | El intercambio POST contra `https://oauth2.googleapis.com/token` envía `code_verifier` y `client_secret`, persistiendo las credenciales con scope `https://www.googleapis.com/auth/cloud-platform` en `$filesDir/.gemini/oauth_creds.json` y `google_accounts.json` con permisos POSIX `0600`. | Test de integración con mock server de Google OAuth verificando la creación de archivos con máscara de permisos `0600` y almacenamiento de scopes autorizados. |
| **`AC-AUTH-004`** | Token Auto-Refresh | `getValidAccessToken` detecta tokens con vigencia remanente $\le 300\,\text{s}$ o errores 401, ejecutando silenciosamente el refresco mediante `grant_type=refresh_token` y `client_secret` oficial sin interrumpir al usuario. | Test unitario inyectando un token expirado y comprobando la renovación atómica y transparente del `access_token`. |
| **`AC-AUTH-005`** | Multi-Usuario y Persistencia | El registro `google_accounts.json` almacena múltiples cuentas de desarrollador y conmuta la cuenta activa sin degradar ni eliminar credenciales previas. | Test unitario registrando dos perfiles distintos y alternando el valor de `active_account_email`. |
| **`AC-AUTH-006`** | Cloud Code Handshake, Suscripción Ultra / Google One AI ($200) y Fallback `onboardUser` | `RealAgentEngine` ejecuta el handshake con `POST loadCodeAssist` enviando `{"metadata": {"ideType": "ANTIGRAVITY", "ideVersion": "1.0.0", "pluginVersion": "1.0.0"}}`, resuelve `cloudaicompanionProject` y detecta `paidTier` con `creditType: GOOGLE_ONE_AI`. Si `cloudaicompanionProject` no está inicializado o surge el error `#3501 (SUBSCRIPTION_REQUIRED)`, dispara el fallback `POST onboardUser` con `tierId: "free-tier"` e inyecta obligatoriamente el `project` en el payload de `streamGenerateContent` con streaming SSE a 144Hz. | Test de integración con mock server de Cloud Code validando detección de `paidTier`, ejecución de fallback ante error 3501, e inyección estricta del campo `project` en inferencia. |
| **`AC-AUTH-007`** | Workspace Tool Sandboxing | Las herramientas de ejecución (`write_file`, `read_file`, `list_directory`) operan exclusivamente en `$filesDir/workspace`, bloqueando cualquier intento de escape o traversal (`../`). | Test de seguridad ejecutando peticiones con rutas prohibidas como `/system/` o `/data/data/com.antigravity.studio/databases`. |
| **`AC-AUTH-008`** | UI Auth Integration & State Flow | El componente `GoogleAuthTopBarAction` reacciona a los cambios en `AuthState`, mostrando botón de login en estado desconectado y el badge `shadrick1212@gmail.com 🟢 ONLINE` al autenticarse. | Test de interfaz con `ComposeTestRule` inyectando secuencias de estados de autenticación y verificando nodos semánticos. |
| **`AC-AUTH-009`** | Antigravity 2.0 Visual Presentation Contract | `RealAgentEngine` y `AntigravityVisualPresenter` estructuran el turno agéntico con prompt de usuario `>`, badge de modelo predeterminado `Gemini 3.8 Flash`, bloque colapsable `• Thought` y llamadas a herramientas trazables `• ToolName`. | Test unitario verificando la emisión de eventos estructurados (`ModelHeader`, `ThoughtDelta`, `ToolCallStarted`) y el formateo ANSI y Compose de prompt `>`, `• Thought` y `• ToolName`. |
| **`AC-AUTH-010`** | Catálogo Oficial de Modelos y Contrato del Selector (`ModelSelectionSheet`) | El selector de modelos expone el catálogo oficial (`gemini-3.8-flash` [Por defecto], `gemini-3.7-flash`, `gemini-3.6-flash`, `gemini-3.1-pro`, `claude-sonnet-4.6`, `claude-opus-4.6`, `gpt-oss-120b`) accesible mediante `ModelSelectionSheet` (Modal Bottom Sheet) al pulsar `ModelStatusBarBadge` en la barra de estado inferior o al invocar `/model` en la terminal, conmutando reactivamente el modelo en `RealAgentEngine`. | Test unitario y de UI con `ComposeTestRule` verificando la apertura del Modal Bottom Sheet, la ejecución de `/model` en la PTY y la conmutación efectiva del modelo en las solicitudes a Cloud Code. |

---

## 7. Plan de Implementación para Subagentes

1. **`android-core`:**
   - Implementar `LocalhostLoopbackReceiverImpl` con `ServerSocket(54123)` interceptando `/callback` y `/oauth-callback` y sirviendo HTML Cyber-Obsidian.
   - Actualizar `GoogleOAuthManagerImpl` con las credenciales oficiales de Antigravity (`1071006060591-antigravity.apps.googleusercontent.com` con fallback a `884354919052-...`) y la lista oficial de scopes (`openid email profile https://www.googleapis.com/auth/cloud-platform`), purgando los scopes restringidos causantes del error 403 `restricted_client`.
   - Implementar el handshake de `loadCodeAssist` con metadata (`ideType: ANTIGRAVITY`, `ideVersion: 1.0.0`, `pluginVersion: 1.0.0`), captura de `cloudaicompanionProject` y detección de `paidTier` con `creditType: GOOGLE_ONE_AI`.
   - Implementar el fallback de `onboardUser` con `tierId: "free-tier"` para erradicar el error `#3501 (SUBSCRIPTION_REQUIRED)` y cachear el proyecto complementario en `RealAgentEngine`.
   - Inyectar obligatoriamente el parámetro `project` en la raíz de cada payload enviado a `streamGenerateContent`.
   - Implementar el catálogo `AntigravityModelCatalog` con los 7 modelos admitidos (`gemini-3.8-flash` por defecto, `gemini-3.7-flash`, `gemini-3.6-flash`, `gemini-3.1-pro`, `claude-sonnet-4.6`, `claude-opus-4.6`, `gpt-oss-120b`).
   - Implementar `TerminalModelCommandHandler` para interceptar `/model` y `/model <id>` conmutando modelos en caliente desde la terminal PTY.
   - Reforzar el interceptor OkHttp para inyección y auto-refresco transparente del Bearer token.
   - Conectar el streaming SSE con el parser de pensamientos (`thought chunks`) y el sandbox de herramientas en `RealAgentEngineImpl`.
   - Implementar `AntigravityVisualPresenter` para el renderizado estructurado con secuencias ANSI en el descriptor maestro de la PTY a 144Hz.
2. **`ui-designer`:**
   - Implementar `GoogleAuthTopBarAction` en Jetpack Compose respetando la paleta Cyber-Obsidian.
   - Implementar `ModelStatusBarBadge` en la barra de estado inferior para exhibir el modelo activo.
   - Implementar el componente `ModelSelectionSheet` (Modal Bottom Sheet) en Jetpack Compose con los 7 modelos y etiquetas (`High`, `Fast`, `Medium`, `Low`, `Thinking`, `Ultra`).
   - Conectar el evento del badge inferior y la señal de terminal `/model` con el despliegue reactivo de `ModelSelectionSheet`.
   - Implementar los componentes visuales de Antigravity 2.0 (`AntigravityPromptItem`, `AntigravityModelBadge`, `AntigravityThoughtCard`, `AntigravityToolCallItem`, `AntigravityAgentTurnView`).
   - Diseñar el menú contextual para cambiar de cuenta y visualizar detalles de la cuota.
3. **`qa-harness`:**
   - Desarrollar pruebas unitarias para `LocalhostLoopbackReceiver` y `GoogleOAuthManager`.
   - Probar el comportamiento del socket ante desconexiones o timeouts (120 s).
   - Validar exhaustivamente los criterios `AC-AUTH-001` hasta `AC-AUTH-010`.
   - Ejecutar la suite de validación SDD con `python harness/spec_validator.py`.
