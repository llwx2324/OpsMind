// Minimal OIDC Authorization Code + PKCE client for the static OpsMind UI.
// All provider-specific values are supplied by /api/public/auth-config.
window.OpsMindAuth = (() => {
    const storage = window.sessionStorage;
    const configUrl = '/api/public/auth-config';

    const base64url = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes)))
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const random = () => base64url(crypto.getRandomValues(new Uint8Array(32)));
    const sha256 = value => crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)).then(base64url);

    async function config() {
        const response = await fetch(configUrl, { credentials: 'same-origin' });
        if (!response.ok) throw new Error('无法加载认证配置');
        return response.json();
    }

    async function discovery(issuer) {
        const base = issuer.replace(/\/$/, '');
        const response = await fetch(`${base}/.well-known/openid-configuration`);
        if (!response.ok) throw new Error('无法加载 OIDC Discovery 文档');
        return response.json();
    }

    async function exchange(code, verifier, metadata, oidc) {
        const body = new URLSearchParams({
            grant_type: 'authorization_code', code, client_id: oidc.clientId,
            redirect_uri: window.location.origin + window.location.pathname,
            code_verifier: verifier
        });
        const response = await fetch(metadata.token_endpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
        if (!response.ok) throw new Error('OIDC 令牌交换失败');
        const token = await response.json();
        storage.setItem('opsmind.access_token', token.access_token);
        storage.setItem('opsmind.expires_at', String(Date.now() + Math.max(0, (token.expires_in || 300) - 30) * 1000));
    }

    async function login(oidc) {
        const metadata = await discovery(oidc.issuer);
        const verifier = random();
        storage.setItem('opsmind.pkce_verifier', verifier);
        storage.setItem('opsmind.oidc_state', random());
        const authorize = new URL(metadata.authorization_endpoint);
        authorize.searchParams.set('response_type', 'code');
        authorize.searchParams.set('client_id', oidc.clientId);
        authorize.searchParams.set('redirect_uri', window.location.origin + window.location.pathname);
        authorize.searchParams.set('scope', oidc.scopes || 'openid profile');
        authorize.searchParams.set('state', storage.getItem('opsmind.oidc_state'));
        authorize.searchParams.set('code_challenge', await sha256(verifier));
        authorize.searchParams.set('code_challenge_method', 'S256');
        if (oidc.audience) authorize.searchParams.set('audience', oidc.audience);
        window.location.assign(authorize.toString());
    }

    async function initialize() {
        const oidc = await config();
        if (!oidc.enabled) return;
        if (!oidc.issuer || !oidc.clientId || !oidc.audience) throw new Error('OIDC 已启用但 issuer、clientId 或 audience 未配置');
        const params = new URLSearchParams(window.location.search);
        if (params.has('error')) throw new Error(params.get('error_description') || 'OIDC 登录失败');
        if (params.has('code')) {
            if (params.get('state') !== storage.getItem('opsmind.oidc_state')) throw new Error('OIDC state 校验失败');
            await exchange(params.get('code'), storage.getItem('opsmind.pkce_verifier'), await discovery(oidc.issuer), oidc);
            window.history.replaceState({}, document.title, window.location.pathname);
        }
        if (!storage.getItem('opsmind.access_token') || Number(storage.getItem('opsmind.expires_at') || 0) < Date.now()) await login(oidc);
    }

    async function fetchWithAuth(input, init = {}) {
        const token = storage.getItem('opsmind.access_token');
        const headers = new Headers(init.headers || {});
        if (token) headers.set('Authorization', `Bearer ${token}`);
        return fetch(input, { ...init, headers });
    }

    return { ready: initialize(), fetch: fetchWithAuth };
})();
