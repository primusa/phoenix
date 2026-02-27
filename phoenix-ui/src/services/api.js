import axios from 'axios';

const isLocal = window.location.hostname === 'localhost';

export const API_BASE = isLocal
    ? 'http://localhost:8080/api'
    : `${window.location.protocol}//${window.location.host}/api`;

export const GRAFANA_URL = isLocal
    ? 'http://localhost:3000'
    : `${window.location.protocol}//${window.location.host}/grafana`;

const JAEGER_URL = isLocal
    ? 'http://localhost:16686/jaeger'
    : `${window.location.protocol}//${window.location.host}/jaeger`;

export const PUBLIC_JAEGER_URL = JAEGER_URL;

const api = axios.create({
    baseURL: API_BASE,
});

export const claimsApi = {
    getClaims: () => api.get('/claims'),
    createClaim: (data) => api.post('/claims', data),
    getAiConfig: () => api.get('/config/ai-provider'),
    setAiConfig: (data) => api.post('/config/ai-provider', data),
    getDashboards: () => api.get('/monitoring/dashboards'),
};

export default api;
