# Business constants and localization

Product names, categories and descriptions are represented by business constants.
SPP can supply defaults instead of scattering display strings through calculation
services. Identifiers such as product, member and order numbers remain stable
when the display language changes. Named numeric constants do not change with
the language of the surrounding text.

The following illustrative snippet uses an English default:

```spp
const String CAT_NAME = "British Shorthair";
product.name = CAT_NAME;
```

APP chooses the default and supported locales:

```app
i18n { defaultLocale "en-US"; supportedLocales "en-US", "en-GB"; }
```

PHY supplies a localized representation under the same constant name:

```phy
phy {
    constants "en-GB" { CAT_NAME = "British Shorthair cat"; }
}
```

The compiler checks constant uniqueness, default-value types, the locale list,
translation names and string types. Untranslated constants use their defaults;
unknown request locales use APP's default locale. Numeric business constants
cannot have locale overrides: choosing a language must not change amount or
quantity rules. Initial values are literals; expression dependencies between
constants are not supported in this recorded design.

The generic web host reads the generated constant catalog, derives the locale
from `Accept-Language`, and restores the previous context after the request.
Locale is not an SPP business parameter. The existing Chinese UI and optional
Chinese/English product resources are separate from this English documentation.
Product browsing recalculates named text constants, not persisted prices or stock.
Customer-entered names and addresses are not translated as display copy.

Across domains, the domain name and constant name identify a value. Catalogs are
generated per domain; the application host loads all deployed domain catalogs.
The product domain owns product text constants and calculations. The shop invokes
the calculation through APP binding to populate text facts only. See the
[multi-domain implementation record](multidomain-implementation.md) for its scope;
single-domain compilation is not proof of cross-domain localization at runtime.

English adaptation of revision `417a41f`. The illustrative locale pair above was
changed to English variants; business identifiers and localization rules are
unchanged. No new runtime verification is claimed by this translation.
