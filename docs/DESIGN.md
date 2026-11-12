# DaySpark design notes

Calm and polished: warm surfaces, one confident accent per theme, generous spacing, no visual
noise. Every theme defines the same color roles, so layouts reference roles
(`?attr/colorPrimary`) and never raw colors.

## Color roles

| Role | DaySpark (free) | Forest | Ocean | Lavender | Sunset |
|---|---|---|---|---|---|
| Background | `#FBF8F3` | `#F6F8F2` | `#F3F8FB` | `#F8F6FC` | `#FDF6F1` |
| Card surface | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| Text | `#14213D` | `#1B2A1F` | `#0B2540` | `#241A3D` | `#2B1A14` |
| Secondary text | `#4A5568` | `#4B5A4F` | `#46596B` | `#564C6B` | `#65504A` |
| Primary (accent) | `#0F766E` | `#2F6B3A` | `#0B6A94` | `#6B4FA3` | `#A8432A` |
| Container | `#CCEFEA` | `#D6E8D2` | `#CDE8F5` | `#E6DDF7` | `#FADCCB` |
| On container | `#134E4A` | `#1E4526` | `#0A4766` | `#46307A` | `#7A2E1B` |
| Outline | `#7D838C` | `#7B877F` | `#788692` | `#857E92` | `#8E7F78` |

Shared: error `#B42318`, error container `#FEE4E2`, streak flame `#E8A33D` (decorative only,
never used for text), white text on primary buttons.

## Contrast (WCAG 2.1)

| Pair | DaySpark | Forest | Ocean | Lavender | Sunset | Needed |
|---|---|---|---|---|---|---|
| Text on background | 15.1 | 14.0 | 14.5 | 15.2 | 15.6 | 4.5 |
| Secondary text on background | 7.1 | 6.8 | 6.8 | 7.4 | 7.0 | 4.5 |
| White on primary button | 5.5 | 6.4 | 6.0 | 6.4 | 6.0 | 4.5 |
| On-container on container | 7.7 | 8.4 | 7.8 | 8.2 | 7.2 | 4.5 |
| Primary on background | 5.2 | 6.0 | 5.6 | 6.0 | 5.6 | 4.5 |
| Outline on background | 3.6 | 3.5 | 3.5 | 3.6 | 3.6 | 3.0 (non-text) |
| Error on background | 6.2 | 6.1 | 6.2 | 6.1 | 6.2 | 4.5 |

## Type scale (sp, follows system font size)

| Style | Size | Use |
|---|---|---|
| Display | 28 medium | Dashboard greeting, habit name on detail |
| Headline | 22 medium | Empty-state title |
| Title | 20 medium | Toolbar titles |
| Subtitle | 17 medium | Habit names, card titles |
| Body | 16 | Main copy |
| Body small | 14 | Schedules, descriptions |
| Label | 15 medium | Buttons |
| Caption | 13 | Dates, section labels, stat labels |

## Spacing and shape

4 dp base: 4 / 8 / 12 / 16 / 24 / 32. Page margin 20 dp (32 dp at ≥600 dp width); content
capped at 640 dp. Corners: 8 (small), 14 (buttons, fields), 18 (cards), 28 (sheets). Cards are
flat with a 1 dp outline; the dashboard progress card and purchase card use the tinted container.
Touch targets are at least 48 dp.

## Launch and icon

Adaptive icon: a warm-white four-point spark with a small amber companion on teal, plus a
monochrome layer for themed icons (Android 13+). The launch screen shows the same spark on a
teal disc over the warm-white background and stays up until the dashboard has loaded.
