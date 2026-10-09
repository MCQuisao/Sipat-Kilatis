# Sipat Kilatis — design system

Clean, light, neutral app. **Color is used only for verdicts** (Safe / Careful / Scam), and every verdict
also has its own shield icon and words, so it still reads for color-blind users and in grayscale.

Code: `ui/theme/` (Color, Type, Shape, Spacing, Theme) and `ui/components/` (Components, FloatingNavBar,
ShieldIcons, PermissionsUi, Previews).

## Colors (`SipatColors`)

| Token   | Light     | Dark      | Use |
|---------|-----------|-----------|-----|
| paper   | `#F4F5F7` | `#0F1114` | app background |
| card    | `#FFFFFF` | `#1A1D22` | cards, nav bar, sheets |
| wash    | `#E9EBEF` | `#262A31` | search field, filter track, active nav pill, icon tiles |
| line    | `#E2E4E9` | `#2C3038` | card borders, dividers |
| mute    | `#9CA0A8` | `#6B7079` | placeholders, timestamps |
| subtle  | `#5F646D` | `#A3A8B1` | secondary text |
| ink     | `#14161A` | `#F1F2F4` | text, icons, primary button |

Status colors (`strong` / `container` / `onContainer`):

| Verdict | Light | Dark |
|---------|-------|------|
| Safe (green)    | `#16A34A` / `#DCFCE7` / `#14532D` | `#4ADE80` / `#12301F` / `#BBF7D0` |
| Careful (amber) | `#D97706` / `#FEF3C7` / `#78350F` | `#FBBF24` / `#3A2A08` / `#FDE68A` |
| Scam (red)      | `#DC2626` / `#FEE2E2` / `#7F1D1D` | `#F87171` / `#3D1414` / `#FECACA` |

Text on a status color always uses `onContainer` on `container` (high contrast); `strong` is for icons,
the gauge arc and highlights.

## Type (Inter, bundled in `res/font`, OFL license in `assets/licenses/OFL-Inter.txt`)

Display 32/38 Bold · Headline 26/32 Bold · Title 18/24 SemiBold · BodyL 16/24 · BodyM 14/20 ·
Label 14/20 Medium · Badge 12/16 SemiBold. MessageText = BodyL + 0.15sp tracking (look-alike characters).

## Shapes

input / button 16dp · card 20dp · hero (verdict + protection card) 28dp · small tiles 12dp · pill for badges,
filter tabs and the nav bar · chat bubble with a 6dp "tail" corner.

## Navigation

Floating pill nav bar (`FloatingNavBar`) on the four main screens: Home, History, Scam guide, Settings.
Inactive = ink outlined icon + label; active = soft grey pill (wash) behind a filled icon + bold label.
Check a message and Result are detail screens with a back arrow and no nav bar.
Tab screens end with `navBarClearance()` so the bar never covers content.

## Screens

- **Home**: title + online/offline line, dark protection card (green "On" badge, green switch; amber card
  with Fix when something needs attention), two stat cards, permission checklist card, "Check a message"
  pinned above the nav bar.
- **History**: rounded search, filter tabs with color dots, one card per message (badge, time, sender,
  two-line preview). Swipe to delete with Undo.
- **Result**: verdict card in the verdict's soft color with a gauge (arc fills to the score, ticks at the
  Careful / Scam thresholds), icon + verdict word + one-line summary. Then cards: What to do now,
  The message (red = strong signs like bad links / OTP, amber = pressure words, red boxes = look-alike
  characters; tap for "Why is this risky?"), Why we think so. Actions: Mark as safe (green tonal),
  Report scam (red tonal), Copy message, Check another.
- **Scam guide**: expandable cards; the example is shown as an incoming text bubble with highlights;
  tips with check tiles; "What to do" in a green box.
- **Settings**: one card per group (Language, Appearance, Protection, Trusted contacts, AI explanations,
  Your data, Privacy, About).

## Accessibility

Verdicts never rely on color alone (icon + words + number). Gauge and verdict card have one TalkBack
description ("Likely a scam, 90 out of 100"). Highlights are TalkBack custom actions. Animations are skipped
when the system "Remove animations" setting is on. Touch targets ≥ 48dp.
