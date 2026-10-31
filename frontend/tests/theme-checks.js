import { expect } from '@playwright/test';

// Measure the rendered colors, including transparent ancestors, instead of matching a stylesheet.
export async function expectReadable(locator, minimum = 4.5, property = 'color') {
  const sample = await locator.evaluate((element, property) => {
    const rgba = text => { const values = text.match(/[\d.]+/g).map(Number); return [...values.slice(0, 3).map(v => text.startsWith('color(srgb ') ? v * 255 : v), values[3] ?? 1]; };
    const over = (top, bottom) => { const a = top[3] + bottom[3] * (1 - top[3]); return [...top.slice(0, 3).map((v, i) => (v * top[3] + bottom[i] * bottom[3] * (1 - top[3])) / a), a]; };
    const layers = [];
    for (let current = element; current; current = current.parentElement) {
      const style = getComputedStyle(current);
      if (Number(style.opacity) !== 1) throw new Error('Contrast sample needs full opacity.');
      if (style.backgroundImage !== 'none') throw new Error('Choose a flat surface for the contrast measurement.');
      const bg = rgba(style.backgroundColor); layers.push(bg); if (bg[3] === 1) break;
    }
    const background = layers.reverse().reduce((bottom, top) => over(top, bottom), [255, 255, 255, 1]);
    const foreground = over(rgba(getComputedStyle(element)[property]), background);
    const luminance = rgb => rgb.slice(0, 3).map(v => v / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4).reduce((sum, v, i) => sum + v * [.2126, .7152, .0722][i], 0);
    const levels = [luminance(background), luminance(foreground)].sort((a, b) => a - b);
    return { ratio: (levels[1] + .05) / (levels[0] + .05), foreground, background };
  }, property);
  expect(sample.ratio, JSON.stringify(sample)).toBeGreaterThanOrEqual(minimum);
  return sample.ratio;
}

export async function expectPageFits(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
}
