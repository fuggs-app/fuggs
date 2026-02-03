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
              <a href="http://localhost:8080" class="btn-primary text-lg px-8 py-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
                </svg>
                Jetzt kostenlos starten
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
