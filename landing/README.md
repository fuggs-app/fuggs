# Fuggs Landing Page

Modern, stylish landing page for Fuggs - the intelligent open-source accounting software for clubs and small organizations.

## Overview

This is a static landing page built with:

- **Custom Web Components** (native Custom Elements API)
- **Tailwind CSS** (via CDN)
- **IBM Plex Sans** font
- **Fuggs brand colors** (#FF521D)
- **No build process required** - pure HTML/CSS/JS

## Features

- ✅ Fully responsive (mobile-first design)
- ✅ Modern Web Components architecture
- ✅ Fast loading with CDN resources
- ✅ Accessible (semantic HTML, ARIA labels, keyboard navigation)
- ✅ SEO optimized
- ✅ Smooth scroll animations
- ✅ Sticky navigation with scroll effects
- ✅ Mobile-friendly hamburger menu

## Project Structure

```
landing/
├── index.html                # Main landing page
├── css/
│   └── styles.css            # Custom styles and design tokens
├── js/
│   ├── main.js               # Global features (smooth scroll, animations)
│   └── components/           # Web Components
│       ├── fuggs-nav.js      # Navigation component
│       ├── fuggs-hero.js     # Hero section
│       ├── fuggs-feature-card.js  # Reusable feature cards
│       ├── fuggs-how-step.js      # Process step component
│       ├── fuggs-cta-section.js   # Call-to-action section
│       └── fuggs-footer.js        # Footer component
├── assets/
│   └── images/
│       └── logo.svg          # Fuggs logo
└── README.md                 # This file
```

## Web Components

### Component Reference

All components are custom elements that can be used declaratively in HTML:

#### `<fuggs-nav>`
Sticky navigation bar with logo, menu links, and CTA button.

**Usage:**
```html
<fuggs-nav></fuggs-nav>
```

**Features:**
- Responsive mobile menu
- Scroll-based styling (transparent → solid background)
- Auto-close menu on link click

---

#### `<fuggs-hero>`
Hero section with headline, subheadline, and CTA buttons.

**Usage:**
```html
<fuggs-hero></fuggs-hero>
```

**Features:**
- Gradient background
- Responsive layout
- Social proof stats

---

#### `<fuggs-feature-card>`
Reusable feature card component with icon, title, and description.

**Usage:**
```html
<fuggs-feature-card
  icon="ai"
  title="Intelligente Belegverarbeitung"
  description="Automatische Extraktion...">
</fuggs-feature-card>
```

**Attributes:**
- `icon` (string): Icon identifier - "ai", "opensource", "clock", "cloud", "workflow", "secure"
- `title` (string): Card title
- `description` (string): Card description

**Features:**
- Hover animation (lift + shadow)
- SVG icons
- Responsive design

---

#### `<fuggs-how-step>`
Numbered process step component.

**Usage:**
```html
<fuggs-how-step
  step="1"
  title="Upload"
  description="Laden Sie Ihre Belege hoch">
</fuggs-how-step>
```

**Attributes:**
- `step` (string): Step number
- `title` (string): Step title
- `description` (string): Step description

**Features:**
- Numbered circle badge
- Arrow connectors between steps
- Responsive layout

---

#### `<fuggs-cta-section>`
Final call-to-action section with email signup form.

**Usage:**
```html
<fuggs-cta-section></fuggs-cta-section>
```

**Features:**
- Gradient background
- Email signup form
- Form validation

---

#### `<fuggs-footer>`
Site footer with links, logo, and copyright.

**Usage:**
```html
<fuggs-footer></fuggs-footer>
```

**Features:**
- Multi-column layout
- Social media links
- Dynamic copyright year

---

## Local Development

### Prerequisites

- Modern web browser with ES6 module support
- Local web server (to avoid CORS issues with ES6 modules)

### Running Locally

**Option 1: Python (recommended)**

```bash
cd landing
python3 -m http.server 8000
```

Then open: http://localhost:8000

**Option 2: Node.js (using http-server)**

```bash
cd landing
npx http-server -p 8000
```

**Option 3: PHP**

```bash
cd landing
php -S localhost:8000
```

**Option 4: VS Code Live Server Extension**

1. Install "Live Server" extension in VS Code
2. Right-click `index.html` → "Open with Live Server"

### Browser Compatibility

- ✅ Chrome/Edge 67+
- ✅ Firefox 63+
- ✅ Safari 10.1+
- ✅ Mobile browsers (iOS Safari, Chrome Mobile)

Custom Elements are widely supported in modern browsers. No polyfills required.

## Deployment

This is a static website that can be deployed anywhere:

### GitHub Pages (Recommended for Open Source)

1. Push to GitHub repository
2. Go to Settings → Pages
3. Select branch and `/landing` folder
4. Save

Your site will be live at `https://your-username.github.io/fuggs/`

### Netlify

1. Drag and drop the `landing/` folder to Netlify
2. Or connect GitHub repository and set build directory to `landing/`
3. Deploy

### Vercel

```bash
cd landing
vercel
```

### Cloudflare Pages

1. Connect GitHub repository
2. Set build directory to `landing/`
3. Deploy

### Custom Server (Apache/Nginx)

Simply copy the `landing/` directory to your web server's document root.

**Example Nginx config:**

```nginx
server {
    listen 80;
    server_name fuggs.app;
    root /var/www/fuggs/landing;
    index index.html;

    location / {
        try_files $uri $uri/ =404;
    }
}
```

## Customization

### Brand Colors

Edit `css/styles.css` to change brand colors:

```css
:root {
  --fuggs-primary: #FF521D;
  --fuggs-primary-hover: #E6491A;
  --fuggs-primary-active: #CC4017;
  --fuggs-primary-light: #FFE5DC;
}
```

### Content

Edit `index.html` to change:
- Section headings
- Feature descriptions
- Technology badges
- Links to GitHub/documentation

### Components

Edit individual component files in `js/components/` to customize:
- Navigation menu items
- Hero section text
- Feature card icons
- Footer links

### Adding New Icons

Edit `fuggs-feature-card.js` and add to the `getIcon()` method:

```javascript
getIcon(iconName) {
  const icons = {
    'ai': `<svg>...</svg>`,
    'your-new-icon': `<svg>...</svg>` // Add here
  };
  return icons[iconName] || icons['ai'];
}
```

## Design Tokens

### Colors

| Token | Value | Usage |
|-------|-------|-------|
| `--fuggs-primary` | #FF521D | Primary brand color |
| `--fuggs-primary-hover` | #E6491A | Hover states |
| `--fuggs-primary-active` | #CC4017 | Active states |
| `--fuggs-primary-light` | #FFE5DC | Backgrounds |
| `--fuggs-success` | #24a148 | Success states |
| `--fuggs-warning` | #f1c21b | Warning states |
| `--fuggs-danger` | #da1e28 | Error states |

### Typography

- **Font Family:** IBM Plex Sans
- **Weights:** 400 (Regular), 500 (Medium), 600 (Semibold), 700 (Bold)
- **Scale:**
  - h1: 3rem (desktop), 2rem (mobile)
  - h2: 2rem (desktop), 1.5rem (mobile)
  - h3: 1.5rem (desktop), 1.25rem (mobile)
  - body: 1rem

### Spacing

| Token | Value |
|-------|-------|
| `--spacing-xs` | 0.5rem |
| `--spacing-sm` | 1rem |
| `--spacing-md` | 2rem |
| `--spacing-lg` | 3rem |
| `--spacing-xl` | 4rem |
| `--spacing-2xl` | 6rem |

## Performance

- **First Contentful Paint:** < 1s (CDN resources)
- **Time to Interactive:** < 2s
- **Lighthouse Score:** 90+ (Performance, Accessibility, Best Practices, SEO)

### Optimization Tips

1. **Preconnect to CDNs** (already implemented)
2. **Use proper image formats** (SVG for logo)
3. **Minimize custom CSS** (Tailwind handles most styling)
4. **Lazy load images** (if adding product screenshots)

## Accessibility

- ✅ Semantic HTML structure
- ✅ ARIA labels on interactive elements
- ✅ Keyboard navigation support
- ✅ Focus visible styles
- ✅ Color contrast WCAG AA compliant
- ✅ Screen reader friendly

## SEO

- ✅ Meta description
- ✅ Open Graph tags
- ✅ Proper heading hierarchy
- ✅ Semantic HTML
- ✅ Fast loading times
- ✅ Mobile responsive

## Browser DevTools

### Inspecting Web Components

1. Open DevTools (F12)
2. Select the Elements tab
3. Look for custom elements like `<fuggs-nav>`
4. Expand to see rendered HTML

### Debugging Components

Components log initialization to console:
```
Fuggs Landing Page initialized
```

## Troubleshooting

### Components not rendering?

1. Check browser console for errors
2. Ensure you're running a local web server (not `file://` protocol)
3. Verify all component files are loaded
4. Check browser supports Custom Elements (should be all modern browsers)

### Styles not loading?

1. Verify Tailwind CDN is accessible
2. Check `css/styles.css` is loaded
3. Clear browser cache

### Images not showing?

1. Verify `assets/images/logo.svg` exists
2. Check file paths are correct
3. Ensure web server serves SVG files with correct MIME type

## Contributing

Improvements and suggestions are welcome!

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This landing page is part of the Fuggs project and is licensed under the MIT License.

See the main repository LICENSE file for details.

## Credits

- **Design System:** Inspired by Carbon Design System
- **Framework:** Custom Web Components (native browser APIs)
- **UI Library:** Tailwind CSS
- **Font:** IBM Plex Sans
- **Icons:** Heroicons (via Tailwind UI)

## Links

- **Main App:** http://localhost:8080 (development)
- **GitHub:** https://github.com/your-org/fuggs
- **Documentation:** https://github.com/your-org/fuggs/wiki
- **Issues:** https://github.com/your-org/fuggs/issues

---

**Built with ❤️ for the Fuggs community**
