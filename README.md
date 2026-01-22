# Fuggs

Wir wollen eine cloudbasierte Open Source Buchhaltungssoftware mit AI entwickeln, damit Vereine mehr Zeit für ihre
wesentlichen Ziele und Angebote haben und frustrierte Situationen mit der Buchhaltung der Vergangenheit angehören.

Fuggs ist ein Fork von [Hopps](https://github.com/hopps-app).

[![MIT License](https://img.shields.io/badge/License-MIT-green.svg)](https://choosealicense.com/licenses/mit/)

## Build Status

| Service | Status |
|---------|--------|
| **Main App** | [![Build fuggs-app](https://github.com/fuggs-app/fuggs/actions/workflows/fuggs-app.yml/badge.svg?branch=main)](https://github.com/fuggs-app/fuggs/actions/workflows/fuggs-app.yml) |
| **ZugFerd Service** | [![Build zugferd-service](https://github.com/fuggs-app/fuggs/actions/workflows/zugferd-service.yml/badge.svg?branch=main)](https://github.com/fuggs-app/fuggs/actions/workflows/zugferd-service.yml) |
| **Azure Document AI** | [![Build az-document-ai-service](https://github.com/fuggs-app/fuggs/actions/workflows/az-document-ai-service.yml/badge.svg?branch=main)](https://github.com/fuggs-app/fuggs/actions/workflows/az-document-ai-service.yml) |

## Features

### Intelligent Document Processing

Fuggs automatisiert die Belegverarbeitung mit einem intelligenten Multi-Schritt-Ansatz:

1. **ZugFerd-Extraktion** - Automatische Erkennung und Extraktion von strukturierten ZugFerd/Factur-X e-Rechnungen aus PDFs
2. **KI-gestützte Analyse** - Fallback auf Azure Document Intelligence für Belege ohne eingebettete Daten
3. **Transparente Herkunft** - Nachvollziehbarkeit, ob Daten via ZugFerd, KI oder manuell erfasst wurden

Die ZugFerd-Integration nutzt das Open-Source [Mustang Project](https://www.mustangproject.org/) und unterstützt deutsche e-Rechnungsstandards (ZugFerd, XRechnung, Factur-X).

## Contributors

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="http://explore.de"><img src="https://avatars.githubusercontent.com/u/545499?v=4?s=100" width="100px;" alt="Markus Herhoffer"/><br /><sub><b>Markus Herhoffer</b></sub></a><br /><a href="https://github.com/fuggs-app/fuggs/commits?author=d135-1r43" title="Code">💻</a> <a href="#business-d135-1r43" title="Business development">💼</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/schitcrafter"><img src="https://avatars.githubusercontent.com/u/58911293?v=4?s=100" width="100px;" alt="Emilia Jaser"/><br /><sub><b>Emilia Jaser</b></sub></a><br /><a href="#tool-schitcrafter" title="Tools">🔧</a> <a href="https://github.com/fuggs-app/fuggs/commits?author=schitcrafter" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="http://www.hummli.de"><img src="https://avatars.githubusercontent.com/u/25209702?v=4?s=100" width="100px;" alt="Manuel hummler"/><br /><sub><b>Manuel hummler</b></sub></a><br /><a href="https://github.com/fuggs-app/fuggs/commits?author=manuelhummler" title="Code">💻</a> <a href="#business-manuelhummler" title="Business development">💼</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/3thr3n"><img src="https://avatars.githubusercontent.com/u/33932819?v=4?s=100" width="100px;" alt="3thr3n"/><br /><sub><b>3thr3n</b></sub></a><br /><a href="https://github.com/fuggs-app/fuggs/commits?author=3thr3n" title="Code">💻</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

### How to use the allcontributors bot

Guide: https://allcontributors.org/docs/en/bot/usage

Available emojis: https://allcontributors.org/docs/en/emoji-key
