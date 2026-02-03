/**
 * Fuggs CTA Section Component
 * Final call-to-action section
 */

class FuggsCtaSection extends HTMLElement {
  connectedCallback() {
    this.innerHTML = `
      <section class="gradient-cta py-20 px-4 text-white">
        <div class="container mx-auto text-center">
          <h2 class="text-4xl md:text-5xl font-bold mb-6">
            Bereit für intelligente Vereinsbuchhaltung?
          </h2>

          <p class="text-xl md:text-2xl mb-10 max-w-3xl mx-auto opacity-90">
            Starten Sie jetzt kostenlos mit Fuggs und erleben Sie, wie KI Ihre Vereinsbuchhaltung vereinfacht.
          </p>

          <!-- Email Signup Form (Optional) -->
          <div class="max-w-md mx-auto mb-8">
            <form class="flex flex-col sm:flex-row gap-4" id="cta-form">
              <input
                type="email"
                placeholder="Ihre E-Mail-Adresse"
                required
                class="flex-1 px-6 py-4 rounded-lg text-gray-900 focus:outline-none focus:ring-2 focus:ring-white"
              >
              <button
                type="submit"
                class="bg-white text-fuggs-orange px-8 py-4 rounded-lg font-semibold hover:bg-gray-100 transition-colors shadow-lg"
              >
                Kostenlos starten
              </button>
            </form>
          </div>

          <p class="text-sm opacity-75">
            Self-Hosting mit Docker • Open Source • DSGVO-konform
          </p>
        </div>
      </section>
    `;

    // Setup form handler
    this.setupFormHandler();
  }

  setupFormHandler() {
    const form = this.querySelector('#cta-form');
    if (form) {
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        const email = form.querySelector('input[type="email"]').value;

        // For demo purposes, just redirect to app
        // In production, you'd send this to your backend/newsletter service
        console.log('Email signup:', email);
        window.location.href = 'http://localhost:8080';
      });
    }
  }
}

customElements.define('fuggs-cta-section', FuggsCtaSection);
