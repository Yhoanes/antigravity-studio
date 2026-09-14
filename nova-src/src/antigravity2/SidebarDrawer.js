/**
 * Google Antigravity 2.0 Mobile - SidebarDrawer Component
 * Session Hub, User Profile (shadrick1212@gmail.com - Google AI Ultra), Project Selector & Chat History
 * SPEC-021: AC-AG2-006, AC-AG2-007
 */

import agentBridge from "./AgentBridge";
import fsOperation from "fileSystem";
import { DEFAULT_USER_PROFILE } from "./types";

export class SidebarDrawer {
  constructor(options = {}) {
    this.userProfile = options.userProfile || DEFAULT_USER_PROFILE;
    this.activeProject = options.activeProject || "calculadora";
    this.projects = options.projects || ["calculadora"];
    this.projectSelectContainer = null;
    this.chatHistory = options.chatHistory || [
      { id: "chat-001", title: "Autenticación JWT y Middleware", group: "Hoy", timestamp: Date.now() - 3600000, count: 14 },
      { id: "chat-002", title: "Refactorización de Base de Datos", group: "Hoy", timestamp: Date.now() - 7200000, count: 22 },
      { id: "chat-003", title: "Configuración Inicial con Docker", group: "Ayer", timestamp: Date.now() - 86400000, count: 9 },
      { id: "chat-004", title: "Corrección de Tests en Jest", group: "Ayer", timestamp: Date.now() - 93600000, count: 16 },
    ];

    this.isOpen = false;
    this.overlayEl = null;
    this.drawerEl = null;
    this.onProjectSelectCallback = options.onProjectSelect || null;
    this.onNewChatCallback = options.onNewChat || null;

    this.init();
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

    // 3. Footer
    const footer = this.createFooter();
    this.drawerEl.appendChild(footer);

    // 4. Initial dynamic project discovery (SPEC-022 §4.2, AC-REP-006)
    this.loadProjectsFromStorage();
  }

  createHeader() {
    const header = document.createElement("div");
    header.className = "ag-drawer-header";

    // User Profile Card: shadrick1212@gmail.com - Google AI Ultra
    const profileCard = document.createElement("div");
    profileCard.className = "ag-user-profile-card";
    profileCard.innerHTML = `
      <div class="ag-avatar-wrapper">
        <img class="ag-avatar-img" src="./logo.svg" alt="User Avatar" />
      </div>
      <div class="ag-user-info">
        <span class="ag-user-email">${this.userProfile.email}</span>
        <span class="ag-user-tier">✦ ${this.userProfile.tier}</span>
      </div>
    `;
    header.appendChild(profileCard);

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

    this.chatHistory.forEach((chat) => {
      const chatItem = document.createElement("div");
      chatItem.className = "ag-history-item";
      chatItem.innerHTML = `
        <span class="ag-item-icon">💬</span>
        <div style="display: flex; flex-direction: column; flex: 1; overflow: hidden;">
          <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${chat.title}</span>
          <span style="font-size: 0.72rem; color: var(--ag-text-secondary);">${chat.group} &bull; ${chat.count} msgs</span>
        </div>
      `;
      chatItem.onclick = () => {
        this.close();
        agentBridge.activeChatId = chat.id;
        agentBridge.launchAgy();
      };
      historyList.appendChild(chatItem);
    });

    historySection.appendChild(historyList);
    body.appendChild(historySection);

    return body;
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

    this.projects.forEach((proj) => {
      const projItem = document.createElement("div");
      projItem.className = `ag-history-item ${proj === this.activeProject ? 'active' : ''}`;
      projItem.innerHTML = `
        <span class="ag-item-icon">📁</span>
        <span style="flex: 1; font-weight: 500;">${proj}</span>
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
        // Filtrar únicamente directorios y excluir ocultos
        const scanned = entries
          .filter(e => e.isDirectory && !e.name.startsWith("."))
          .map(e => e.name);
        if (scanned.length) {
          this.projects = scanned;
        }
      } else {
        this.projects = ["calculadora"];
      }
    } catch (err) {
      console.warn("No se pudo leer directorio de proyectos directamente, usando defaults:", err);
      this.projects = ["calculadora"];
    }

    if (!this.projects.length) {
      this.projects = ["calculadora"];
    }

    // Establecer proyecto activo por defecto si el actual no existe en la lista
    if (!this.projects.includes(this.activeProject) && this.projects.length > 0) {
      this.activeProject = this.projects.includes("calculadora") ? "calculadora" : this.projects[0];
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
}

export default SidebarDrawer;
