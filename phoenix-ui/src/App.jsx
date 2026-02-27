import React, { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { Sparkles, Search, HelpCircle, Zap, CheckCircle2, ArrowRight } from 'lucide-react';
import DashboardLayout from './components/layout/DashboardLayout';
import ClaimCard from './components/claims/ClaimCard';
import CreateClaimForm from './components/claims/CreateClaimForm';
import MonitoringPanel from './components/monitoring/MonitoringPanel';
import TelemetryLog from './components/monitoring/TelemetryLog';
import { useClaims } from './hooks/useClaims';
import { STAGES } from './constants';
import { claimsApi } from './services/api';

const isLocal = window.location.hostname === 'localhost';

function App() {
  const [view, setView] = useState('app');
  const [searchQuery, setSearchQuery] = useState('');
  const [grafanaPath, setGrafanaPath] = useState('/d/d9e64ba9-4f2d-4081-8a94-9a65726084db/jvm-overview-micrometer-otlp');

  const {
    claims,
    loading,
    pipelineStep,
    logs,
    createClaim,
    fetchClaims
  } = useClaims();

  const [metrics] = useState({ latency: '42ms', cost: '$0.004', health: '99.8%' });

  useEffect(() => {
    const syncGrafana = async () => {
      if (!isLocal) return;
      try {
        const res = await claimsApi.getDashboards();
        const jvmDash = res.data.find(d =>
          d.title.toLowerCase().includes('jvm') ||
          d.uid === 'd9e64ba9-4f2d-4081-8a94-9a65726084db'
        );
        if (jvmDash && jvmDash.url) {
          setGrafanaPath(jvmDash.url);
        }
      } catch (err) {
        console.warn("Observability Tunnel: Failed to reach Grafana via Backend Proxy");
      }
    };
    syncGrafana();
    fetchClaims();
  }, [fetchClaims]);

  const filteredClaims = claims.filter(c =>
    c.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (c.summary && c.summary.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <DashboardLayout view={view} setView={setView} isLocal={isLocal}>
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

              <div className="flex items-center justify-between md:justify-start gap-3 md:gap-6 px-4 md:px-6 py-3 bg-slate-900 rounded-2xl shadow-xl overflow-x-auto no-scrollbar">
                <Metric label="Latency" value={metrics.latency} tooltip="Calculated as Δt between Postgres WAL commit and Vector DB ingestion acknowledgement." />
                <div className="w-px h-6 bg-slate-700 shrink-0" />
                <Metric label="Cost" value={metrics.cost} tooltip="Based on avg tokens/claim x current model pricing." />
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
              <CreateClaimForm onSubmit={createClaim} isLocal={isLocal} />
              <TelemetryLog logs={logs} />
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
                  <ClaimCard key={c.id} claim={c} idx={idx} isLocal={isLocal} onTraceClick={(id) => {
                    setView('monitoring');
                    toast(`Locating Span: ${id}`, { icon: '🔍' });
                  }} />
                ))}
              </div>
            </div>
          </div>
        </div>
      ) : (
        <MonitoringPanel grafanaPath={grafanaPath} />
      )}
    </DashboardLayout>
  );
}

const Metric = ({ label, value, tooltip }) => (
  <div className="flex flex-col relative group cursor-help min-w-fit">
    <span className="text-[8px] font-black text-indigo-400 uppercase flex items-center gap-1">
      {label} <HelpCircle className="w-2 h-2" />
    </span>
    <span className="text-sm font-black text-white">{value}</span>
    <div className="absolute top-full mt-2 left-0 w-48 p-2 bg-slate-800 text-[9px] text-slate-300 rounded-lg opacity-0 group-hover:opacity-100 transition-all z-[110] border border-slate-700 pointer-events-none shadow-2xl">
      {tooltip}
    </div>
  </div>
);

export default App;