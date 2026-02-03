/**
 * Fuggs Navigation Component
 * Sticky navigation bar with logo, menu, and CTA
 */

class FuggsNav extends HTMLElement {
  connectedCallback() {
    this.innerHTML = `
      <nav class="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-md border-b border-gray-200 transition-all duration-300" id="main-nav">
        <div class="container mx-auto">
          <div class="flex items-center justify-between h-16">
            <!-- Logo -->
            <a href="/" class="flex items-center">
              <img src="assets/images/logo.svg" alt="Fuggs" class="h-8">
            </a>

            <!-- Desktop Menu -->
            <div class="hidden md:flex items-center space-x-8">
              <a href="#features" class="text-gray-700 hover:text-fuggs-orange transition-colors">Features</a>
              <a href="#how-it-works" class="text-gray-700 hover:text-fuggs-orange transition-colors">So funktioniert's</a>
              <a href="#technology" class="text-gray-700 hover:text-fuggs-orange transition-colors">Technologie</a>
              <a href="https://github.com/your-org/fuggs" target="_blank" rel="noopener" class="text-gray-700 hover:text-fuggs-orange transition-colors flex items-center gap-1">
                GitHub
                <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
              </a>
            </div>

            <!-- CTA Button (Desktop) -->
            <div class="hidden md:block">
              <a href="http://localhost:8080" class="btn-primary">
                Jetzt starten
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6"/>
                </svg>
              </a>
            </div>

            <!-- Mobile Menu Button -->
            <button class="md:hidden p-2 text-gray-700 hover:text-fuggs-orange transition-colors" id="mobile-menu-btn" aria-label="Toggle menu">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
              </svg>
            </button>
          </div>

          <!-- Mobile Menu -->
          <div class="md:hidden hidden pb-4" id="mobile-menu">
            <div class="flex flex-col space-y-4">
              <a href="#features" class="text-gray-700 hover:text-fuggs-orange transition-colors">Features</a>
              <a href="#how-it-works" class="text-gray-700 hover:text-fuggs-orange transition-colors">So funktioniert's</a>
              <a href="#technology" class="text-gray-700 hover:text-fuggs-orange transition-colors">Technologie</a>
              <a href="https://github.com/your-org/fuggs" target="_blank" rel="noopener" class="text-gray-700 hover:text-fuggs-orange transition-colors flex items-center gap-1">
                GitHub
                <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
              </a>
              <a href="http://localhost:8080" class="btn-primary inline-flex justify-center">
                Jetzt starten
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6"/>
                </svg>
              </a>
            </div>
          </div>
        </div>
      </nav>
    `;

    // Setup event listeners
    this.setupEventListeners();
  }

  setupEventListeners() {
    // Mobile menu toggle
    const mobileMenuBtn = this.querySelector('#mobile-menu-btn');
    const mobileMenu = this.querySelector('#mobile-menu');

    if (mobileMenuBtn && mobileMenu) {
      mobileMenuBtn.addEventListener('click', () => {
        mobileMenu.classList.toggle('hidden');
      });

      // Close mobile menu when clicking on a link
      const mobileLinks = mobileMenu.querySelectorAll('a');
      mobileLinks.forEach(link => {
        link.addEventListener('click', () => {
          mobileMenu.classList.add('hidden');
        });
      });
    }

    // Scroll behavior - add shadow and solid background when scrolled
    const nav = this.querySelector('#main-nav');
    if (nav) {
      window.addEventListener('scroll', () => {
        if (window.scrollY > 20) {
          nav.classList.add('shadow-md', 'bg-white');
          nav.classList.remove('bg-white/80');
        } else {
          nav.classList.remove('shadow-md', 'bg-white');
          nav.classList.add('bg-white/80');
        }
      });
    }
  }
}

customElements.define('fuggs-nav', FuggsNav);
