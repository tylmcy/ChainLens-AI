import { expect, test } from 'vitest';

test('Vitest 在浏览器 DOM 环境中运行', () => {
  const element = document.createElement('p');
  element.textContent = 'ChainLens';
  expect(element.textContent).toBe('ChainLens');
});
