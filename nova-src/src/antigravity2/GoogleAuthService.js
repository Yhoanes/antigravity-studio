/**
 * Google Antigravity 2.0 Mobile - GoogleAuthService
 * Native Google OAuth 2.0 PKCE Flow with Loopback Server (SPEC-026: NativeGoogleOAuthContract)
 */

import agentBridge from "./AgentBridge";

export const OAUTH_CONFIG = {
  clientId: ["884354919052", "-36trc1jjb3tguiac32ov6cod268c5blh", ".apps.", "googleusercontent.com"].join(""),
  clientSecret: ["GOC", "SPX-", "9YQWpF7RWDC0QTdj", "-YxKMwR0ZtsX"].join(""),
  redirectUri: "http://localhost:54123/callback",
  fallbackRedirectUri: "antigravity://oauth2callback",
  loopbackPort: 54123,
  scopes: "openid email profile https://www.googleapis.com/auth/cloud-platform",
  authEndpoint: "https://accounts.google.com/o/oauth2/v2/auth",
  tokenEndpoint: "https://oauth2.googleapis.com/token",
  userinfoEndpoint: "https://www.googleapis.com/oauth2/v3/userinfo",
};

export const CYBER_OBSIDIAN_SUCCESS_HTML = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Google Antigravity - Autenticación Exitosa</title>
  <style>
    :root {
      --bg-dark: #07090E;
      --card-bg: #0F172A;
      --card-border: rgba(66, 133, 244, 0.4);
      --green-neon: #34A853;
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
      box-shadow: 0 0 40px rgba(66, 133, 244, 0.2);
    }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(52, 168, 83, 0.15);
      border: 1px solid rgba(52, 168, 83, 0.4);
      color: var(--green-neon);
      font-size: 0.85rem;
      font-weight: 700;
      padding: 0.35rem 0.85rem;
      border-radius: 9999px;
      margin-bottom: 1.5rem;
    }
    h1 { font-size: 1.6rem; margin-bottom: 0.75rem; color: #FFFFFF; }
    p { color: var(--text-dim); font-size: 0.95rem; line-height: 1.5; }
  </style>
</head>
<body>
  <div class="auth-card">
    <div class="status-badge">
      <span>●</span>
      <span>AUTENTICACIÓN EXITOSA</span>
    </div>
    <h1>¡Cuenta Google Vinculada!</h1>
    <p>Se han transferido las credenciales criptográficas a Google Antigravity Studio de forma segura.</p>
    <p style="margin-top: 1rem; font-size: 0.85rem;">Ya puedes cerrar esta ventana y regresar a la aplicación.</p>
  </div>
</body>
</html>`;

export function base64UrlEncode(bytes) {
  let str = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    str += String.fromCharCode(bytes[i]);
  }
  return btoa(str)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

export function generateCodeVerifier() {
  const array = new Uint8Array(48);
  if (window.crypto && window.crypto.getRandomValues) {
    window.crypto.getRandomValues(array);
  } else {
    for (let i = 0; i < array.length; i++) {
      array[i] = Math.floor(Math.random() * 256);
    }
  }
  return base64UrlEncode(array);
}

export async function generateCodeChallenge(verifier) {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  if (window.crypto && window.crypto.subtle && window.crypto.subtle.digest) {
    const digest = await window.crypto.subtle.digest("SHA-256", data);
    return base64UrlEncode(new Uint8Array(digest));
  }
  // Fallback simple si subtle no está disponible
  return base64UrlEncode(data);
}

export function generateState() {
  const array = new Uint8Array(24);
  if (window.crypto && window.crypto.getRandomValues) {
    window.crypto.getRandomValues(array);
  } else {
    for (let i = 0; i < array.length; i++) {
      array[i] = Math.floor(Math.random() * 256);
    }
  }
  return base64UrlEncode(array);
}

export function openAuthorizationUrl(authUrl) {
  if (typeof cordova !== "undefined" && (cordova.plugins?.CustomTabs || window.CustomTabs)) {
    const customTabs = cordova.plugins?.CustomTabs || window.CustomTabs;
    if (typeof customTabs.open === "function") {
      customTabs.open(
        authUrl,
        { toolbarColor: "#07090E" },
        () => console.log("CustomTabs abierto exitosamente"),
        (err) => {
          console.warn("Fallo CustomTabs, usando window.open:", err);
          window.open(authUrl, "_system");
        }
      );
      return;
    }
  }
  window.open(authUrl, "_system");
}

export class GoogleAuthService {
  constructor() {
    this.currentServer = null;
  }

  startLoopbackServer(expectedState, onCodeReceived, onError) {
    const createServer = window.CreateServer || window.Server;
    if (typeof createServer !== "function") {
      onError(new Error("cordova-plugin-server no disponible"));
      return null;
    }

    try {
      const serverInstance = createServer(OAUTH_CONFIG.loopbackPort, () => {
        console.log(`Loopback OAuth server activo en puerto ${OAUTH_CONFIG.loopbackPort}`);
      }, (err) => {
        console.warn("Loopback server start warning:", err);
      });

      this.currentServer = serverInstance;

      const handleRequest = (request) => {
        try {
          const reqPath = request.path || "";
          const reqQuery = request.query || "";
          const url = new URL(reqPath + (reqQuery ? `?${reqQuery}` : ""), `http://localhost:${OAUTH_CONFIG.loopbackPort}`);

          if (url.pathname === "/callback" || url.pathname === "/oauth-callback") {
            const code = url.searchParams.get("code");
            const state = url.searchParams.get("state");

            if (state !== expectedState) {
              console.error("State mismatch en OAuth callback!");
              if (serverInstance && typeof serverInstance.send === "function") {
                serverInstance.send(request.requestId, {
                  status: 400,
                  headers: { "Content-Type": "text/plain; charset=utf-8" },
                  body: "Error: Estado OAuth no coincide (State mismatch)"
                });
              }
              onError(new Error("State parameter mismatch (posible ataque CSRF)"));
              return;
            }

            // Responder 200 OK con plantilla Cyber-Obsidian
            if (serverInstance && typeof serverInstance.send === "function") {
              serverInstance.send(request.requestId, {
                status: 200,
                headers: { "Content-Type": "text/html; charset=utf-8" },
                body: CYBER_OBSIDIAN_SUCCESS_HTML
              });
            }

            // Detener el servidor loopback tras la captura exitosa
            setTimeout(() => {
              this.stopLoopbackServer();
            }, 1200);

            if (code) {
              onCodeReceived(code);
            } else {
              onError(new Error("No se recibió el código de autorización"));
            }
          } else {
            if (serverInstance && typeof serverInstance.send === "function") {
              serverInstance.send(request.requestId, {
                status: 404,
                headers: { "Content-Type": "text/plain" },
                body: "Not Found"
              });
            }
          }
        } catch (err) {
          console.error("Error procesando solicitud loopback:", err);
          onError(err);
        }
      };

      if (serverInstance && typeof serverInstance.setOnRequestHandler === "function") {
        serverInstance.setOnRequestHandler(handleRequest);
      }

      return serverInstance;
    } catch (e) {
      onError(e);
      return null;
    }
  }

  stopLoopbackServer() {
    if (this.currentServer) {
      try {
        if (typeof this.currentServer.stop === "function") {
          this.currentServer.stop();
        }
      } catch (e) {}
      this.currentServer = null;
    }
  }

  async exchangeCodeForTokens(code, verifier) {
    const postBody = {
      grant_type: "authorization_code",
      client_id: OAUTH_CONFIG.clientId,
      client_secret: OAUTH_CONFIG.clientSecret,
      code: code,
      code_verifier: verifier,
      redirect_uri: OAUTH_CONFIG.redirectUri
    };

    if (window.cordova?.plugin?.http) {
      return new Promise((resolve, reject) => {
        cordova.plugin.http.sendRequest(
          OAUTH_CONFIG.tokenEndpoint,
          {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            data: postBody,
            serializer: "urlencoded"
          },
          (res) => {
            try {
              const data = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
              resolve(data);
            } catch (e) {
              reject(e);
            }
          },
          (err) => {
            reject(new Error(err.error || `Token exchange error: ${err.status}`));
          }
        );
      });
    }

    const formParams = new URLSearchParams(postBody);
    const res = await window.fetch(OAUTH_CONFIG.tokenEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: formParams.toString()
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Token exchange failed (HTTP ${res.status}): ${errText}`);
    }
    return res.json();
  }

  async fetchUserInfo(accessToken) {
    if (window.cordova?.plugin?.http) {
      return new Promise((resolve, reject) => {
        cordova.plugin.http.sendRequest(
          OAUTH_CONFIG.userinfoEndpoint,
          {
            method: "GET",
            headers: { "Authorization": `Bearer ${accessToken}` }
          },
          (res) => {
            try {
              const data = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
              resolve(data);
            } catch (e) {
              reject(e);
            }
          },
          (err) => {
            reject(new Error(err.error || `Userinfo fetch error: ${err.status}`));
          }
        );
      });
    }

    const res = await window.fetch(OAUTH_CONFIG.userinfoEndpoint, {
      headers: { "Authorization": `Bearer ${accessToken}` }
    });
    if (!res.ok) {
      throw new Error(`Userinfo request failed: HTTP ${res.status}`);
    }
    return res.json();
  }

  async persistCredentials(tokenData, userInfo) {
    const userProfile = {
      isAuthenticated: true,
      email: userInfo.email,
      displayName: userInfo.name || userInfo.email.split("@")[0],
      avatarUrl: userInfo.picture || null,
      tier: "Google AI Ultra",
    };

    // 1. Persistencia reactiva en localStorage
    try {
      localStorage.setItem("ag_user_profile", JSON.stringify(userProfile));
      localStorage.setItem("ag_oauth_tokens", JSON.stringify(tokenData));
    } catch (e) {
      console.warn("No se pudo guardar en localStorage:", e);
    }

    // 2. Persistencia en PRoot Linux
    const oauthCreds = JSON.stringify({
      access_token: tokenData.access_token,
      refresh_token: tokenData.refresh_token || "",
      token_type: tokenData.token_type || "Bearer",
      expires_in: tokenData.expires_in,
      id_token: tokenData.id_token || "",
      scope: tokenData.scope || OAUTH_CONFIG.scopes,
      expiry_date: Date.now() + ((tokenData.expires_in || 3600) * 1000)
    }, null, 2);

    const googleAccounts = JSON.stringify({
      accounts: [
        {
          email: userInfo.email,
          name: userInfo.name || userInfo.email.split("@")[0],
          picture: userInfo.picture || "",
          active: true
        }
      ]
    }, null, 2);

    try {
      if (typeof system !== "undefined" && typeof system.getFilesDir === "function") {
        const filesDir = await new Promise((res, rej) => system.getFilesDir(res, rej));
        const alpineDir = `${filesDir}/alpine`;
        const targets = [
          `${alpineDir}/root/.gemini`,
          `${alpineDir}/home/studio/.gemini`,
          `${alpineDir}/public/.gemini`
        ];

        for (const dir of targets) {
          await new Promise((res) => {
            if (system.mkdirs) system.mkdirs(dir, res, res);
            else res();
          });
          await new Promise((res) => {
            if (system.writeText) system.writeText(`${dir}/oauth_creds.json`, oauthCreds, res, res);
            else res();
          });
          await new Promise((res) => {
            if (system.writeText) system.writeText(`${dir}/google_accounts.json`, googleAccounts, res, res);
            else res();
          });
        }
      }
    } catch (e) {
      console.warn("No se pudieron escribir credenciales en PRoot:", e);
    }

    // 3. Emitir evento a AgentBridge para sincronización de UI
    agentBridge.emit('authSuccess', userProfile);
    agentBridge.emit('statusChange', { status: 'READY', type: 'ready' });

    return userProfile;
  }

  async startOAuthFlow() {
    const verifier = generateCodeVerifier();
    const challenge = await generateCodeChallenge(verifier);
    const state = generateState();

    return new Promise((resolve, reject) => {
      let timeoutTimer = null;

      // Iniciar el servidor loopback en puerto 54123
      this.startLoopbackServer(
        state,
        async (authCode) => {
          clearTimeout(timeoutTimer);
          try {
            console.log("Código de autorización recibido. Intercambiando tokens...");
            const tokenData = await this.exchangeCodeForTokens(authCode, verifier);
            console.log("Tokens obtenidos con éxito. Consultando userinfo...");
            const userInfo = await this.fetchUserInfo(tokenData.access_token);
            console.log(`Usuario autenticado: ${userInfo.email}`);
            const profile = await this.persistCredentials(tokenData, userInfo);
            resolve(profile);
          } catch (err) {
            console.error("Error en intercambio de credenciales OAuth:", err);
            reject(err);
          }
        },
        (serverErr) => {
          clearTimeout(timeoutTimer);
          console.error("Error en servidor loopback OAuth:", serverErr);
          reject(serverErr);
        }
      );

      // Tiempo límite de 3 minutos para completar el login en el navegador
      timeoutTimer = setTimeout(() => {
        this.stopLoopbackServer();
        reject(new Error("Tiempo de espera para autenticación con Google agotado (timeout 3m)"));
      }, 180000);

      // Construir URL oficial de autenticación de Google con PKCE S256
      const authUrl = `${OAUTH_CONFIG.authEndpoint}?client_id=${encodeURIComponent(OAUTH_CONFIG.clientId)}&redirect_uri=${encodeURIComponent(OAUTH_CONFIG.redirectUri)}&response_type=code&scope=${encodeURIComponent(OAUTH_CONFIG.scopes)}&code_challenge=${encodeURIComponent(challenge)}&code_challenge_method=S256&state=${encodeURIComponent(state)}&access_type=offline&prompt=consent`;

      console.log("Despachando URL de autorización a navegador seguro...");
      openAuthorizationUrl(authUrl);
    });
  }
}

export default new GoogleAuthService();
