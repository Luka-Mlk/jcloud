# Developer-First Cloud Design System (Supabase-Inspired)

A design language characterized by high-contrast "Dark Mode" by default, sophisticated typography, and a "Bento-box" layout style that communicates modularity and power.

---

## 1. Color Palette

### Base Neutrals
| Token | Hex | Application |
| :--- | :--- | :--- |
| `--color-bg-canvas` | `#1C1C1C` | Global background. |
| `--color-bg-card` | `#232323` | Modular sections, bento grid items. |
| `--color-border-subtle`| `#2E2E2E` | Default component borders, grid lines. |
| `--color-border-strong`| `#3E3E3E` | Hovered borders, active input states. |

### Brand & Syntax
| Token | Hex | Application |
| :--- | :--- | :--- |
| `--color-brand-green` | `#3ECF8E` | Primary actions, "Ready" states, brand marks. |
| `--color-brand-hover` | `#30B477` | Button hover states. |
| `--color-text-main` | `#EDEDED` | Primary content, high legibility. |
| `--color-text-muted` | `#A0A0A0` | Descriptive text, secondary labels. |

---

## 2. Typography

- **Font Family:** `Geist Sans` (Primary), `Geist Mono` (Data/Code).
- **Scale:**
  - **Display:** `3.5rem (56px)` / Bold / Tracking `-0.04em`
  - **Heading:** `1.5rem (24px)` / SemiBold / Tracking `-0.02em`
  - **Label:** `0.75rem (12px)` / Medium / Uppercase / Tracking `0.05em`
  - **Mono:** `0.875rem (14px)` / Regular / Line-height `1.6`

---

## 3. The "Bento" Grid & UI Logic

- **Grid System:** Strict adherence to a modular grid where components are housed in cards of varying spans (1x1, 2x1, 3x2).
- **Corner Radius:** `8px` for buttons and small inputs; `12px` for cards and containers.
- **Glassmorphism:** Subtle use of `backdrop-filter: blur(8px)` on navigation bars and overlays to maintain context.
- **Borders:** 1px solid strokes are the primary method of separation rather than shadows.

---

## 4. Components

### Action Buttons
- **Primary:** `--color-brand-green` background, black text. High contrast, no gradient.
- **Secondary:** Dark gray background (`#2E2E2E`), white text, subtle 1px border.

### Status Indicators
- **Connected:** Small pulsing `--color-brand-green` dot.
- **Badge:** Small pill-shaped containers with low-opacity background tints and high-opacity text.

### Code & Data
- **Syntax Highlighting:** Neon-inspired colors (cyan, lime, purple) against the `--color-bg-card` background.
- **Data Rows:** Alternating hover highlights using `--color-bg-card`.

---

## 5. Visual Accents

- **Gradients:** Very subtle top-to-bottom linear gradients on cards (e.g., `#282828` to `#232323`).
- **Icons:** Thin-line (1.5px) monochrome icons. Usually white or light gray.
- **Depth:** Elements feel "pressed into" or "floating slightly above" the canvas through border-color manipulation rather than heavy drop shadows.

---

## 6. Spacing
Base unit: `4px`
- **Inner Padding:** `16px` or `24px`
- **Gap between Modules:** `12px` or `16px`
- **Section Margin:** `64px`
