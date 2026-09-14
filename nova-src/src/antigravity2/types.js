/**
 * Google Antigravity 2.0 Mobile - Type Definitions & Contracts
 * Based on SPEC-021: Agent-First Mobile Studio Architecture
 */

/**
 * @typedef {'user' | 'agent'} MessageSender
 */

/**
 * @typedef {'PENDING' | 'RUNNING' | 'SUCCESS' | 'ERROR'} ToolStatus
 */

/**
 * @typedef {'AWAITING_USER_APPROVAL' | 'APPROVED' | 'REJECTED'} PlanStatus
 */

/**
 * @typedef {'responsive' | 'mobile' | 'tablet' | 'desktop'} ViewportMode
 */

/**
 * @typedef {Object} ThinkingProcess
 * @property {number} durationSeconds
 * @property {string[]} thoughts
 * @property {boolean} isComplete
 */

/**
 * @typedef {Object} ToolInvocation
 * @property {string} toolId
 * @property {'write_to_file' | 'replace_file_content' | 'run_command' | 'grep_search' | 'read_url'} toolName
 * @property {ToolStatus} status
 * @property {Record<string, any>} arguments
 * @property {string} [outputSummary]
 * @property {string} [diffContent]
 * @property {number} [durationMs]
 */

/**
 * @typedef {Object} PlanStep
 * @property {string} id
 * @property {string} description
 * @property {boolean} completed
 */

/**
 * @typedef {Object} PlanApprovalRequest
 * @property {string} planId
 * @property {string} title
 * @property {PlanStep[]} steps
 * @property {string[]} affectedFiles
 * @property {PlanStatus} status
 */

/**
 * @typedef {Object} ChatMessage
 * @property {string} id
 * @property {MessageSender} sender
 * @property {number} timestamp
 * @property {string} content
 * @property {ThinkingProcess} [thinkingProcess]
 * @property {ToolInvocation[]} [toolInvocations]
 * @property {PlanApprovalRequest} [planApproval]
 */

/**
 * @typedef {Object} UserProfile
 * @property {boolean} isAuthenticated
 * @property {string} displayName
 * @property {string | null} email
 * @property {string} tier
 * @property {string | null} avatarUrl
 */

/**
 * @typedef {Object} ChatSessionMetadata
 * @property {string} chatId
 * @property {string} projectName
 * @property {string} title
 * @property {number} lastActiveTimestamp
 * @property {number} messageCount
 */

/**
 * @typedef {Object} ProjectDirectory
 * @property {string} name
 * @property {string} absolutePath
 * @property {boolean} hasAgentMd
 */

/**
 * @typedef {Object} LivePreviewConfig
 * @property {string} activeUrl
 * @property {number[]} detectedPorts
 * @property {ViewportMode} viewportMode
 * @property {boolean} isConsoleVisible
 * @property {{ level: 'info' | 'warn' | 'error', message: string, timestamp: number }[]} capturedLogs
 */

export const DEFAULT_USER_PROFILE = {
  isAuthenticated: false,
  displayName: "Invitado",
  email: null,
  tier: "Google AI (Modo Invitado)",
  avatarUrl: null,
};

export const GOOGLE_COLORS = {
  blue: '#4285f4',
  red: '#ea4335',
  yellow: '#fbbc04',
  green: '#34a853',
};
