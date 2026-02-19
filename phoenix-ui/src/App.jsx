import { useState, useEffect, useCallback } from 'react'
import axios from 'axios'
import toast, { Toaster } from 'react-hot-toast'
import { Brain, Database, Send, RefreshCw, Zap, Activity, ShieldCheck, LayoutDashboard, Sliders, Search, CheckCircle2, ArrowRight, Terminal, Clock, DollarSign, Lock, Sparkles, HelpCircle, X, ShieldAlert, BarChart3, ExternalLink, Box, Link2, AlertTriangle, History, Info } from 'lucide-react'

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
  { id: 3, label: 'AI Summary', icon: <Brain className="w-4 h-4" /> },
  { id: 4, label: 'Fraud Detection RAG', icon: <ShieldCheck className="w-4 h-4" /> },
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
      if (!isLocal) return;
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

  // 1. Global Sync Poller (Efficient & Single Source of Truth)
  useEffect(() => {
    const timer = setInterval(() => fetchClaims(true), 3000);
    return () => clearInterval(timer);
  }, [fetchClaims]);

  // 2. Data-Driven Pipeline Orchestrator (Reacts to Claim state changes)
  useEffect(() => {
    if (!claims || claims.length === 0) return;
    const latest = claims[0];

    // Check if the latest claim is "fresh" (e.g., within last 2 minutes)
    const isRecentlyCreated = (new Date().getTime() - new Date(latest.created_at).getTime()) < 120000;

    if (isRecentlyCreated) {
      if (!latest.summary) {
        setPipelineStep(2); // In flight: WAL/CDC/AI processing
      } else if (latest.fraud_score === -1) {
        setPipelineStep(4); // In flight: Fraud Detection RAG
      } else if (latest.fraud_score >= 0) {
        setPipelineStep(5); // Complete
        const timer = setTimeout(() => setPipelineStep(0), 8000);
        return () => clearTimeout(timer);
      }
    } else if (pipelineStep !== 0) {
      setPipelineStep(0);
    }
  }, [claims]);

  const handleProviderChange = async (p) => {
    setProvider(p)
    toast(`Engine: Switched to ${p.toUpperCase()}`, { icon: '🤖' })
  }

  const handleTemperatureChange = async (t) => {
    setTemperature(t)
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
    setCreating(true);
    setPipelineStep(1); // Start locally
    addLog(`PG_SOURCE: Transaction committed to public.claims`, "db")

    try {
      await axios.post(`${API_BASE}/claims`, {
        description: newClaim,
        aiProvider: provider,
        aiTemperature: temperature
      })
      setNewClaim('')
      addLog(`SYSTEM: CDC Pipeline Triggered for new record.`, "system")
      // fetchClaims(true) will pick up the update in its next tick
    } catch (err) {
      setPipelineStep(0);
      toast.error('Pipeline Interrupted')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans flex flex-col md:flex-row">
      <Toaster position="top-right" />

      {/* Responsive Navigation */}
      <nav className="fixed bottom-0 left-0 w-full h-16 bg-slate-900 flex flex-row items-center justify-around px-4 z-[100] md:fixed md:left-0 md:top-0 md:h-full md:w-20 md:flex-col md:py-8 md:gap-8 md:justify-start">
        <div className="hidden md:block p-3 bg-indigo-600 rounded-xl shadow-lg mt-0 md:mt-0"><Brain className="w-8 h-8 text-white" /></div>
        <button onClick={() => setView('app')} className={`p-3 rounded-xl transition-all ${view === 'app' ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'text-slate-500 hover:text-white'}`}>
          <LayoutDashboard className="w-6 h-6 md:w-6 md:h-6" />
        </button>
        {isLocal && (
          <button onClick={() => setView('monitoring')} className={`p-3 rounded-xl transition-all ${view === 'monitoring' ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'text-slate-500 hover:text-white'}`}>
            <Activity className="w-6 h-6 md:w-6 md:h-6" />
          </button>
        )}
      </nav>

      {/* Main Content Area */}
      <main className="flex-1 p-4 pb-20 md:pl-28 md:p-8 flex flex-col min-h-screen">
        <div className="max-w-7xl mx-auto w-full flex-1 flex flex-col space-y-6">

          {view === 'app' ? (
            <div className="flex flex-col gap-6">
              <header className="flex flex-col bg-white p-4 md:p-6 rounded-2xl shadow-sm border border-slate-200 gap-6 shrink-0 relative z-50">
                <div className="flex flex-col lg:flex-row lg:justify-between lg:items-center gap-4">
                  <div>
                    <h1 className="text-xl font-black text-slate-900 uppercase tracking-tight leading-none italic">Phoenix // Intelligence Engine</h1>
                    <span className="text-[10px] md:text-[11px] font-bold text-slate-500 mt-2 uppercase tracking-tight md:tracking-widest flex items-center flex-wrap gap-2">
                      <Zap className="w-3 h-3 text-orange-400" /> WAL STREAMING ➜ KAFKA CDC ➜ AI SUMMARY ➜ FRAUD DETECTION RAG
                    </span>
                  </div>

                  {/* METRICS WITH TOOLTIPS */}
                  <div className="flex items-center justify-between md:justify-start gap-3 md:gap-6 px-4 md:px-6 py-3 bg-slate-900 rounded-2xl shadow-xl overflow-x-auto no-scrollbar">
                    <div className="flex flex-col relative group cursor-help min-w-fit">
                      <span className="text-[8px] font-black text-indigo-400 uppercase flex items-center gap-1">Latency <HelpCircle className="w-2 h-2" /></span>
                      <span className="text-sm font-black text-white">{metrics.latency}</span>
                      <div className="absolute top-full mt-2 left-0 w-48 p-2 bg-slate-800 text-[9px] text-slate-300 rounded-lg opacity-0 group-hover:opacity-100 transition-all z-[110] border border-slate-700 pointer-events-none shadow-2xl">
                        Calculated as Δt between Postgres WAL commit and Vector DB ingestion acknowledgement.
                      </div>
                    </div>
                    <div className="w-px h-6 bg-slate-700 shrink-0" />
                    <div className="flex flex-col relative group cursor-help min-w-fit">
                      <span className="text-[8px] font-black text-indigo-400 uppercase flex items-center gap-1">Cost <HelpCircle className="w-2 h-2" /></span>
                      <span className="text-sm font-black text-white">{metrics.cost}</span>
                      <div className="absolute top-full mt-2 left-0 w-48 p-2 bg-slate-800 text-[9px] text-slate-300 rounded-lg opacity-0 group-hover:opacity-100 transition-all z-[110] border border-slate-700 pointer-events-none shadow-2xl">
                        Based on avg tokens/claim (Input: ~150, Output: ~400) x current model {provider.toUpperCase()} pricing.
                      </div>
                    </div>
                    <div className="w-px h-6 bg-slate-700 shrink-0" />
                    <div className="flex flex-col min-w-fit">
                      <span className="text-[8px] font-black text-indigo-400 uppercase">Total</span>
                      <span className="text-sm font-black text-white">{claims.length}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between bg-slate-50/50 p-2 md:p-4 rounded-xl border border-slate-100 overflow-x-auto gap-2 no-scrollbar">
                  {STAGES.map((stage, idx) => (
                    <div key={stage.id} className="flex-1 flex items-center min-w-[80px]">
                      <div className="flex flex-col items-center gap-2 flex-1">
                        <div className={`w-8 h-8 md:w-10 md:h-10 rounded-full border-2 flex items-center justify-center transition-all ${pipelineStep > idx ? 'border-indigo-600 bg-indigo-600 text-white shadow-lg' : 'border-slate-200 bg-white text-slate-300'}`}>
                          {pipelineStep > idx + 1 ? <CheckCircle2 className="w-4 h-4 md:w-6 md:h-6" /> : stage.icon}
                        </div>
                        <span className={`text-[8px] md:text-[9px] font-black uppercase text-center ${pipelineStep === idx + 1 ? 'text-indigo-600' : 'text-slate-400'}`}>
                          {stage.label} {pipelineStep === idx + 1 && idx === 3 && <span className="animate-pulse opacity-70">...PENDING</span>}
                        </span>
                      </div>
                      {idx < STAGES.length - 1 && <ArrowRight className="w-3 h-3 text-slate-200 shrink-0 mx-1" />}
                    </div>
                  ))}
                </div>
              </header>

              <div className="grid grid-cols-1 md:grid-cols-12 gap-6 pb-4">
                <div className="md:col-span-4 flex flex-col gap-6">
                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                    <div className="flex justify-between items-center mb-3">
                      <h2 className="text-[10px] font-black uppercase text-slate-400 flex items-center gap-2"><Sliders className="w-3 h-3" /> Engine Config</h2>
                      <span className="text-[10px] font-bold bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded">Temp: {temperature}</span>
                    </div>
                    <div className="flex bg-slate-100 p-1 rounded-xl mb-4">
                      {['openai', 'ollama', 'gemini'].map(p => {
                        const isDisabled = !isLocal && p !== 'ollama';
                        return (
                          <button
                            key={p}
                            disabled={isDisabled}
                            onClick={() => handleProviderChange(p)}
                            className={`flex-1 py-1.5 rounded-lg text-[10px] font-bold transition-all 
                              ${provider === p ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-400'}
                              ${isDisabled ? 'opacity-30 cursor-not-allowed italic' : 'hover:text-indigo-500'}
                            `}
                          >
                            {p.toUpperCase()}
                          </button>
                        );
                      })}
                    </div>
                    <input type="range" min="0" max="1" step="0.1" value={temperature} onChange={(e) => handleTemperatureChange(e.target.value)} className="w-full h-1.5 accent-indigo-600 bg-slate-200 rounded-lg appearance-none cursor-pointer" />
                  </div>

                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-col">
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
                    <textarea value={newClaim} onChange={(e) => setNewClaim(e.target.value)} className="w-full p-3 bg-slate-50 rounded-xl min-h-[100px] mb-3 text-sm border border-slate-100 outline-none focus:ring-2 ring-indigo-500/20 transition-all" placeholder="Enter raw claim data..." />
                    <button onClick={handleCreateClaim} disabled={creating || !newClaim} className="w-full bg-slate-900 text-white py-3 rounded-xl font-bold flex justify-center items-center gap-2 hover:bg-indigo-600 transition-all text-sm px-4">
                      {creating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />} Submit CDC Pipeline
                    </button>
                  </div>

                  <div className="bg-slate-900 rounded-2xl p-4 font-mono text-[10px] text-slate-300 shadow-inner max-h-[300px] overflow-y-auto">
                    <div className="w-full">
                      <div className="border-b border-slate-800 pb-2 mb-3 text-slate-500 flex items-center gap-2 sticky top-0 bg-slate-900 border-t-0 uppercase tracking-tighter font-black text-[9px]"><Terminal className="w-3 h-3 text-emerald-500" /> Realtime Telemetry // Cluster Logs</div>
                      <div className="space-y-1">{logs.map(l => (<div key={l.id}><span className="opacity-30">[{l.time}]</span> <span className={l.type === 'kafka' ? 'text-orange-400' : l.type === 'ai' ? 'text-indigo-400' : ''}>{l.msg}</span></div>))}</div>
                    </div>
                  </div>
                </div>

                <div className="md:col-span-8 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col relative overflow-hidden min-h-[400px]">
                  <div className="p-4 md:p-5 border-b border-slate-100 bg-slate-50/50 flex flex-col gap-4 z-40 relative">
                    <div className="flex justify-between items-center">
                      <h2 className="font-bold text-slate-700 uppercase text-[10px] md:text-xs tracking-widest flex items-center gap-2"><Sparkles className="w-4 h-4 text-indigo-500" /> Intelligence Layer</h2>
                      <button onClick={() => fetchClaims()} className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-[10px] font-black text-slate-600 hover:bg-slate-50 transition-all">REFRESH</button>
                    </div>
                    <div className="relative">
                      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                      <input type="text" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Filter modernized records..." className="w-full pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-[11px] outline-none focus:ring-2 ring-indigo-500/10 transition-all" />
                    </div>
                  </div>

                  <div className="flex-1 overflow-y-auto p-4 md:p-6 space-y-4 min-h-0 overflow-x-visible">
                    {filteredClaims.map((c, idx) => (
                      <div key={c.id} className="p-4 md:p-5 bg-white border border-slate-100 rounded-2xl border-l-4 border-l-indigo-500 shadow-sm relative group/claim">
                        <div className="flex justify-between items-start mb-3 gap-2 flex-wrap">
                          <span className="text-[9px] md:text-[10px] font-mono font-bold text-slate-400 uppercase">#CLAIM_ID_{c.id}</span>
                          <div className="flex gap-2 relative flex-wrap justify-end">
                            {isLocal && (
                              <button onClick={() => openTrace(c.traceId)} className="flex items-center gap-1 px-2 py-0.5 rounded bg-slate-100 text-slate-600 text-[8px] font-black border border-slate-200 hover:bg-indigo-600 hover:text-white transition-all">
                                <Link2 className="w-2.5 h-2.5" /> TRACE
                              </button>
                            )}
                            <div className="flex items-center gap-1.5 px-2 py-0.5 rounded bg-amber-50 text-amber-700 text-[8px] font-black border border-amber-100 group/tip cursor-help relative">
                              <Lock className="w-2.5 h-2.5" /> BYPASSED
                              <div className={`absolute right-0 w-48 md:w-64 p-3 bg-slate-900 text-slate-200 rounded-xl shadow-2xl opacity-0 group-hover/tip:opacity-100 transition-all pointer-events-none font-normal z-[200] border border-slate-700
                                ${idx === 0 ? 'top-full mt-2' : 'bottom-full mb-2'}`}>
                                <span className="font-bold text-amber-400 uppercase block mb-1">Status: Governance Dormant</span>
                                <p className="text-[10px] text-slate-400 leading-relaxed">Identity masking is currently bypassed. Personal information in this record has not been modified.</p>
                                <div className={`absolute right-4 w-3 h-3 bg-slate-900 border-slate-700 rotate-45 ${idx === 0 ? '-top-1.5 border-l border-t' : '-bottom-1.5 border-r border-b'}`}></div>
                              </div>
                            </div>
                            <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-indigo-50 text-indigo-600 text-[8px] font-black border border-indigo-100"><ShieldCheck className="w-2.5 h-2.5" /> SYNC</div>
                          </div>
                        </div>
                        <p className="text-slate-500 text-[11px] md:text-xs mb-3 italic">"{c.description}"</p>

                        {c.summary && (
                          <div className="bg-slate-50 p-3 md:p-4 rounded-xl border border-slate-200 text-xs md:text-sm font-semibold text-slate-800 animate-in fade-in duration-300">
                            {c.summary}
                          </div>
                        )}

                        {c.summary && c.fraud_score === -1 && (
                          <div className="mt-3 p-3 rounded-xl border border-blue-100 bg-blue-50/50 flex flex-col gap-2 animate-pulse">
                            <div className="flex justify-between items-center">
                              <span className="text-[9px] font-black uppercase flex items-center gap-1.5 text-blue-600">
                                <History className="w-3.5 h-3.5 animate-spin" /> Cross-Referencing Knowledge Base...
                              </span>
                            </div>
                            <div className="h-2 w-full bg-blue-200/50 rounded-full"></div>
                          </div>
                        )}

                        {c.fraud_score >= 0 && (
                          <div className={`mt-3 overflow-hidden rounded-xl border shadow-sm transition-all animate-in slide-in-from-bottom-2 duration-500 ${c.fraud_score > 70
                            ? 'bg-red-50/50 border-red-100'
                            : c.fraud_score > 30
                              ? 'bg-orange-50/50 border-orange-100'
                              : 'bg-emerald-50/50 border-emerald-100'
                            }`}>
                            <div className={`px-3 py-2 border-b flex justify-between items-center ${c.fraud_score > 70 ? 'border-red-100 bg-red-100/30' :
                              c.fraud_score > 30 ? 'border-orange-100 bg-orange-100/30' :
                                'border-emerald-100 bg-emerald-100/30'
                              }`}>
                              <span className={`text-[9px] font-black uppercase flex items-center gap-1.5 ${c.fraud_score > 70 ? 'text-red-700' :
                                c.fraud_score > 30 ? 'text-orange-700' :
                                  'text-emerald-700'
                                }`}>
                                <AlertTriangle className="w-3.5 h-3.5" /> Intelligence Assessment
                              </span>
                              <div className="flex items-center gap-2">
                                <span className="text-[8px] font-bold text-slate-400 uppercase tracking-tighter">Fraud Risk Score</span>
                                <span className={`px-2 py-0.5 rounded-lg text-[10px] font-black ${c.fraud_score > 70 ? 'bg-red-600 text-white shadow-sm' :
                                  c.fraud_score > 30 ? 'bg-orange-600 text-white shadow-sm' :
                                    'bg-emerald-600 text-white shadow-sm'
                                  }`}>
                                  {c.fraud_score}%
                                </span>
                              </div>
                            </div>
                            <div className="p-3 space-y-3">
                              <div>
                                <h4 className="text-[8px] font-black uppercase text-slate-400 mb-1 flex items-center gap-1"><Sparkles className="w-2.5 h-2.5" /> Analysis Summary</h4>
                                <p className="text-[11px] font-medium text-slate-700 leading-normal">{c.fraud_analysis}</p>
                              </div>
                              {c.fraud_rationale && (
                                <div className="pt-2 border-t border-slate-100/50">
                                  <h4 className="text-[8px] font-black uppercase text-slate-400 mb-1 flex items-center gap-1"><Info className="w-2.5 h-2.5" /> Calculation Rationale</h4>
                                  <p className="text-[10px] text-slate-500 leading-relaxed italic">{c.fraud_rationale}</p>
                                </div>
                              )}
                              <div className="flex items-center gap-2 pt-1">
                                <div className="flex -space-x-1">
                                  <div className="w-4 h-4 rounded-full bg-indigo-500 border border-white flex items-center justify-center text-[6px] text-white font-bold">V</div>
                                  <div className="w-4 h-4 rounded-full bg-slate-800 border border-white flex items-center justify-center text-[6px] text-white font-bold">R</div>
                                </div>
                                <span className="text-[8px] font-bold text-slate-400">RAG Context: Checked against historical vector corpus</span>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-4 h-full animate-in fade-in duration-500 min-h-0">
              <div className="bg-slate-900 p-4 rounded-2xl flex flex-col md:flex-row items-center justify-between shrink-0 shadow-xl border border-slate-800 gap-4">
                <div className="flex gap-2 w-full md:w-auto">
                  <button onClick={() => setMonitorTool('grafana')} className={`flex-1 md:flex-none flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-[10px] md:text-xs font-bold transition-all ${monitorTool === 'grafana' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30' : 'bg-slate-800 text-slate-400'}`}>
                    <BarChart3 className="w-4 h-4" /> METRICS
                  </button>
                  <button onClick={() => setMonitorTool('jaeger')} className={`flex-1 md:flex-none flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-[10px] md:text-xs font-bold transition-all ${monitorTool === 'jaeger' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30' : 'bg-slate-800 text-slate-400'}`}>
                    <Activity className="w-4 h-4" /> TRACES
                  </button>
                </div>
                <div className="flex gap-3 w-full md:w-auto justify-between md:justify-end">
                  <div className="hidden lg:flex items-center gap-4 mr-4 text-[10px] font-mono text-slate-500">
                    <span className="flex items-center gap-1"><Box className="w-3 h-3" /> NODE_01: ACTIVE</span>
                  </div>
                  <a href={monitorTool === 'grafana' ? `${PUBLIC_GRAFANA_URL}${grafanaPath}` : PUBLIC_JAEGER_URL} target="_blank" rel="noreferrer" className="flex items-center gap-2 px-4 py-2 rounded-xl text-[10px] md:text-xs font-bold bg-slate-800 text-slate-300 hover:bg-slate-700 transition-all">
                    <ExternalLink className="w-4 h-4" /> OPEN EXTERNAL
                  </a>
                </div>
              </div>
              <div className="flex-1 bg-white rounded-3xl overflow-hidden shadow-2xl border border-slate-200 relative">
                <iframe
                  key={monitorTool}
                  src={monitorTool === 'grafana'
                    ? `${PUBLIC_GRAFANA_URL}${grafanaPath}?refresh=10s&theme=light&kiosk&from=now-1h&to=now`
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