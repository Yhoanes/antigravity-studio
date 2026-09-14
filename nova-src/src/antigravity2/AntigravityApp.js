/**
 * Google Antigravity 2.0 Mobile - Main Application Orchestrator
 * Connects ChatCanvas, LivePreview, SidebarDrawer and AgentBridge in Split-View (Tablet) or Tabbed (Mobile)
 * SPEC-021: AC-AG2-001, AC-AG2-002, AC-AG2-008
 */

import "./style.scss";
import agentBridge from "./AgentBridge";
import ChatCanvas from "./ChatCanvas";
import LivePreview from "./LivePreview";
import SidebarDrawer from "./SidebarDrawer";
import { DEFAULT_USER_PROFILE } from "./types";

export class AntigravityApp {
  constructor(options = {}) {
    this.rootEl = null;
    this.chatCanvas = null;
    this.livePreview = null;
    this.sidebarDrawer = null;
    this.activeProject = "calculadora";
    this.activeTab = "chat"; // 'chat' | 'preview'
    this.isSplitView = window.innerWidth >= 1024;
    this.statusBadgeEl = null;
    this.projectBtnTextEl = null;
    this.stageContainerEl = null;

    this.init();
  }

  init() {
    this.rootEl = document.createElement("div");
    this.rootEl.className = "antigravity2-app";

    // 1. Initialize Subcomponents
    this.chatCanvas = new ChatCanvas({ userProfile: DEFAULT_USER_PROFILE });
    this.livePreview = new LivePreview({ initialUrl: "http://localhost:3000" });
    this.sidebarDrawer = new SidebarDrawer({
      activeProject: this.activeProject,
      onProjectSelect: (proj) => this.onProjectChanged(proj),
      onNewChatCallback: () => this.onNewChat(),
    });

    // 2. Build Top App Bar
    const topBar = this.createTopBar();
    this.rootEl.appendChild(topBar);

    // 3. Build Stage Container
    this.stageContainerEl = document.createElement("div");
    this.stageContainerEl.className = `ag-stage-container ${this.isSplitView ? 'split-view' : 'single-view'}`;

    // Add Chat Canvas pane
    this.stageContainerEl.appendChild(this.chatCanvas.el);

    // Add Split Divider (visible in split-view)
    if (this.isSplitView) {
      const divider = document.createElement("div");
      divider.className = "ag-split-divider";
      this.setupDividerDrag(divider);
      this.stageContainerEl.appendChild(divider);
    }

    // Add Live Preview pane
    this.stageContainerEl.appendChild(this.livePreview.el);
    this.rootEl.appendChild(this.stageContainerEl);

    // 4. Mount Sidebar Drawer
    this.sidebarDrawer.mount(this.rootEl);

    // 5. Setup Responsive Resize Handlers
    window.addEventListener("resize", () => this.onWindowResize());

    // 6. Connect AgentBridge
    setTimeout(() => {
      agentBridge.connect().catch((err) => {
        console.warn("AgentBridge initial connect error (handled):", err);
      });
    }, 200);

    // 7. Subscribe to AgentBridge Status
    agentBridge.on('statusChange', ({ status, type }) => {
      if (this.statusBadgeEl) {
        this.statusBadgeEl.textContent = status;
        this.statusBadgeEl.className = `ag-status-badge ${type}`;
      }
    });
  }

  createTopBar() {
    const topBar = document.createElement("header");
    topBar.className = "ag-top-bar";

    // Left Section: Menu button + Brand logo + Project pill
    const leftSec = document.createElement("div");
    leftSec.className = "ag-header-left";

    // Hamburger button [ ☰ ]
    const menuBtn = document.createElement("button");
    menuBtn.className = "ag-icon-btn";
    menuBtn.innerHTML = "☰";
    menuBtn.title = "Abrir menú lateral";
    menuBtn.onclick = () => this.sidebarDrawer.toggle();
    leftSec.appendChild(menuBtn);

    // Brand Pill: Prism + "Google Antigravity"
    const brandPill = document.createElement("div");
    brandPill.className = "ag-brand-pill";
    brandPill.innerHTML = `
      <img class="ag-prism-logo" src="./logo.svg" alt="Google Antigravity Prism" />
      <span class="ag-brand-name">Google Antigravity</span>
    `;
    leftSec.appendChild(brandPill);

    // Project Selector Button
    const projBtn = document.createElement("button");
    projBtn.className = "ag-project-selector-btn";
    projBtn.innerHTML = `
      <span>📁</span>
      <span class="ag-project-name" id="ag-header-project-name">${this.activeProject}</span>
      <span>▾</span>
    `;
    projBtn.onclick = () => this.sidebarDrawer.open();
    leftSec.appendChild(projBtn);
    this.projectBtnTextEl = projBtn.querySelector("#ag-header-project-name");

    topBar.appendChild(leftSec);

    // Right Section: Status badge + View toggles
    const rightSec = document.createElement("div");
    rightSec.className = "ag-header-right";

    this.statusBadgeEl = document.createElement("span");
    this.statusBadgeEl.className = "ag-status-badge ready";
    this.statusBadgeEl.textContent = "READY";
    rightSec.appendChild(this.statusBadgeEl);

    // Tab buttons for mobile view (<1024px)
    this.chatTabBtn = document.createElement("button");
    this.chatTabBtn.className = `ag-tab-btn ${this.activeTab === 'chat' ? 'active' : ''}`;
    this.chatTabBtn.innerHTML = "<span>✦</span><span>Agente</span>";
    this.chatTabBtn.onclick = () => this.setActiveTab("chat");
    rightSec.appendChild(this.chatTabBtn);

    this.previewTabBtn = document.createElement("button");
    this.previewTabBtn.className = `ag-tab-btn ${this.activeTab === 'preview' ? 'active' : ''}`;
    this.previewTabBtn.innerHTML = "<span>🌐</span><span>Vista Previa</span>";
    this.previewTabBtn.onclick = () => this.setActiveTab("preview");
    rightSec.appendChild(this.previewTabBtn);

    // In tablet mode, hide the tab buttons since both panes are visible side by side
    if (this.isSplitView) {
      this.chatTabBtn.style.display = "none";
      this.previewTabBtn.style.display = "none";
    }

    topBar.appendChild(rightSec);
    return topBar;
  }

  setActiveTab(tab) {
    this.activeTab = tab;
    if (this.chatTabBtn) this.chatTabBtn.classList.toggle("active", tab === "chat");
    if (this.previewTabBtn) this.previewTabBtn.classList.toggle("active", tab === "preview");

    if (!this.isSplitView) {
      if (tab === "chat") {
        this.chatCanvas.el.style.display = "flex";
        this.livePreview.el.classList.remove("active");
      } else {
        this.chatCanvas.el.style.display = "none";
        this.livePreview.el.classList.add("active");
        this.livePreview.reload();
      }
    }
  }

  showPreviewTab() {
    this.setActiveTab("preview");
  }

  onProjectChanged(projectName) {
    this.activeProject = projectName;
    if (this.projectBtnTextEl) {
      this.projectBtnTextEl.textContent = projectName;
    }
    this.chatCanvas.addAgentMessage(`**Espacio de trabajo cambiado a:** \`/home/studio/workspace/${projectName}\`\nGoogle Antigravity CLI reanudado con auto-continue (\`agy -c\`).`);
  }

  onNewChat() {
    this.chatCanvas.messagesListEl.innerHTML = "";
    this.chatCanvas.renderWelcomeConversation();
    agentBridge.launchAgy();
  }

  onWindowResize() {
    const wasSplit = this.isSplitView;
    this.isSplitView = window.innerWidth >= 1024;

    if (wasSplit !== this.isSplitView) {
      this.stageContainerEl.className = `ag-stage-container ${this.isSplitView ? 'split-view' : 'single-view'}`;

      if (this.isSplitView) {
        this.chatTabBtn.style.display = "none";
        this.previewTabBtn.style.display = "none";
        this.chatCanvas.el.style.display = "flex";
        this.livePreview.el.style.display = "flex";
      } else {
        this.chatTabBtn.style.display = "inline-flex";
        this.previewTabBtn.style.display = "inline-flex";
        this.setActiveTab(this.activeTab);
      }
    }
  }

  setupDividerDrag(divider) {
    let isDragging = false;
    divider.addEventListener("mousedown", (e) => {
      isDragging = true;
      document.body.style.cursor = "col-resize";
    });

    window.addEventListener("mousemove", (e) => {
      if (!isDragging) return;
      const totalWidth = window.innerWidth;
      const leftWidthPercent = (e.clientX / totalWidth) * 100;
      if (leftWidthPercent >= 30 && leftWidthPercent <= 75) {
        this.chatCanvas.el.style.width = `${leftWidthPercent}%`;
        this.livePreview.el.style.width = `${100 - leftWidthPercent}%`;
      }
    });

    window.addEventListener("mouseup", () => {
      if (isDragging) {
        isDragging = false;
        document.body.style.cursor = "";
      }
    });
  }

  mount(targetParent = document.body) {
    targetParent.appendChild(this.rootEl);
  }

  get el() {
    return this.rootEl;
  }
}

export default AntigravityApp;
