# Shared Compose components and IDR input

Initial implementation, 15 November 2026. Source:
`app/src/main/java/com/dauntehnusantara/pos/core/designsystem/components/`.
Use the existing Material3 theme from core/designsystem; no additional UI library or
new palette. Keep feature state, permissions, validation, callbacks and business
calculations in their existing feature/domain layers.

## Components and current adoption

| Component | Responsibility | Adopted in |
| --- | --- | --- |
| AppScreenHeader | Page heading/description/type/spacing | Reports, staff, settings |
| AppSection | Section heading, spacing and divider | Settings groups |
| AppNotice / AppErrorText | Semantic surface/text/live region | Catalog, POS, management/settings |
| AppListRow | Shared row spacing and divider | Staff, report rows |
| AppEmptyState | Consistent empty-state text | Staff and zero-sale report |
| AppIdentity | Selectable public ID and optional label | Staff devices, settings device identity |
| AppPrimaryButton | 48dp minimum, disabled busy action and progress | Checkout, staff/settings confirmations |
| AppTextField | Label/error/help/shape/keyboard presentation | Catalog text/integer fields, POS quantity |
| MoneyInputField | Whole IDR prefix/grouping and normalized paste | Catalog price/option delta, checkout cash tender |
| AppConfirmation | Adaptive dialog/sheet, scrolling body, fixed actions | Staff access/device and settings export/sign-out |

This is an incremental adoption. Feature-specific editor/payment/filter layouts are
not forced into one generic form. Catalog/other existing specialized dialogs can be
adopted later when their body/action contracts fit. Avoid adding flags merely to
make one exceptional screen use a common component.

AppConfirmation chooses a bounded max560dp dialog at readable >=600dp width and
>=500dp height; compact, short and >=150% text uses a sheet. Body scrolls separately
from actions, with IME padding. Busy blocks dismissal; callers must also gate their
buttons and provide named progress copy. The component owns no target/reason/query,
permission decision, saved authority or network operation. Features retain focus
restoration and cancellation/result rules. Body/action slots preserve feature copy.

AppNotice tone is visual meaning only; a success color never establishes server ACK,
backup, completed provider write or authorization. Prefer explicit text. AppIdentity
is for public IDs, never tokens, secrets, passwords or credential-bearing URLs.

## Money input contract

MoneyInputField takes a **raw String** and emits another raw String. It does not
own a Long value or silently choose zero for empty input. The field displays an Rp
prefix and dot thousands grouping through IdrInputTransformation. Prefix/separators
are not stored in draft values or passed to checkout commands.

```kotlin
MoneyInputField(
    value = draft.price,
    onValueChange = { raw -> updatePriceDraft(raw) },
    label = "Harga (IDR)",
    isError = priceError != null,
    supportingText = priceError,
)
```

Existing feature validation remains authoritative. Parse nonnegative whole digits
with moneyInputLongOrNull, then apply the existing domain checks. Money values remain
Long IDR; no Float/Double/decimal rounding is introduced. Zero remains allowed where
the feature/domain permits it. Price, modifier delta and cash tender use this field;
quantity, ordering and selection limits stay ordinary integer fields.

| Input | Raw value / behavior |
| --- | --- |
| `10000` | `10000`, displayed `10.000` with Rp prefix |
| `Rp 10.000`, `rp 10.000` | Valid pasted IDR becomes `10000` |
| `1.000` | Indonesian whole-IDR grouping: `1000`, not decimal 1 |
| Empty/whitespace | Empty; never coerced to zero |
| `0`, leading zeros | Preserved as digit drafts; feature validates numeric value |
| `1,50`, `1.5`, `1.000,50`, `-1000`, letters, foreign grouping | Preserved visibly; existing validation rejects |
| Long.MAX_VALUE | Exactly `9.223.372.036.854.775.807`; parses without rounding |
| Long overflow | Remains visible; parse returns null and feature submission stays invalid |

normalizeMoneyInput accepts only ASCII whole digits, optionally a case-insensitive Rp
prefix/space/NBSP and correctly grouped dots. It does not strip arbitrary punctuation
from invalid input. Formatting uses string characters, so even overflow drafts display
without conversion to a numeric type. Invalid drafts use identity transformation.
Cursor mapping is monotonic/bounded and round-trips every raw position; separators
map to adjacent digit boundaries. Grouping/selection/deletion is presentation only.

## Verification and limits

MoneyInputTest covers strict paste, decimal/negative/malformed input, empty/zero,
Long precision/overflow and every cursor offset for lengths0–80. Shared component
Compose tests cover paste/clear/middle insertion/deletion, invalid decimal preservation
and busy confirmation action gating. Existing catalog, POS, report, staff and settings
regressions cover behavior/adaptive layouts after migration. Full results and commands
are recorded in PROGRESS.md.

No Room, server, sync policy, payment rules, authorization or sale snapshot changes.
Device checks/install remain paused by user request. Physical keyboards/IME/TalkBack,
OEM appearance and normal-app provider/auth acceptance remain separate M7 checks.

## Form sizing aligned to login (16 November 2026)

AppTextField is the single outlined input implementation, including login, password
change, staff creation/reasons, catalog, money/quantity, date filters and searches.
Use the login baseline: Material 3 default internal padding/label transitions,
56dp minimum (not fixed) height, theme bodyLarge input text (16sp/24sp line height)
and medium rounded shape (12dp). Parent heading typography must not enlarge inputs.
MoneyInputField inherits the same styling while retaining its strict IDR mapping.

Use leadingIcon/trailingIcon and keyboardActions slots for searches/password/IME;
do not fork field styling. Multiline fields explicitly set singleLine=false and can
grow. Supporting/error text occupies additional space below the outline; accessibility
font scaling can increase height. Do not force the entire input/error block to 56dp.
Form widths retain existing adaptive layout constraints, not one fixed phone width.
