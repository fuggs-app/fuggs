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
      <div class="flex-1 text-center p-6">
        <!-- Step Number Circle -->
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-fuggs-orange text-white text-2xl font-bold mb-4 shadow-lg">
          ${step}
        </div>

        <!-- Step Title -->
        <h3 class="text-xl font-semibold mb-2 text-gray-900">${title}</h3>

        <!-- Step Description -->
        <p class="text-gray-600">${description}</p>
      </div>
    `;
  }
}

customElements.define('fuggs-how-step', FuggsHowStep);
