/**
 * Fuggs Landing Page - Main JavaScript
 * Global features and initialization
 */

(function() {
  'use strict';

  /**
   * Smooth scrolling for anchor links
   */
  function initSmoothScrolling() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
      anchor.addEventListener('click', function(e) {
        const href = this.getAttribute('href');
        if (href === '#') return;

        e.preventDefault();
        const target = document.querySelector(href);

        if (target) {
          const navHeight = 64; // Height of fixed nav
          const targetPosition = target.offsetTop - navHeight;

          window.scrollTo({
            top: targetPosition,
            behavior: 'smooth'
          });
        }
      });
    });
  }

  /**
   * Intersection Observer for fade-in animations
   */
  function initScrollAnimations() {
    const observerOptions = {
      threshold: 0.1,
      rootMargin: '0px 0px -50px 0px'
    };

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          // Unobserve after animating to improve performance
          observer.unobserve(entry.target);
        }
      });
    }, observerOptions);

    // Observe all elements with fade-in class
    document.querySelectorAll('.fade-in').forEach(el => {
      observer.observe(el);
    });
  }

  /**
   * Initialize all features when DOM is ready
   */
  function init() {
    // Wait for Web Components to be defined
    Promise.all([
      customElements.whenDefined('fuggs-nav'),
      customElements.whenDefined('fuggs-hero'),
      customElements.whenDefined('fuggs-feature-card'),
      customElements.whenDefined('fuggs-how-step'),
      customElements.whenDefined('fuggs-cta-section'),
      customElements.whenDefined('fuggs-footer')
    ]).then(() => {
      // Initialize smooth scrolling
      initSmoothScrolling();

      // Initialize scroll animations
      initScrollAnimations();

      // Log initialization
      console.log('Fuggs Landing Page initialized');
    });
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
