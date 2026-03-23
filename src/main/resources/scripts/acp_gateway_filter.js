#!/usr/bin/env node
'use strict';
/**
 * ACP Gateway Auth Filter
 *
 * Prevents IntelliJ's gateway auth from overriding custom Anthropic-compatible
 * provider configurations (ANTHROPIC_BASE_URL, ANTHROPIC_AUTH_TOKEN env vars).
 *
 * The problem: IntelliJ 253+ always sends `auth._meta.gateway=true` capability.
 * claude-agent-acp responds by advertising a "gateway" auth method. IntelliJ
 * then auto-authenticates via gateway if JetBrains AI / BYOK is configured,
 * overriding ANTHROPIC_BASE_URL and ANTHROPIC_AUTH_TOKEN with its own values.
 * Only ANTHROPIC_DEFAULT_*_MODEL survives (hence "only the model arrives").
 *
 * The fix: Strip gateway auth from the initialize response so IntelliJ never
 * calls authenticate("gateway", ...), leaving our env vars intact.
 *
 * Usage: node acp_gateway_filter.js <command> [args...]
 * No external dependencies — built-in Node.js modules only.
 */

const { spawn } = require('child_process');
const readline = require('readline');

const args = process.argv.slice(2);
if (args.length === 0) {
    process.stderr.write('Usage: acp_gateway_filter.js <command> [args...]\n');
    process.exit(1);
}

const child = spawn(args[0], args.slice(1), {
    stdio: ['pipe', 'pipe', 'inherit'],
    env: process.env,
});

child.on('error', (err) => {
    process.stderr.write(`acp_gateway_filter: Error starting agent '${args[0]}': ${err.message}\n`);
    process.exit(1);
});

// Forward stdin to child
process.stdin.pipe(child.stdin);
process.stdin.on('end', () => child.stdin.end());

// Filter child stdout line by line
let done = false;

const rl = readline.createInterface({
    input: child.stdout,
    crlfDelay: Infinity,
});

rl.on('line', (line) => {
    let out = line;
    if (!done) {
        try {
            const msg = JSON.parse(line);
            const result = msg && msg.result;
            if (result && Array.isArray(result.authMethods)) {
                done = true;
                const before = result.authMethods.length;
                result.authMethods = result.authMethods.filter((m) => {
                    const gateway = m && m._meta && m._meta.gateway;
                    return gateway === null || gateway === undefined || typeof gateway !== 'object';
                });
                if (result.authMethods.length < before) {
                    out = JSON.stringify(msg);
                }
            }
        } catch (_) {}
    }
    process.stdout.write(out + '\n');
});

child.on('exit', (code) => {
    process.exit(code !== null ? code : 0);
});
