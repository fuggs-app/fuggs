/**
 * Fuggs CTA Section Component
 * Final call-to-action section
 */

class FuggsCtaSection extends HTMLElement {
  connectedCallback() {
    this.innerHTML = `
      <section id="newsletter" class="gradient-cta py-20 px-4 text-white">
        <div class="container mx-auto text-center">
          <h2 class="text-4xl md:text-5xl font-bold mb-6">
            Jetzt für Public Beta anmelden
          </h2>

          <p class="text-xl md:text-2xl mb-10 max-w-3xl mx-auto opacity-90">
            Fuggs befindet sich aktuell in der Closed Beta. Melden Sie sich für unseren Newsletter an
            und erhalten Sie als Erste:r Zugang zur Public Beta.
          </p>

          <!-- Email Signup Form -->
          <div class="max-w-md mx-auto mb-8">
            <form class="flex flex-col sm:flex-row gap-4" id="cta-form">
              <input
                type="email"
                name="email"
                placeholder="Ihre E-Mail-Adresse"
                required
                class="flex-1 px-6 py-4 rounded-lg text-gray-900 focus:outline-none focus:ring-2 focus:ring-white"
              >
              <button
                type="submit"
                class="bg-white text-fuggs-orange px-8 py-4 rounded-lg font-semibold hover:bg-gray-100 transition-colors shadow-lg"
              >
                Anmelden
              </button>
            </form>
          </div>

          <p class="text-sm opacity-75">
            Kostenlos • Self-Hosting mit Docker • Open Source • DSGVO-konform
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

        // TODO: Integrate with your newsletter service (e.g., Mailchimp, SendGrid, etc.)
        // For now, show success message
        console.log('Newsletter signup:', email);

        // Show success message
        const formContainer = form.parentElement;
        formContainer.innerHTML = `
          <div class="bg-white/20 backdrop-blur-sm border border-white/30 rounded-lg p-8 text-center">
            <svg class="w-16 h-16 text-white mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <h3 class="text-2xl font-bold mb-2">Vielen Dank!</h3>
            <p class="text-lg">Wir haben Ihre Anmeldung erhalten. Sie erhalten in Kürze eine E-Mail mit weiteren Informationen zur Public Beta.</p>
          </div>
        `;
      });
    }
  }
}

customElements.define('fuggs-cta-section', FuggsCtaSection);
