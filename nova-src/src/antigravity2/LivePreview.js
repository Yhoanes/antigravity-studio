/**
 * Google Antigravity 2.0 Mobile - LivePreview Component
 * Interactive Live Web Preview with Port Detection, Split-View & Embedded Console
 * SPEC-021: AC-AG2-008 (localhost:3000 detection, Split-View on tablets)
 */

import agentBridge from "./AgentBridge";

export class LivePreview {
  constructor(options = {}) {
    this.activeUrl = options.initialUrl || "http://localhost:3000";
    this.viewportMode = "responsive"; // 'responsive' | 'mobile' | 'tablet' | 'desktop'
    this.container = null;
    this.iframeEl = null;
    this.urlInputEl = null;
    this.bannerEl = null;
    this.consoleDrawerEl = null;
    this.isConsoleOpen = false;
    this.logs = [];

    this.init();
  }

  init() {
    this.container = document.createElement("div");
    this.container.className = "ag-live-preview-pane";

    // 1. Navigation control bar
    const navBar = this.createNavBar();
    this.container.appendChild(navBar);

    // 2. Iframe Preview Viewport
    const previewContainer = document.createElement("div");
    previewContainer.className = "ag-preview-container";

    this.iframeEl = document.createElement("iframe");
    this.iframeEl.src = this.activeUrl;
    this.iframeEl.setAttribute("sandbox", "allow-scripts allow-same-origin allow-forms allow-modals allow-popups");
    previewContainer.appendChild(this.iframeEl);

    this.container.appendChild(previewContainer);

    // 3. Auto-detected server notification banner
    this.bannerEl = document.createElement("div");
    this.bannerEl.className = "ag-server-notification-banner";
    this.bannerEl.style.display = "none";
    this.container.appendChild(this.bannerEl);

    // 4. Hook agentBridge server detection (SPEC-021: AC-AG2-008)
    agentBridge.on('serverDetected', ({ port, url }) => {
      this.showServerNotification(port, url);
    });
  }

  createNavBar() {
    const nav = document.createElement("div");
    nav.className = "ag-preview-nav";

    // Reload button (↻)
    const reloadBtn = document.createElement("button");
    reloadBtn.className = "ag-preview-icon-btn";
    reloadBtn.innerHTML = "↻";
    reloadBtn.title = "Recargar vista previa";
    reloadBtn.onclick = () => this.reload();
    nav.appendChild(reloadBtn);

    // URL input
    this.urlInputEl = document.createElement("input");
    this.urlInputEl.type = "text";
    this.urlInputEl.className = "ag-preview-url-input";
    this.urlInputEl.value = this.activeUrl;
    this.urlInputEl.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        this.navigate(this.urlInputEl.value.trim());
      }
    });
    nav.appendChild(this.urlInputEl);

    // Viewport Mode Buttons
    const modeMobileBtn = document.createElement("button");
    modeMobileBtn.className = "ag-preview-icon-btn";
    modeMobileBtn.innerHTML = "📱";
    modeMobileBtn.title = "Modo Móvil (375px)";
    modeMobileBtn.onclick = () => this.setViewportMode("mobile");
    nav.appendChild(modeMobileBtn);

    const modeTabletBtn = document.createElement("button");
    modeTabletBtn.className = "ag-preview-icon-btn";
    modeTabletBtn.innerHTML = "📟";
    modeTabletBtn.title = "Modo Tablet (768px)";
    modeTabletBtn.onclick = () => this.setViewportMode("tablet");
    nav.appendChild(modeTabletBtn);

    const modeFullBtn = document.createElement("button");
    modeFullBtn.className = "ag-preview-icon-btn";
    modeFullBtn.innerHTML = "💻";
    modeFullBtn.title = "Pantalla Completa (100%)";
    modeFullBtn.onclick = () => this.setViewportMode("responsive");
    nav.appendChild(modeFullBtn);

    // External browser open
    const openExtBtn = document.createElement("button");
    openExtBtn.className = "ag-preview-icon-btn";
    openExtBtn.innerHTML = "↗";
    openExtBtn.title = "Abrir en navegador externo";
    openExtBtn.onclick = () => {
      if (window.system?.openInBrowser) {
        window.system.openInBrowser(this.activeUrl);
      } else {
        window.open(this.activeUrl, "_blank");
      }
    };
    nav.appendChild(openExtBtn);

    return nav;
  }

  showServerNotification(port, url) {
    this.bannerEl.innerHTML = `
      <span>🌐 Servidor detectado en http://localhost:${port}</span>
      <button class="ag-btn-banner-open" id="btn-banner-open-preview">Abrir Vista Previa</button>
    `;
    this.bannerEl.style.display = "flex";

    const openBtn = this.bannerEl.querySelector("#btn-banner-open-preview");
    openBtn.onclick = () => {
      this.navigate(url);
      this.bannerEl.style.display = "none";
    };

    // Auto-hide after 8 seconds
    setTimeout(() => {
      if (this.bannerEl) this.bannerEl.style.display = "none";
    }, 8000);
  }

  navigate(url) {
    if (!url) return;
    let fullUrl = url;
    if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
      fullUrl = `http://${fullUrl}`;
    }
    this.activeUrl = fullUrl;
    if (this.urlInputEl) this.urlInputEl.value = fullUrl;
    if (this.iframeEl) this.iframeEl.src = fullUrl;
  }

  setUrl(url) {
    this.navigate(url);
  }

  reload() {
    if (this.iframeEl) {
      this.iframeEl.src = this.activeUrl;
    }
  }

  setViewportMode(mode) {
    this.viewportMode = mode;
    const previewContainer = this.container.querySelector(".ag-preview-container");
    if (!previewContainer) return;

    previewContainer.classList.remove("mode-mobile", "mode-tablet", "mode-responsive");
    if (mode === "mobile") {
      previewContainer.classList.add("mode-mobile");
    } else if (mode === "tablet") {
      previewContainer.classList.add("mode-tablet");
    } else {
      previewContainer.classList.add("mode-responsive");
    }
  }

  get el() {
    return this.container;
  }
}

export default LivePreview;
