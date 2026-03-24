# robot-mcp

A Windows RPA server built with Spring Boot and Spring AI that exposes mouse, keyboard, screen, clipboard, and window-management tools via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) and a REST API.

Connect it to Claude Desktop (or any MCP-compatible client) and let an AI model control your desktop directly.

## Prerequisites

| Requirement | Version |
|---|---|
| OS | Windows (JNA Win32 APIs required) |
| Java | 25+ |
| Maven | via included `mvnw` wrapper |

## Building

```bash
./mvnw package -DskipTests
```

## Running

**Via Maven (development):**
```bash
./mvnw spring-boot:run
```

**Via jar:**
```bash
run.bat
```

On startup a small control panel window appears showing the server URLs and a Stop Server button.

## Connecting an MCP client

### Claude Desktop

Add to `%APPDATA%\Claude\claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "robot-mcp": {
      "type": "streamableHttp",
      "url": "http://127.0.0.1:8080/mcp"
    }
  }
}
```

### Claude Code CLI

```bash
claude mcp add --transport streamable-http robot-mcp http://127.0.0.1:8080/mcp
```

## Available tools

### Mouse
| Tool | Description |
|---|---|
| `mouseMove` | Move cursor to (x, y) |
| `leftClick` | Left-click at (x, y) |
| `doubleClick` | Double-click at (x, y) |
| `rightClick` | Right-click at (x, y) |
| `middleClick` | Middle-click at (x, y) |
| `mouseScroll` | Scroll wheel at (x, y) — positive = down |
| `mouseDrag` | Click-drag from one point to another |

### Keyboard
| Tool | Description |
|---|---|
| `typeText` | Type a string (handles uppercase + common symbols) |
| `pressKey` | Press a named key: `ENTER`, `TAB`, `F5`, `WIN`, etc. |
| `pressKeyCombination` | Press a combination like `CTRL+C` or `CTRL+SHIFT+T` |

### Screen
| Tool | Description |
|---|---|
| `captureScreen` | Full-screen screenshot (returns image) |
| `captureRegion` | Screenshot of a rectangle |
| `captureMonitor` | Screenshot of a specific monitor by index |
| `listMonitors` | List monitors with resolution and position |
| `findPixelColor` | Get hex color of a pixel |
| `waitForPixelColor` | Wait until a pixel reaches a target color |
| `waitForScreenChange` | Wait until any pixel changes in a region |

### Clipboard
| Tool | Description |
|---|---|
| `getClipboard` | Read text from the system clipboard |
| `setClipboard` | Write text to the system clipboard |

### Windows
| Tool | Description |
|---|---|
| `getActiveWindowTitle` | Title of the foreground window |
| `listWindows` | All visible top-level windows |
| `focusWindow` | Bring a window to the foreground by title substring |
| `getWindowBounds` | Position and size of a window |
| `minimizeWindow` | Minimize a window |
| `maximizeWindow` | Maximize a window |
| `restoreWindow` | Restore a minimized/maximized window |

### Utility
| Tool | Description |
|---|---|
| `sleep` | Pause for up to 30 seconds |

## REST API

All tools are also available as a REST API under `/api` — useful for models that don't support MCP.

**OpenAI-compatible tool schema** (pass directly as `tools` in an OpenAI API call):
```
GET http://127.0.0.1:8080/api/tools
```

**Example — left-click:**
```bash
curl -X POST http://127.0.0.1:8080/api/mouse/left-click \
  -H "Content-Type: application/json" \
  -d '{"x": 500, "y": 300}'
# {"result": "Left clicked at (500, 300)"}
```

**Example — type text:**
```bash
curl -X POST http://127.0.0.1:8080/api/keyboard/type \
  -H "Content-Type: application/json" \
  -d '{"text": "hello world"}'
```

**Example — screenshot:**
```bash
curl http://127.0.0.1:8080/api/screen/capture
# {"data": "<base64 PNG>", "mediaType": "image/png"}
```

Full endpoint list: `GET /api` → Spring Boot Actuator mappings at `/actuator/mappings`.

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `server.address` | `127.0.0.1` | Bind address — keep localhost unless you know what you're doing |
| `spring.ai.mcp.server.name` | `robot-mcp` | MCP server name advertised to clients |

Enable verbose debug logging by activating the `dev` profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Security

> **Warning:** This server gives any HTTP client full control of your mouse, keyboard, clipboard, and screen. It binds to `127.0.0.1` by default. Do not change `server.address` to `0.0.0.0` unless you fully understand and accept the risk.

## Stack

- **Java 25** with virtual threads
- **Spring Boot 4** / **Spring MVC**
- **Spring AI 2.0.0-M3** — MCP server (Streamable HTTP transport)
- **JNA 5.15** — Win32 window management (User32, Kernel32)
- **java.awt.Robot** — mouse, keyboard, and screen automation
