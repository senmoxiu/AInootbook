const fs = require('fs');
let text = fs.readFileSync('D:/下载/论文相关/AIbook/ainootbook-uniapp/pages.config.ts', 'utf8');

if (text.charCodeAt(0) === 0xFEFF) {
  text = text.slice(1);
}

text = text.replace(
  /pagePath:\s*'pages-study\/course\/list',\s*text:\s*'.*?'/s,
  "pagePath: 'pages-study/course/list',\n        text: '\u5B66\u4E60'"
);

text = text.replace(
  /text:\s*'.*?',\s*\}\s*,\s*\{\s*iconPath:\s*'static\/tabbar\/tabbar-home-2\.png'/s,
  "text: '\u5B66\u4E60',\n      },\n      {\n        iconPath: 'static/tabbar/tabbar-home-2.png'"
);

fs.writeFileSync('D:/下载/论文相关/AIbook/ainootbook-uniapp/pages.config.ts', text, 'utf8');
console.log("Success");
