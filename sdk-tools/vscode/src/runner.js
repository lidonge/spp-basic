'use strict';

const { spawn } = require('node:child_process');
const { StringDecoder } = require('node:string_decoder');

function diagnosticWriter(values, write) {
  const secrets = [...new Set(values.filter(value => typeof value === 'string' && value.length))].sort((a,b) => b.length-a.length);
  const decoder = new StringDecoder('utf8');
  let pending = '';
  return {
    append(chunk) {
      pending += decoder.write(chunk);
      for (const secret of secrets) pending = pending.split(secret).join('[redacted]');
      let retained = 0;
      for (const secret of secrets) for (let length = Math.min(secret.length - 1, pending.length); length > retained; length--) {
        if (pending.endsWith(secret.slice(0, length))) { retained = length; break; }
      }
      const ready = pending.slice(0, pending.length - retained);
      pending = pending.slice(pending.length - retained);
      if (ready) write?.(ready);
    },
    end() {
      pending += decoder.end();
      if (pending) write?.(secrets.length ? '[redacted]' : pending);
      pending = '';
    }
  };
}

function runCli(executable, jar, argumentsList, token, options = {}) {
  if (options.cancellation?.isCancellationRequested) return Promise.reject(new Error('S++ operation cancelled.'));
  const spawnProcess = options.spawn || spawn;
  return new Promise((resolve, reject) => {
    const child = spawnProcess(executable, ['-jar', jar, ...argumentsList, ...(options.unauthenticated ? [] : ['--token-stdin'])], {
      cwd: options.cwd,
      shell: false,
      windowsHide: true,
      stdio: ['pipe', 'pipe', 'pipe']
    });
    let stdout = '';
    const decoder = new StringDecoder('utf8');
    const input = typeof token === 'string' ? { accessToken: token } : token;
    const diagnostics = diagnosticWriter([input?.accessToken, input?.refreshToken, input?.deviceCode], options.onDiagnostic);
    let completed = false;
    let cancellationSubscription;
    const timer = setTimeout(() => {
      finish(new Error('S++ operation timed out.'));
      child.kill();
    }, options.timeout || 135000);
    cancellationSubscription = options.cancellation?.onCancellationRequested(() => {
      finish(new Error('S++ operation cancelled.'));
      child.kill();
    });
    if (completed) {
      cancellationSubscription?.dispose();
      return;
    }
    function finish(error, result) {
      if (completed) return;
      completed = true;
      clearTimeout(timer);
      cancellationSubscription?.dispose();
      diagnostics.end();
      if (error) reject(error);
      else resolve(result);
    }
    child.once('error', () => finish(new Error('Cannot start the S++ CLI. Check Java and the SDK JAR.')));
    child.stdin.on('error', () => {});
    child.stdout.on('data', chunk => {
      if (completed) return;
      stdout += decoder.write(chunk);
      if (Buffer.byteLength(stdout) > 1024 * 1024) {
        child.kill();
        finish(new Error('S++ CLI response exceeded the size limit.'));
      }
    });
    child.stderr.on('data', chunk => {
      if (!completed) diagnostics.append(chunk);
    });
    child.once('close', code => {
      if (completed) return;
      try {
        stdout += decoder.end();
        const response = JSON.parse(stdout);
        if (code !== 0 || response.ok !== true) {
          const allowed = new Set(['SCENARIO_LIMIT_EXCEEDED', 'TECHNICAL_SERVICE_LIMIT_EXCEEDED', 'DEPLOYMENT_NOT_INCLUDED', 'DEPLOYMENT_TARGET_UNRESOLVED', 'MEMBERSHIP_PROTOCOL_REQUIRED', 'SDK_RELEASE_UNAVAILABLE', 'AUTH_NOT_CONFIGURED', 'AUTH_DENIED', 'AUTH_UNAVAILABLE', 'INVALID_LEASE',
            'AUTH_PROTOCOL', 'AUTH_LOGIN_EXPIRED', 'AUTH_CACHE', 'AUTH_CLOCK', 'CREDENTIAL_STORAGE', 'CANCELLED', 'LOGIN_REQUIRED', 'FEATURE_DENIED', 'OUTPUT_EXISTS', 'INVALID_SOURCE', 'INVALID_ARGUMENT', 'COMPILATION_FAILED', 'COMPILATION_TIMEOUT', 'OUTPUT_BUSY', 'ATOMIC_OUTPUT_UNSUPPORTED']);
          const errorCode = allowed.has(response.error?.code) ? response.error.code : 'CLI_FAILED';
          const guidance = {
            SCENARIO_LIMIT_EXCEEDED: 'This project exceeds the scenario limit in your current signed authorization.',
            TECHNICAL_SERVICE_LIMIT_EXCEEDED: 'Review the APP technical service registrations; repeated hook references count once.',
            DEPLOYMENT_NOT_INCLUDED: 'Use a local application target or an authorization that includes distributed deployment. Remote databases are allowed locally.',
            DEPLOYMENT_TARGET_UNRESOLVED: 'Resolve the selected PHY application-domain endpoint before converting.',
            MEMBERSHIP_PROTOCOL_REQUIRED: 'Use the current SDK and authorize a personal evaluation installation; legacy proofs cannot grant membership capabilities.',
            SDK_RELEASE_UNAVAILABLE: 'Install a supported SDK edition from the release catalogue; signing in again does not fix a withdrawn edition.',
            AUTH_NOT_CONFIGURED: 'This development SDK has no production account service configured.',
            AUTH_DENIED: 'Sign in again or check your subscription and assigned seat.',
            AUTH_UNAVAILABLE: 'Cannot verify authorization online; no conversion was started.',
            OUTPUT_EXISTS: 'Choose a new output directory; existing output is not overwritten.',
            INVALID_SOURCE: 'Choose a readable S++ source directory containing .spp files and no symbolic links.'
          };
          const failure = new Error(`S++ ${errorCode}. ${guidance[errorCode] || 'See the S++ output channel for local diagnostics.'}`);
          failure.code = errorCode;
          finish(failure);
        } else finish(null, response.result);
      } catch {
        finish(new Error('Invalid S++ CLI response. Check the SDK version.'));
      }
    });
    child.stdin.end(options.unauthenticated ? '' : JSON.stringify(input));
  });
}

module.exports = { runCli };
