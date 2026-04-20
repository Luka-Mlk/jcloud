# Atmospheric Dark Design System (Spotify-Inspired)

A design language focused on depth, immersion, and high-contrast information density within a dark-mode environment.

---

## 1. Color Palette

### Base Surfaces
| Token | Hex | Application |
| :--- | :--- | :--- |
| `--color-bg-deep` | `#000000` | Global background, lowest elevation. |
| `--color-bg-base` | `#121212` | Main content area background. |
| `--color-bg-elevated` | `#181818` | Cards, secondary nav, or default component background. |
| `--color-bg-hover` | `#282828` | Interactive hover states on surfaces. |
| `--color-bg-press` | `#111111` | Active/pressed state for components. |

### Brand & Feedback
| Token | Hex | Application |
| :--- | :--- | :--- |
| `--color-primary` | `#1DB954` | Primary actions, success states, active highlights. |
| `--color-primary-hover` | `#1ED760` | Hover state for primary actions. |
| `--color-error` | `#E91429` | Destructive actions, error messaging. |

### Typography & Icons
| Token | Hex | Application |
| :--- | :--- | :--- |
| `--color-text-primary` | `#FFFFFF` | Headings, emphasized text. |
| `--color-text-secondary`| `#B3B3B3` | Body text, metadata, labels. |
| `--color-text-tertiary` | `#A7A7A7` | Captions, disabled states, hint text. |

---

## 2. Typography

- **Font Family:** Primary: `Circular Std`, Fallback: `Inter`, `sans-serif`.
- **Scale:**
  - **H1 (Display):** `2rem (32px)` / Bold / Tracking `-0.04em`
  - **H2 (Section):** `1.5rem (24px)` / Bold / Tracking `-0.02em`
  - **Body (Standard):** `0.875rem (14px)` / Regular / Tracking `normal`
  - **Body (Small):** `0.75rem (12px)` / Medium / Tracking `0.01em`

---

## 3. Elevation & Depth

- **Level 0:** `#000000` (The void)
- **Level 1:** `#121212` (Flat surface)
- **Level 2:** `#181818` (Small components, cards)
- **Level 3:** `#282828` (Overlays, context menus, tooltips)
- **Shadows:** Soft black glows `rgba(0,0,0,0.5)` with `20px` to `40px` blur for floating elements.

---

## 4. Components & Interactive Patterns

### Action Buttons
- **Primary:** Capsule-shaped, `--color-primary` background, `--color-bg-deep` text. Bold typography.
- **Secondary:** Capsule-shaped, `--color-text-primary` border (1px), transparent background.
- **Iconic:** Circular background on hover, no border.

### List Items / Rows
- **Height:** `56px` (Standard) or `48px` (Compact).
- **Behavior:** Full-width hover state using `--color-bg-hover`.
- **Transitions:** `background-color 0.2s ease`, `color 0.2s ease`.

### Inputs
- **Field:** Rectangular with rounded corners (4px). Background: `--color-bg-hover`.
- **Focus:** Border: 1px solid `--color-text-secondary`.

---

## 5. Spacing System
Base unit: `4px`
- **XS:** `4px`
- **S:** `8px`
- **M:** `16px`
- **L:** `24px`
- **XL:** `32px`
- **XXL:** `48px`

---

## 6. Iconography
- **Style:** Linear (Outlined) for default states; Solid (Filled) for active/selected states.
- **Weight:** 2px stroke width.
- **Sizes:** 16px (Small), 24px (Standard), 32px (Large).
