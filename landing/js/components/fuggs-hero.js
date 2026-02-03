/**
 * Fuggs Hero Section Component
 * Main hero section with headline and CTAs
 */

class FuggsHero extends HTMLElement {
  connectedCallback() {
    this.innerHTML = `
      <section class="gradient-hero pt-32 pb-20 px-4">
        <div class="container mx-auto">
          <div class="max-w-4xl mx-auto text-center">
            <!-- Beta Badge -->
            <div class="inline-flex items-center gap-2 bg-fuggs-orange-light text-fuggs-orange px-4 py-2 rounded-full font-semibold mb-6">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"/>
              </svg>
              Jetzt in Closed Beta
            </div>

            <!-- Main Headline -->
            <h1 class="text-5xl md:text-6xl font-bold mb-6 text-gray-900">
              Vereinsbuchhaltung. <span class="text-fuggs-orange">Intelligent.</span> Einfach.
            </h1>

            <!-- Subheadline -->
            <p class="text-xl md:text-2xl text-gray-700 mb-10 max-w-3xl mx-auto">
              KI-gestützte Open Source Vereinsbuchhaltung für Vereine und kleine Organisationen.
              Self-Hosting mit Docker, automatische Belegverarbeitung und moderne Benutzeroberfläche.
            </p>

            <!-- CTA Buttons -->
            <div class="flex flex-col sm:flex-row gap-4 justify-center items-center">
              <a href="#newsletter" class="btn-primary text-lg px-8 py-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
                </svg>
                Für Public Beta anmelden
              </a>
              <a href="#how-it-works" class="btn-secondary text-lg px-8 py-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
                Mehr erfahren
              </a>
            </div>

            <!-- Social Proof / Stats -->
            <div class="mt-16 grid grid-cols-1 md:grid-cols-3 gap-8 max-w-3xl mx-auto">
              <div class="text-center">
                <div class="text-3xl font-bold text-fuggs-orange mb-2">Self-Hosting</div>
                <div class="text-gray-600">Docker Compose</div>
              </div>
              <div class="text-center">
                <div class="text-3xl font-bold text-fuggs-orange mb-2">Open Source</div>
                <div class="text-gray-600">100% transparent</div>
              </div>
              <div class="text-center">
                <div class="text-3xl font-bold text-fuggs-orange mb-2">KI-gestützt</div>
                <div class="text-gray-600">Automatische Extraktion</div>
              </div>
            </div>
          </div>
        </div>
      </section>
    `;
  }
}

customElements.define('fuggs-hero', FuggsHero);
