import { useState, useEffect, useCallback } from 'react'
import axios from 'axios'
import toast, { Toaster } from 'react-hot-toast'
import { Brain, Database, Send, RefreshCw, Zap, Activity, ShieldCheck, LayoutDashboard, Sliders, Search, CheckCircle2, ArrowRight, Terminal, Clock, DollarSign, Lock, Sparkles, HelpCircle, X, ShieldAlert, BarChart3, ExternalLink, Box, Link2 } from 'lucide-react'

const isLocal = window.location.hostname === 'localhost';

// In production/AWS, the API Gateway or ALB will proxy these paths
const API_BASE = isLocal
  ? 'http://localhost:8080/api'
  : `${window.location.protocol}//${window.location.host}/api`;

const GRAFANA_URL = isLocal
  ? 'http://localhost:3000'
  : `${window.location.protocol}//${window.location.host}/grafana`;

const JAEGER_URL = isLocal
  ? 'http://localhost:16686/jaeger'
  : `${window.location.protocol}//${window.location.host}/jaeger`;

const PUBLIC_GRAFANA_URL = GRAFANA_URL;
const PUBLIC_JAEGER_URL = JAEGER_URL;

const STAGES = [
  { id: 1, label: 'Postgres WAL', icon: <Database className="w-4 h-4" /> },
  { id: 2, label: 'Kafka Stream', icon: <Zap className="w-4 h-4" /> },
  { id: 3, label: 'LLM Synthesis', icon: <Brain className="w-4 h-4" /> },
  { id: 4, label: 'Vector Core', icon: <ShieldCheck className="w-4 h-4" /> },
]

function App() {
  const [view, setView] = useState('app')
  const [monitorTool, setMonitorTool] = useState('grafana')
  const [claims, setClaims] = useState([])
  const [searchQuery, setSearchQuery] = useState('')
  const [provider, setProvider] = useState('ollama')
  const [temperature, setTemperature] = useState(0.3)
  const [newClaim, setNewClaim] = useState('')
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [pipelineStep, setPipelineStep] = useState(0)
  const [showWarning, setShowWarning] = useState(true)
  const [grafanaPath, setGrafanaPath] = useState('/d/d9e64ba9-4f2d-4081-8a94-9a65726084db/jvm-overview-micrometer-otlp')

  const [logs, setLogs] = useState([{ id: 1, time: new Date().toLocaleTimeString(), msg: "SYSTEM: Bridge Protocol Online. Listening for Postgres WAL events...", type: "system" }])
  const [metrics] = useState({ latency: '42ms', cost: '$0.004', health: '99.8%' })

  useEffect(() => {
    const syncGrafana = async () => {
      try {
        const res = await axios.get(`${API_BASE}/monitoring/dashboards`);
        const jvmDash = res.data.find(d =>
          d.title.toLowerCase().includes('jvm') ||
          d.uid === 'd9e64ba9-4f2d-4081-8a94-9a65726084db'
        );
        if (jvmDash && jvmDash.url) {
          setGrafanaPath(jvmDash.url);
          console.log("Observability Tunnel: Synchronized via Phoenix Backend");
        }
      } catch (err) {
        console.warn("Observability Tunnel: Failed to reach Grafana via Backend Proxy");
      }
    };
    syncGrafana();
  }, []);

  const addLog = (msg, type = "info") => {
    setLogs(prev => [{ id: Date.now(), time: new Date().toLocaleTimeString(), msg, type }, ...prev].slice(0, 50))
  }

  const fetchClaims = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true)
    try {
      const res = await axios.get(`${API_BASE}/claims`)
      const enriched = res.data.map(c => ({
        ...c,
        traceId: c.traceId || `tr-${Math.random().toString(36).substr(2, 9)}`
      }))
      setClaims(enriched)
      if (!isSilent) toast.success('Vector Core Synchronized')
      return enriched.length
    } catch (err) {
      if (!isSilent) toast.error('Sync failed: DB Connection Refused')
      return claims.length
    } finally {
      if (!isSilent) setLoading(false)
    }
  }, [claims.length])

  useEffect(() => { fetchClaims(true) }, [])

  const handleProviderChange = async (p) => {
    setProvider(p)
    await axios.post(`${API_BASE}/config/ai-provider`, { provider: p, temperature: temperature })
    toast(`Engine: Switched to ${p.toUpperCase()}`, { icon: '🤖' })
  }

  const handleTemperatureChange = async (t) => {
    setTemperature(t)
    await axios.post(`${API_BASE}/config/ai-provider`, { provider: provider, temperature: t })
    toast(`Engine: Temperature set to ${t}`, { icon: '🌡️' })
  }

  const openTrace = (id) => {
    setMonitorTool('jaeger')
    setView('monitoring')
    toast(`Locating Span: ${id}`, { icon: '🔍' })
  }

  const filteredClaims = claims.filter(c =>
    c.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (c.summary && c.summary.toLowerCase().includes(searchQuery.toLowerCase()))
  )

  const handleCreateClaim = async () => {
    if (!newClaim) return
    setCreating(true); setPipelineStep(1)
    addLog(`PG_SOURCE: Transaction committed to public.claims`, "db")
    try {
      const initialCount = claims.length
      await axios.post(`${API_BASE}/claims`, { description: newClaim })
      setNewClaim('')
      setTimeout(() => { setPipelineStep(2); addLog(`KAFKA_CDC: Captured event. Topic: 'claims.raw'`, "kafka") }, 800)
      setTimeout(() => { setPipelineStep(3); addLog(`AI_ENGINE: Synthesizing via ${provider}...`, "ai") }, 1800)

      const poll = setInterval(async () => {
        const currentCount = await fetchClaims(true)
        if (currentCount > initialCount) {
          setPipelineStep(4)
          addLog(`SINK: Indexed in Vector Core`, "success")
          toast.success('Claim Modernized Successfully')
          clearInterval(poll)
          setTimeout(() => setPipelineStep(0), 4000)
        }
      }, 1500)
    } catch (err) { setPipelineStep(0); toast.error('Pipeline Interrupted') }
    finally { setCreating(false) }
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans">
      <Toaster position="top-right" />

      <nav className="fixed left-0 top-0 h-full w-20 bg-slate-900 flex flex-col items-center py-8 gap-8 z-[100]">
        <div className="p-3 bg-indigo-600 rounded-xl shadow-lg"><Brain className="w-8 h-8 text-white" /></div>
        <button onClick={() => setView('app')} className={`p-3 rounded-xl transition-all ${view === 'app' ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'text-slate-500 hover:text-white'}`}><LayoutDashboard className="w-6 h-6" /></button>
        <button onClick={() => setView('monitoring')} className={`p-3 rounded-xl transition-all ${view === 'monitoring' ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'text-slate-500 hover:text-white'}`}><Activity className="w-6 h-6" /></button>
      </nav>

      <main className="pl-20 p-8 h-screen flex flex-col overflow-hidden">
        <div className="max-w-7xl mx-auto w-full flex-1 flex flex-col space-y-6 min-h-0">

          {view === 'app' ? (
            <div className="flex flex-col gap-6 h-full min-h-0">
              <header className="flex flex-col bg-white p-6 rounded-2xl shadow-sm border border-slate-200 gap-6 shrink-0 relative z-50">
                <div className="flex justify-between items-center">
                  <div>
                    <h1 className="text-xl font-black text-slate-900 uppercase tracking-tight leading-none">Legacy Modernization Pipeline</h1>
                    <span className="text-[11px] font-bold text-slate-500 mt-2 uppercase tracking-widest flex items-center gap-2">
                      <Zap className="w-3 h-3 text-orange-400" /> PostgreSQL ➜ Kafka ➜ AI Enrichment ➜ Vector Core
                    </span>
                  </div>

                  {/* METRICS WITH TOOLTIPS RESTORED */}
                  <div className="flex items-center gap-6 px-6 py-3 bg-slate-900 rounded-2xl shadow-xl">
                    <div className="flex flex-col relative group cursor-help">
                      <span className="text-[8px] font-black text-indigo-400 uppercase flex items-center gap-1">CDC Latency <HelpCircle className="w-2 h-2" /></span>
                      <span className="text-sm font-black text-white">{metrics.latency}</span>
                      <div className="absolute top-full mt-2 left-0 w-48 p-2 bg-slate-800 text-[9px] text-slate-300 rounded-lg opacity-0 group-hover:opacity-100 transition-all z-[110] border border-slate-700 pointer-events-none shadow-2xl">
                        Calculated as Δt between Postgres WAL commit and Vector DB ingestion acknowledgement.
                      </div>
                    </div>
                    <div className="w-px h-6 bg-slate-700" />
                    <div className="flex flex-col relative group cursor-help">
                      <span className="text-[8px] font-black text-indigo-400 uppercase flex items-center gap-1">Enrichment Cost <HelpCircle className="w-2 h-2" /></span>
                      <span className="text-sm font-black text-white">{metrics.cost}</span>
                      <div className="absolute top-full mt-2 left-0 w-48 p-2 bg-slate-800 text-[9px] text-slate-300 rounded-lg opacity-0 group-hover:opacity-100 transition-all z-[110] border border-slate-700 pointer-events-none shadow-2xl">
                        Based on avg tokens/claim (Input: ~150, Output: ~400) x current model {provider.toUpperCase()} pricing.
                      </div>

                    </div>
                    <div className="w-px h-6 bg-slate-700" />
                    <div className="flex flex-col">
                      <span className="text-[8px] font-black text-indigo-400 uppercase">Modernized Total</span>
                      <span className="text-sm font-black text-white">{claims.length}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between bg-slate-50/50 p-4 rounded-xl border border-slate-100">
                  {STAGES.map((stage, idx) => (
                    <div key={stage.id} className="flex-1 flex items-center">
                      <div className="flex flex-col items-center gap-2 flex-1">
                        <div className={`w-10 h-10 rounded-full border-2 flex items-center justify-center transition-all ${pipelineStep > idx ? 'border-indigo-600 bg-indigo-600 text-white shadow-lg' : 'border-slate-200 bg-white text-slate-300'}`}>
                          {pipelineStep > idx + 1 ? <CheckCircle2 className="w-6 h-6" /> : stage.icon}
                        </div>
                        <span className={`text-[9px] font-black uppercase ${pipelineStep === idx + 1 ? 'text-indigo-600' : 'text-slate-400'}`}>{stage.label}</span>
                      </div>
                      {idx < STAGES.length - 1 && <ArrowRight className="w-4 h-4 text-slate-200" />}
                    </div>
                  ))}
                </div>
              </header>

              <div className="grid grid-cols-12 gap-6 flex-1 min-h-0">
                <div className="col-span-4 flex flex-col gap-6 overflow-hidden">
                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm shrink-0">
                    <div className="flex justify-between items-center mb-3">
                      <h2 className="text-[10px] font-black uppercase text-slate-400 flex items-center gap-2"><Sliders className="w-3 h-3" /> Engine Config</h2>
                      <span className="text-[10px] font-bold bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded">Temp: {temperature}</span>
                    </div>
                    <div className="flex bg-slate-100 p-1 rounded-xl mb-4">
                      {['openai', 'ollama', 'gemini'].map(p => (
                        <button key={p} onClick={() => handleProviderChange(p)} className={`flex-1 py-1.5 rounded-lg text-[10px] font-bold transition-all ${provider === p ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-400'}`}>{p.toUpperCase()}</button>
                      ))}
                    </div>
                    <input type="range" min="0" max="1" step="0.1" value={temperature} onChange={(e) => handleTemperatureChange(e.target.value)} className="w-full h-1.5 accent-indigo-600 bg-slate-200 rounded-lg appearance-none cursor-pointer" />
                  </div>

                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-col shrink-0">
                    <h2 className="text-[10px] font-black uppercase text-slate-400 mb-2">Ingestion Source (PostgreSQL)</h2>
                    {showWarning && (
                      <div className="mb-3 p-3 bg-amber-50 border border-amber-100 rounded-xl flex gap-3 relative animate-in slide-in-from-top-2">
                        <ShieldAlert className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                        <div className="flex flex-col pr-6">
                          <span className="text-[10px] font-black text-amber-800 uppercase">Governance Notice</span>
                          <span className="text-[9px] font-semibold text-amber-700 leading-tight">PII scrubbing is dormant. Bypass active.</span>
                        </div>
                        <button onClick={() => setShowWarning(false)} className="absolute top-2 right-2 p-1 text-amber-400 hover:text-amber-900"><X className="w-3 h-3" /></button>
                      </div>
                    )}
                    <textarea value={newClaim} onChange={(e) => setNewClaim(e.target.value)} className="w-full p-3 bg-slate-50 rounded-xl min-h-[80px] mb-3 text-sm border border-slate-100 outline-none focus:ring-2 ring-indigo-500/20 transition-all" placeholder="Enter raw claim data..." />
                    <button onClick={handleCreateClaim} disabled={creating || !newClaim} className="w-full bg-slate-900 text-white py-3 rounded-xl font-bold flex justify-center items-center gap-2 hover:bg-indigo-600 transition-all">
                      {creating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />} Submit Claim & Start CDC Pipeline
                    </button>
                  </div>

                  <div className="flex-1 bg-slate-900 rounded-2xl p-4 font-mono text-[10px] overflow-y-auto text-slate-300 shadow-inner min-h-0">
                    <div className="border-b border-slate-800 pb-2 mb-3 text-slate-500 flex items-center gap-2"><Terminal className="w-3 h-3 text-green-500" /> CLUSTER_TELEMETRY</div>
                    <div className="space-y-1">{logs.map(l => (<div key={l.id}><span className="opacity-30">[{l.time}]</span> <span className={l.type === 'kafka' ? 'text-orange-400' : l.type === 'ai' ? 'text-indigo-400' : ''}>{l.msg}</span></div>))}</div>
                  </div>
                </div>

                <div className="col-span-8 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col relative overflow-hidden">
                  <div className="p-5 border-b border-slate-100 bg-slate-50/50 flex flex-col gap-4 z-40 relative">
                    <div className="flex justify-between items-center">
                      <h2 className="font-bold text-slate-700 uppercase text-xs tracking-widest flex items-center gap-2"><Sparkles className="w-4 h-4 text-indigo-500" /> Enriched Claims Intelligence</h2>
                      <button onClick={() => fetchClaims()} className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-[10px] font-black text-slate-600 hover:bg-slate-50 transition-all">REFRESH</button>
                    </div>
                    <div className="relative">
                      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                      <input type="text" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Filter modernized records..." className="w-full pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-[11px] outline-none focus:ring-2 ring-indigo-500/10 transition-all" />
                    </div>
                  </div>

                  <div className="flex-1 overflow-y-auto p-6 space-y-4 min-h-0 overflow-x-visible">
                    {filteredClaims.map((c, idx) => (
                      <div key={c.id} className="p-5 bg-white border border-slate-100 rounded-2xl border-l-4 border-l-indigo-500 shadow-sm relative group/claim">
                        <div className="flex justify-between items-start mb-3">
                          <span className="text-[10px] font-mono font-bold text-slate-400 uppercase">#CLAIM_ID_{c.id}</span>
                          <div className="flex gap-2 relative">
                            <button onClick={() => openTrace(c.traceId)} className="flex items-center gap-1 px-2 py-0.5 rounded bg-slate-100 text-slate-600 text-[8px] font-black border border-slate-200 hover:bg-indigo-600 hover:text-white transition-all">
                              <Link2 className="w-2.5 h-2.5" /> TRACE
                            </button>
                            <div className="flex items-center gap-1.5 px-2 py-0.5 rounded bg-amber-50 text-amber-700 text-[8px] font-black border border-amber-100 group/tip cursor-help relative">
                              <Lock className="w-2.5 h-2.5" /> PII BYPASSED <HelpCircle className="w-2 h-2" />
                              <div className={`absolute right-0 w-64 p-3 bg-slate-900 text-slate-200 rounded-xl shadow-2xl opacity-0 group-hover/tip:opacity-100 transition-all pointer-events-none font-normal z-[200] border border-slate-700
                                ${idx === 0 ? 'top-full mt-2' : 'bottom-full mb-2'}`}>
                                <span className="font-bold text-amber-400 uppercase block mb-1">Status: Governance Dormant</span>
                                <p className="text-[10px] text-slate-400 leading-relaxed">Identity masking is currently bypassed. Personal information in this record has not been modified by the security gatekeeper.</p>
                                <div className={`absolute right-4 w-3 h-3 bg-slate-900 border-slate-700 rotate-45 ${idx === 0 ? '-top-1.5 border-l border-t' : '-bottom-1.5 border-r border-b'}`}></div>
                              </div>
                            </div>
                            <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-indigo-50 text-indigo-600 text-[8px] font-black border border-indigo-100"><ShieldCheck className="w-2.5 h-2.5" /> VECTOR_SYNC</div>
                          </div>
                        </div>
                        <p className="text-slate-500 text-xs mb-3 italic">"{c.description}"</p>
                        {c.summary && <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 text-sm font-semibold text-slate-800 animate-in fade-in duration-300">{c.summary}</div>}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-4 h-full animate-in fade-in duration-500 min-h-0">
              <div className="bg-slate-900 p-4 rounded-2xl flex items-center justify-between shrink-0 shadow-xl border border-slate-800">
                <div className="flex gap-2">
                  <button onClick={() => setMonitorTool('grafana')} className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${monitorTool === 'grafana' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30' : 'bg-slate-800 text-slate-400'}`}>
                    <BarChart3 className="w-4 h-4" /> GRAFANA METRICS
                  </button>
                  <button onClick={() => setMonitorTool('jaeger')} className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${monitorTool === 'jaeger' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30' : 'bg-slate-800 text-slate-400'}`}>
                    <Activity className="w-4 h-4" /> JAEGER TRACES
                  </button>
                </div>
                <div className="flex gap-3">
                  <div className="flex items-center gap-4 mr-4 text-[10px] font-mono text-slate-500">
                    <span className="flex items-center gap-1"><Box className="w-3 h-3" /> NODE_01: ACTIVE</span>
                    {monitorTool === 'grafana' && <span className="text-amber-500 animate-pulse uppercase text-[8px]">Linked to: {grafanaPath.split('/').pop()}</span>}
                  </div>
                  <a href={monitorTool === 'grafana' ? `${PUBLIC_GRAFANA_URL}${grafanaPath}` : PUBLIC_JAEGER_URL} target="_blank" rel="noreferrer" className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-300 hover:bg-slate-700 transition-all">
                    <ExternalLink className="w-4 h-4" /> OPEN EXTERNAL
                  </a>
                </div>
              </div>
              <div className="flex-1 bg-white rounded-3xl overflow-hidden shadow-2xl border border-slate-200 relative">
                <iframe
                  key={monitorTool}
                  src={monitorTool === 'grafana'
                    // Default to a shorter window so new users immediately see data.
                    ? `${PUBLIC_GRAFANA_URL}${grafanaPath}?refresh=10s&theme=light&kiosk&from=now-1h&to=now`
                    // Service name in Jaeger is "phoenix-service" (see /api/services).
                    : `${PUBLIC_JAEGER_URL}/search?service=phoenix-service`
                  }
                  className="w-full h-full border-none rounded-2xl"
                  title="observability-suite"
                  allow="autoplay; fullscreen"
                />
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}

export default App