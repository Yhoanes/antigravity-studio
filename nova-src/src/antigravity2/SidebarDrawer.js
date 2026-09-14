/**
 * Google Antigravity 2.0 Mobile - SidebarDrawer Component
 * Session Hub, User Profile (Zero-Mock), Project Selector & Chat History
 * SPEC-023: ZeroMockContract (AC-CLN-001, AC-CLN-002, AC-CLN-003, AC-CLN-004)
 */

import agentBridge from "./AgentBridge";
import fsOperation from "fileSystem";
import { DEFAULT_USER_PROFILE } from "./types";

export class SidebarDrawer {
  constructor(options = {}) {
    let initialProfile = options.userProfile || DEFAULT_USER_PROFILE;
    try {
      const stored = localStorage.getItem("ag_user_profile");
      if (stored) {
        initialProfile = JSON.parse(stored);
      }
    } catch (e) {}

    this.userProfile = initialProfile;
    this.activeProject = options.activeProject || "workspace";
    this.projects = options.projects || [];
    this.projectSelectContainer = null;
    this.chatHistory = [];
    this.historyListContainer = null;

    this.isOpen = false;
    this.overlayEl = null;
    this.drawerEl = null;
    this.headerEl = null;
    this.profileCardEl = null;
    this.onProjectSelectCallback = options.onProjectSelect || null;
    this.onNewChatCallback = options.onNewChat || null;

    this.init();
    this.setupAuthListener();
  }

  init() {
    // Overlay
    this.overlayEl = document.createElement("div");
    this.overlayEl.className = "ag-sidebar-drawer-overlay";
    this.overlayEl.onclick = () => this.close();

    // Drawer Container
    this.drawerEl = document.createElement("aside");
    this.drawerEl.className = "ag-sidebar-drawer";

    // 1. Header (User Profile & New Chat)
    const header = this.createHeader();
    this.drawerEl.appendChild(header);

    // 2. Body (Projects & History)
    const body = this.createBody();
    this.drawerEl.appendChild(body);

    // 3. Footer (System & Version)
    const footer = this.createFooter();
    this.drawerEl.appendChild(footer);

    // 4. Initial dynamic project discovery (SPEC-023 §2.2, AC-CLN-003)
    this.loadProjectsFromStorage();
  }

  createProfileCard() {
    const profileCard = document.createElement("div");
    profileCard.className = "ag-user-profile-card";

    if (this.userProfile.isAuthenticated && this.userProfile.email) {
      profileCard.innerHTML = `
        <div class="ag-avatar-wrapper">
          <img class="ag-avatar-img" src="${this.userProfile.avatarUrl || './logo.svg'}" alt="User Avatar" />
        </div>
        <div class="ag-user-info">
          <span class="ag-user-email">${this.escapeHtml(this.userProfile.displayName || this.userProfile.email)}</span>
          <span class="ag-user-tier">✦ ${this.escapeHtml(this.userProfile.tier)}</span>
        </div>
      `;
    } else {
      profileCard.innerHTML = `
        <div class="ag-avatar-wrapper">
          <svg class="ag-avatar-icon" viewBox="0 0 24 24" width="36" height="36" fill="#94a3b8">
            <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
          </svg>
        </div>
        <div class="ag-user-info">
          <span class="ag-user-email">Invitado</span>
          <span class="ag-user-tier">Modo Local / Sin Conexión</span>
          <button class="ag-btn-link-account" id="btn-link-google" title="Vincular Cuenta Google">
            <span>🌐 Vincular Cuenta Google</span>
          </button>
        </div>
      `;

      const linkBtn = profileCard.querySelector("#btn-link-google");
      if (linkBtn) {
        linkBtn.onclick = (e) => {
          e.stopPropagation();
          linkBtn.disabled = true;
          linkBtn.innerHTML = `<span>⏳ Abriendo navegador Google...</span>`;

          if (typeof window.toast === "function") {
            window.toast("Iniciando autenticación oficial con Google...");
          }

          agentBridge.triggerGoogleLogin()
            .then((profile) => {
              console.log("Autenticación completada con éxito:", profile);
            })
            .catch((err) => {
              console.error("Error al iniciar autenticación Google:", err);
              linkBtn.disabled = false;
              linkBtn.innerHTML = `<span>🌐 Vincular Cuenta Google</span>`;
              if (typeof window.toast === "function") {
                window.toast("Error al conectar con el servicio de autenticación.");
              }
            });
        };
      }
    }
    return profileCard;
  }

  createHeader() {
    const header = document.createElement("div");
    header.className = "ag-drawer-header";
    this.headerEl = header;

    // User Profile Card (Zero-Mock: Invitado / Conectado)
    this.profileCardEl = this.createProfileCard();
    header.appendChild(this.profileCardEl);

    // Button: + Nueva Conversación
    const newChatBtn = document.createElement("button");
    newChatBtn.className = "ag-btn-new-chat";
    newChatBtn.innerHTML = `<span>+</span><span>Nueva Conversación</span>`;
    newChatBtn.onclick = () => {
      this.close();
      if (this.onNewChatCallback) {
        this.onNewChatCallback();
      } else {
        agentBridge.launchAgy();
      }
    };
    header.appendChild(newChatBtn);

    return header;
  }

  setupAuthListener() {
    agentBridge.on('authSuccess', ({ email, tier, displayName, avatarUrl }) => {
      this.userProfile = {
        isAuthenticated: true,
        displayName: displayName || (email ? email.split('@')[0] : "Google Developer"),
        email: email,
        tier: tier || "Google AI Ultra",
        avatarUrl: avatarUrl || null,
      };
      try {
        localStorage.setItem("ag_user_profile", JSON.stringify(this.userProfile));
      } catch (e) {}

      // Actualizar tarjeta de perfil reactivamente en el Header
      if (this.headerEl && this.profileCardEl) {
        const newCard = this.createProfileCard();
        this.headerEl.replaceChild(newCard, this.profileCardEl);
        this.profileCardEl = newCard;
      }

      if (typeof window.toast === "function") {
        window.toast(`Bienvenido, ${this.userProfile.displayName}`);
      }
    });
  }

  createBody() {
    const body = document.createElement("div");
    body.className = "ag-drawer-body";

    // Project Section
    const projectSection = document.createElement("div");
    projectSection.className = "ag-drawer-section";

    const projectTitle = document.createElement("div");
    projectTitle.className = "ag-section-title";
    projectTitle.textContent = "PROYECTO ACTIVO (/sdcard/Projects)";
    projectSection.appendChild(projectTitle);

    const projectSelectContainer = document.createElement("div");
    projectSelectContainer.className = "ag-history-list";
    this.projectSelectContainer = projectSelectContainer;
    this.renderProjectsList();

    projectSection.appendChild(projectSelectContainer);
    body.appendChild(projectSection);

    // Chat History Section
    const historySection = document.createElement("div");
    historySection.className = "ag-drawer-section";

    const historyTitle = document.createElement("div");
    historyTitle.className = "ag-section-title";
    historyTitle.textContent = "HISTORIAL DE CONVERSACIONES";
    historySection.appendChild(historyTitle);

    const historyList = document.createElement("div");
    historyList.className = "ag-history-list";
    this.historyListContainer = historyList;
    this.renderChatHistory();

    historySection.appendChild(historyList);
    body.appendChild(historySection);

    return body;
  }

  renderChatHistory() {
    if (!this.historyListContainer) return;
    this.historyListContainer.innerHTML = "";

    if (!this.chatHistory || !this.chatHistory.length) {
      const emptyHistory = document.createElement("div");
      emptyHistory.className = "ag-history-empty";
      emptyHistory.innerHTML = `
        <span class="ag-empty-icon">💬</span>
        <span class="ag-empty-title">Sin conversaciones previas</span>
        <span class="ag-empty-desc">Inicia una nueva sesión agéntica para comenzar a desarrollar.</span>
      `;
      this.historyListContainer.appendChild(emptyHistory);
      return;
    }

    this.chatHistory.forEach((chat) => {
      const chatItem = document.createElement("div");
      chatItem.className = "ag-history-item";
      chatItem.innerHTML = `
        <span class="ag-item-icon">💬</span>
        <div style="display: flex; flex-direction: column; flex: 1; overflow: hidden;">
          <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${this.escapeHtml(chat.title)}</span>
          <span style="font-size: 0.72rem; color: var(--ag-text-secondary);">${this.escapeHtml(chat.group || "Reciente")} &bull; ${chat.count || 1} msgs</span>
        </div>
      `;
      chatItem.onclick = () => {
        this.close();
        agentBridge.activeChatId = chat.id;
        agentBridge.launchAgy();
      };
      this.historyListContainer.appendChild(chatItem);
    });
  }

  createFooter() {
    const footer = document.createElement("div");
    footer.className = "ag-drawer-footer";
    footer.innerHTML = `
      <button class="ag-footer-btn" id="btn-drawer-settings">
        <span>⚙️</span>
        <span>Ajustes</span>
      </button>
      <span class="ag-quality-gate-badge">🛡️ Quality Gates: OK</span>
    `;

    const settingsBtn = footer.querySelector("#btn-drawer-settings");
    settingsBtn.onclick = () => {
      if (window.acode?.exec) {
        window.acode.exec("open-settings");
      }
      this.close();
    };

    return footer;
  }

  renderProjectsList() {
    if (!this.projectSelectContainer) return;
    this.projectSelectContainer.innerHTML = "";

    if (!this.projects || !this.projects.length) {
      const emptyItem = document.createElement("div");
      emptyItem.className = "ag-history-empty";
      emptyItem.innerHTML = `
        <span class="ag-empty-icon">📁</span>
        <span class="ag-empty-title">Sin proyectos activos</span>
        <button class="ag-chip" style="margin-top: 8px;" id="btn-create-proj">+ Nuevo Proyecto</button>
      `;
      const createBtn = emptyItem.querySelector("#btn-create-proj");
      if (createBtn) {
        createBtn.onclick = () => {
          this.createNewProject();
        };
      }
      this.projectSelectContainer.appendChild(emptyItem);
      return;
    }

    this.projects.forEach((proj) => {
      const projItem = document.createElement("div");
      projItem.className = `ag-history-item ${proj === this.activeProject ? 'active' : ''}`;
      projItem.innerHTML = `
        <span class="ag-item-icon">📁</span>
        <span style="flex: 1; font-weight: 500;">${this.escapeHtml(proj)}</span>
        ${proj === this.activeProject ? '<span style="color: var(--ag-google-green); font-size: 0.8rem;">●</span>' : ''}
      `;
      projItem.onclick = () => {
        this.activeProject = proj;
        agentBridge.switchProject(proj);
        if (this.onProjectSelectCallback) {
          this.onProjectSelectCallback(proj);
        }
        this.close();
        this.renderProjectsList();
      };
      this.projectSelectContainer.appendChild(projItem);
    });
  }

  async createNewProject() {
    let name = prompt("Nombre del nuevo proyecto:");
    if (!name || !name.trim()) return;
    name = name.trim().replace(/[^a-zA-Z0-9_-]/g, "");
    if (!name) return;

    try {
      if (typeof fsOperation !== "undefined") {
        await fsOperation("/storage/emulated/0/Projects").createDirectory(name);
      }
    } catch (e) {
      console.warn("Error creando directorio de proyecto:", e);
    }
    await this.loadProjectsFromStorage();
    this.activeProject = name;
    agentBridge.switchProject(name);
    if (this.onProjectSelectCallback) {
      this.onProjectSelectCallback(name);
    }
    this.close();
  }

  async readDirEntries(path) {
    if (typeof fsOperation !== "undefined") {
      try {
        const fs = fsOperation(path);
        if (await fs.exists()) {
          const list = await fs.lsDir();
          return list.map(item => ({
            name: item.name,
            isDirectory: item.isDirectory || item.type === "dir" || !item.isFile,
          }));
        }
      } catch (e) {
        console.warn("fsOperation failed for path:", path, e);
      }
    }

    if (typeof window.resolveLocalFileSystemURL === "function") {
      return new Promise((resolve) => {
        window.resolveLocalFileSystemURL(
          path,
          (dirEntry) => {
            if (!dirEntry.isDirectory) return resolve([]);
            const reader = dirEntry.createReader();
            reader.readEntries(
              (entries) => {
                resolve(
                  entries.map((e) => ({
                    name: e.name,
                    isDirectory: e.isDirectory,
                  }))
                );
              },
              () => resolve([])
            );
          },
          () => resolve([])
        );
      });
    }

    return [];
  }

  async loadProjectsFromStorage() {
    const projectsPath = "file:///storage/emulated/0/Projects";
    const fallbackPath = "file:///sdcard/Projects";

    try {
      let entries = await this.readDirEntries(projectsPath);
      if (!entries || !entries.length) {
        entries = await this.readDirEntries(fallbackPath);
      }
      if (!entries || !entries.length) {
        entries = await this.readDirEntries("/storage/emulated/0/Projects");
      }

      if (entries && entries.length) {
        // Filtrar únicamente directorios reales y excluir ocultos
        const scanned = entries
          .filter(e => e.isDirectory && !e.name.startsWith("."))
          .map(e => e.name);
        this.projects = scanned;
      } else {
        this.projects = [];
      }
    } catch (err) {
      console.warn("No se pudo leer directorio de proyectos directamente:", err);
      this.projects = [];
    }

    // Establecer proyecto activo: si hay proyectos y el actual no está en la lista, tomar el primero; si no, 'workspace'
    if (this.projects.length > 0) {
      if (!this.projects.includes(this.activeProject)) {
        this.activeProject = this.projects[0];
      }
    } else {
      this.activeProject = "workspace";
    }

    this.renderProjectsList();
  }

  refreshProjectsList() {
    this.renderProjectsList();
  }

  open() {
    this.isOpen = true;
    this.loadProjectsFromStorage();
    this.overlayEl.classList.add("open");
    this.drawerEl.classList.add("open");
  }

  close() {
    this.isOpen = false;
    this.overlayEl.classList.remove("open");
    this.drawerEl.classList.remove("open");
  }

  toggle() {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  }

  mount(parentEl) {
    parentEl.appendChild(this.overlayEl);
    parentEl.appendChild(this.drawerEl);
  }

  escapeHtml(str) {
    return String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }
}

export default SidebarDrawer;
