const fs = require('fs');
let c = fs.readFileSync('ainootbook-vue3/patches/@jeecg__aiflow.patch', 'utf8');
c = c.replace(/"b\/D:\/[^"]+"/g, 'b/WorkflowView.vue_vue_type_style_index_0_lang-Bo5xJraI.mjs');
fs.writeFileSync('ainootbook-vue3/patches/@jeecg__aiflow.patch', c);