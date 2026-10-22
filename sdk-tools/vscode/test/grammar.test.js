'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const textmate = require('vscode-textmate');
const oniguruma = require('vscode-oniguruma');

const root = path.resolve(__dirname, '..');
const registry = new textmate.Registry({
  onigLib: oniguruma.loadWASM(fs.readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm'))).then(() => ({
    createOnigScanner: patterns => new oniguruma.OnigScanner(patterns),
    createOnigString: value => new oniguruma.OnigString(value)
  })),
  loadGrammar: async scope => scope === 'source.spp'
    ? JSON.parse(fs.readFileSync(path.join(root, 'syntaxes/spp.tmLanguage.json'), 'utf8')) : null
});

async function tokenize(source) {
  const grammar = await registry.loadGrammar('source.spp');
  let stack = textmate.INITIAL;
  return source.split('\n').map(line => {
    const result = grammar.tokenizeLine(line, stack);
    stack = result.ruleStack;
    return result.tokens.map(token => ({ text: line.slice(token.startIndex, token.endIndex), scopes: token.scopes }));
  });
}

function assertScope(tokens, fragment, scope) {
  const match = tokens.find(token => token.text === fragment);
  assert.ok(match, `Missing token ${fragment}: ${JSON.stringify(tokens)}`);
  assert.ok(match.scopes.includes(scope), `${fragment}: ${match.scopes.join(', ')}`);
}

test('TextMate recognizes SPP declarations and Chinese names', async () => {
  const lines = await tokenize('domain ledger;\nRole 账户 {\n int amount;\n}\natomic calculate twice(Facts) {}\nScenario serial preview(Facts) {}\nEnum State { Pending }');
  assertScope(lines[0], 'ledger', 'entity.name.namespace.spp');
  assertScope(lines[1], '账户', 'entity.name.type.spp');
  assertScope(lines[2], 'int', 'storage.type.primitive.spp');
  assertScope(lines[4], 'twice', 'entity.name.function.spp');
  assertScope(lines[5], 'preview', 'entity.name.function.spp');
  assertScope(lines[6], 'State', 'entity.name.type.spp');
});

test('comments and strings shield fake declarations and support multiline comments', async () => {
  const lines = await tokenize('// Role Fake {}\n/* Scenario hidden() {\n atomic calculate nope(); */ Role Visible;\nString message = "Role Fake \\" text";');
  assert.ok(lines[0].every(token => token.scopes.includes('comment.line.double-slash.spp')));
  assert.ok(lines[1].every(token => token.scopes.includes('comment.block.spp')));
  assertScope(lines[2], 'Visible', 'entity.name.type.spp');
  assert.ok(lines[3].some(token => token.scopes.includes('constant.character.escape.spp')));
  assert.ok(!lines[3].some(token => token.scopes.includes('entity.name.type.spp')));
});

test('numbers, character escapes, booleans and annotations have distinct scopes', async () => {
  const [tokens] = await tokenize('@Label decimal(18,2) amount = 0xFF + 0b10 + 1_000L + .25e-2; true null \'\\n\'');
  for (const number of ['18', '2', '0xFF', '0b10', '1_000L', '.25e-2'])
    assertScope(tokens, number, 'constant.numeric.spp');
  assertScope(tokens, 'Label', 'storage.type.annotation.spp');
  assertScope(tokens, 'true', 'constant.language.spp');
  assertScope(tokens, '\\n', 'constant.character.escape.spp');
});

test('APP RDB PHY configuration and contextual identifiers are distinguished', async () => {
  const lines = await tokenize('scope { local { serviceName; } }\ntables Facts { table records(Facts) {} }\nphy { environment develop { backend jdbc; } }\nRole key { String fail; }\ntechnical services { audit; }\n} fail { recover; }');
  assertScope(lines[0], 'scope', 'keyword.other.configuration.spp');
  assertScope(lines[1], 'tables', 'keyword.other.configuration.spp');
  assertScope(lines[2], 'environment', 'keyword.other.configuration.spp');
  assertScope(lines[3], 'key', 'entity.name.type.spp');
  assert.ok(!lines[3].some(token => token.scopes.includes('keyword.other.context.spp')));
  assertScope(lines[4], 'technical', 'keyword.other.context.spp');
  assertScope(lines[5], 'fail', 'keyword.other.context.spp');
  const [query] = await tokenize('atomic query lookup(key Facts);');
  assertScope(query, 'key', 'keyword.other.context.spp');
});

test('base editing resources are declared, safe without trust, and do not change CLI trust gates', () => {
  const manifest = require('../package.json');
  assert.deepEqual(manifest.contributes.languages[0].extensions, ['.spp', '.enum', '.app', '.phy', '.rdb']);
  assert.equal(manifest.capabilities.untrustedWorkspaces.supported, 'limited');
  assert.equal(manifest.contributes.grammars[0].scopeName, 'source.spp');
  assert.ok(manifest.activationEvents.includes('onLanguage:spp'));
  const configuration = require('../language-configuration.json');
  assert.equal(configuration.comments.lineComment, '//');
  assert.ok(configuration.autoClosingPairs.every(pair => pair.notIn.includes('comment')));
  const snippets = require('../snippets/spp.json');
  for (const [name, snippet] of Object.entries(snippets)) {
    assert.ok(snippet.prefix.startsWith('spp-'), name);
    assert.ok(Array.isArray(snippet.body), name);
  }
});
