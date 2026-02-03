/**
 * Fuggs Footer Component
 * Site footer with links and copyright
 */

class FuggsFooter extends HTMLElement {
  connectedCallback() {
    const currentYear = new Date().getFullYear();

    this.innerHTML = `
      <footer class="bg-gray-900 text-white py-12 px-4">
        <div class="container mx-auto">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-8 mb-8">
            <!-- Brand Column -->
            <div>
              <img src="assets/images/logo.svg" alt="Fuggs" class="h-8 mb-4 brightness-0 invert">
              <p class="text-gray-400">
                Open Source Vereinsbuchhaltung mit KI-Unterstützung für Vereine und kleine Organisationen.
              </p>
            </div>

            <!-- Links Column -->
            <div>
              <h3 class="font-semibold mb-4">Ressourcen</h3>
              <ul class="space-y-2 text-gray-400">
                <li>
                  <a href="https://github.com/fuggs-app/fuggs" target="_blank" rel="noopener" class="hover:text-fuggs-orange transition-colors">
                    GitHub Repository
                  </a>
                </li>
                <li>
                  <a href="https://github.com/fuggs-app/fuggs/blob/main/CONTRIBUTING.md" target="_blank" rel="noopener" class="hover:text-fuggs-orange transition-colors">
                    Contributing Guide
                  </a>
                </li>
                <li>
                  <a href="https://github.com/fuggs-app/fuggs/discussions" target="_blank" rel="noopener" class="hover:text-fuggs-orange transition-colors">
                    Discussions
                  </a>
                </li>
              </ul>
            </div>

            <!-- Contact/Social Column -->
            <div>
              <h3 class="font-semibold mb-4">Community</h3>
              <ul class="space-y-2 text-gray-400">
                <li>
                  <a href="https://github.com/fuggs-app/fuggs/discussions" target="_blank" rel="noopener" class="hover:text-fuggs-orange transition-colors">
                    Fragen & Antworten
                  </a>
                </li>
                <li>
                  <a href="mailto:hello@fuggs.app" class="hover:text-fuggs-orange transition-colors">
                    Kontakt
                  </a>
                </li>
              </ul>

              <!-- Social Icons -->
              <div class="flex gap-4 mt-6">
                <a href="https://github.com/fuggs-app/fuggs" target="_blank" rel="noopener" class="text-gray-400 hover:text-fuggs-orange transition-colors" aria-label="GitHub">
                  <svg class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                  </svg>
                </a>
              </div>
            </div>
          </div>

          <!-- Bottom Bar -->
          <div class="border-t border-gray-800 pt-8 flex flex-col md:flex-row justify-between items-center text-gray-400 text-sm">
            <p>&copy; ${currentYear} Fuggs. Open Source unter <a href="https://github.com/fuggs-app/fuggs/blob/main/LICENSE" target="_blank" rel="noopener" class="text-fuggs-orange hover:underline">MIT Lizenz</a>.</p>
            <div class="flex gap-6 mt-4 md:mt-0">
              <a href="/privacy" class="hover:text-fuggs-orange transition-colors">Datenschutz</a>
              <a href="/imprint" class="hover:text-fuggs-orange transition-colors">Impressum</a>
            </div>
          </div>
        </div>
      </footer>
    `;
  }
}

customElements.define('fuggs-footer', FuggsFooter);
