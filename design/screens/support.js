/**
 * 에움길 (Byway) Local Runtime for .dc.html Screens
 * Enables full interactive rendering of <x-dc>, <sc-if>, <sc-for>, {{ bindings }}, SVG namespaces, and DCLogic state in any browser.
 */
(function () {
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const SVG_TAGS = new Set([
    'svg', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'ellipse',
    'g', 'defs', 'lineargradient', 'radialgradient', 'stop', 'filter',
    'fegaussianblur', 'femerge', 'femergenode', 'feoffset', 'feblend',
    'clippath', 'mask', 'pattern', 'text', 'tspan', 'use'
  ]);
  const SVG_CASE_MAP = {
    lineargradient: 'linearGradient',
    radialgradient: 'radialGradient',
    fegaussianblur: 'feGaussianBlur',
    femerge: 'feMerge',
    femergenode: 'feMergeNode',
    feoffset: 'feOffset',
    feblend: 'feBlend',
    clippath: 'clipPath',
     viewbox: 'viewBox',
    preserveaspectratio: 'preserveAspectRatio',
    gradientunits: 'gradientUnits',
    gradienttransform: 'gradientTransform',
    stddeviation: 'stdDeviation',
     filterunits: 'filterUnits'
  };

  window.DCLogic = class DCLogic {
    constructor(props) {
      this.props = props || {};
      this.state = {};
      this.__rerender = null;
    }
    setState(patch) {
      const next = typeof patch === 'function' ? patch(this.state) : patch;
      this.state = Object.assign({}, this.state, next);
      if (typeof this.__rerender === 'function') {
        this.__rerender();
      }
    }
    renderVals() {
      return {};
    }
  };

  function resolvePath(ctx, expr) {
    const clean = expr.trim();
    if (!clean) return undefined;
    if (clean === 'true') return true;
    if (clean === 'false') return false;
    const parts = clean.split('.');
    let cur = ctx;
    for (let i = 0; i < parts.length; i++) {
      if (cur == null) return undefined;
      cur = cur[parts[i]];
    }
    return cur;
  }

  function interpolateString(str, ctx) {
    return str.replace(/\{\{\s*([^}]+?)\s*\}\}/g, function (_, expr) {
      const val = resolvePath(ctx, expr);
      return val !== undefined && val !== null ? String(val) : '';
    });
  }

  function renderNode(templateNode, ctx, inSvg) {
    if (templateNode.nodeType === Node.TEXT_NODE) {
      return document.createTextNode(interpolateString(templateNode.nodeValue, ctx));
    }
    if (templateNode.nodeType !== Node.ELEMENT_NODE) {
      return templateNode.cloneNode(true);
    }

    const tag = templateNode.tagName.toLowerCase();
    const isSvg = inSvg || tag === 'svg' || templateNode.namespaceURI === SVG_NS || SVG_TAGS.has(tag);

    if (tag === 'sc-if') {
      const rawVal = templateNode.getAttribute('value') || '';
      const match = rawVal.match(/\{\{\s*([^}]+?)\s*\}\}/);
      let cond = match ? resolvePath(ctx, match[1]) : resolvePath(ctx, rawVal);
      if (cond === undefined) {
        const hint = templateNode.getAttribute('hint-placeholder-val') || '';
        cond = hint.includes('true');
      }
      const frag = document.createDocumentFragment();
      if (cond) {
        Array.from(templateNode.childNodes).forEach(function (child) {
          frag.appendChild(renderNode(child, ctx, isSvg));
        });
      }
      return frag;
    }

    if (tag === 'sc-for') {
      const rawList = templateNode.getAttribute('list') || '';
      const asName = templateNode.getAttribute('as') || 'item';
      const match = rawList.match(/\{\{\s*([^}]+?)\s*\}\}/);
      const list = (match ? resolvePath(ctx, match[1]) : resolvePath(ctx, rawList)) || [];
      const frag = document.createDocumentFragment();
      if (Array.isArray(list)) {
        list.forEach(function (item, idx) {
          const childCtx = Object.assign({}, ctx);
          childCtx[asName] = item;
          childCtx.$index = idx;
          Array.from(templateNode.childNodes).forEach(function (child) {
            frag.appendChild(renderNode(child, childCtx, isSvg));
          });
        });
      }
      return frag;
    }

    const el = isSvg
      ? document.createElementNS(SVG_NS, SVG_CASE_MAP[tag] || templateNode.localName || tag)
      : document.createElement(templateNode.tagName);

    Array.from(templateNode.attributes).forEach(function (attr) {
      const name = attr.name;
      const val = attr.value;
      if (name.toLowerCase() === 'onclick') {
        const match = val.match(/\{\{\s*([^}]+?)\s*\}\}/);
        if (match) {
          const fn = resolvePath(ctx, match[1]);
          if (typeof fn === 'function') {
            el.addEventListener('click', function (e) {
              e.preventDefault();
              fn(e);
            });
          }
        }
      } else {
        const attrName = isSvg && SVG_CASE_MAP[name.toLowerCase()] ? SVG_CASE_MAP[name.toLowerCase()] : name;
        el.setAttribute(attrName, interpolateString(val, ctx));
      }
    });

    Array.from(templateNode.childNodes).forEach(function (child) {
      el.appendChild(renderNode(child, ctx, isSvg));
    });
    return el;
  }

  document.addEventListener('DOMContentLoaded', function () {
    const xdc = document.querySelector('x-dc');
    if (!xdc) return;

    const helmet = xdc.querySelector('helmet');
    if (helmet) {
      Array.from(helmet.children).forEach(function (child) {
        document.head.appendChild(child.cloneNode(true));
      });
      helmet.remove();
    }

    const templateRoot = document.createElement('div');
    Array.from(xdc.childNodes).forEach(function (n) {
      templateRoot.appendChild(n.cloneNode(true));
    });

    let instance = null;
    const scriptEl = document.querySelector('script[type="text/x-dc"]');
    if (scriptEl) {
      try {
        const propsRaw = scriptEl.getAttribute('data-props');
        const props = propsRaw ? JSON.parse(propsRaw) : {};
        const code = scriptEl.textContent + '\n;return typeof Component !== "undefined" ? Component : null;';
        const CompClass = new Function(code)();
        if (CompClass) {
          instance = new CompClass(props);
        }
      } catch (err) {
        console.warn('DCLogic script evaluation warning:', err);
      }
    }

    function mount() {
      const vals = instance && typeof instance.renderVals === 'function' ? instance.renderVals() : {};
      xdc.innerHTML = '';
      Array.from(templateRoot.childNodes).forEach(function (child) {
        xdc.appendChild(renderNode(child, vals, false));
      });
    }

    if (instance) {
      instance.__rerender = mount;
    }
    mount();
  });
})();
