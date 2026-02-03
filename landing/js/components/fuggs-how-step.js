/**
 * Fuggs How-It-Works Step Component
 * Numbered step component for process explanation
 */

class FuggsHowStep extends HTMLElement {
  connectedCallback() {
    const step = this.getAttribute('step') || '1';
    const title = this.getAttribute('title') || 'Step Title';
    const description = this.getAttribute('description') || 'Step description';

    this.innerHTML = `
      <div class="flex-1 text-center p-6 relative">
        <!-- Step Number Circle -->
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-fuggs-orange text-white text-2xl font-bold mb-4 shadow-lg">
          ${step}
        </div>

        <!-- Step Title -->
        <h3 class="text-xl font-semibold mb-2 text-gray-900">${title}</h3>

        <!-- Step Description -->
        <p class="text-gray-600">${description}</p>

        <!-- Arrow Connector (hidden on last step via CSS) -->
        <div class="hidden md:block absolute top-8 left-full w-full arrow-connector">
          <svg class="w-8 h-8 text-fuggs-orange mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6"/>
          </svg>
        </div>
      </div>
    `;
  }
}

customElements.define('fuggs-how-step', FuggsHowStep);
