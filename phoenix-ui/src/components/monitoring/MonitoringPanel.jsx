import React, { useState } from 'react';
import { BarChart3, Activity, ExternalLink, Box } from 'lucide-react';
import { PUBLIC_JAEGER_URL, GRAFANA_URL } from '../../services/api';

const MonitoringPanel = ({ grafanaPath }) => {
    const [monitorTool, setMonitorTool] = useState('grafana');

    const publicGrafanaUrl = GRAFANA_URL;

    return (
        <div className="flex flex-col gap-4 h-full animate-in fade-in duration-500 min-h-0">
            <div className="bg-slate-900 p-4 rounded-2xl flex flex-col md:flex-row items-center justify-between shrink-0 shadow-xl border border-slate-800 gap-4">
                <div className="flex gap-2 w-full md:w-auto">
                    <button
                        onClick={() => setMonitorTool('grafana')}
                        className={`flex-1 md:flex-none flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-[10px] md:text-xs font-bold transition-all ${monitorTool === 'grafana' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30' : 'bg-slate-800 text-slate-400'}`}
                    >
                        <BarChart3 className="w-4 h-4" /> METRICS
                    </button>
                    <button
                        onClick={() => setMonitorTool('jaeger')}
                        className={`flex-1 md:flex-none flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-[10px] md:text-xs font-bold transition-all ${monitorTool === 'jaeger' ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30' : 'bg-slate-800 text-slate-400'}`}
                    >
                        <Activity className="w-4 h-4" /> TRACES
                    </button>
                </div>
                <div className="flex gap-3 w-full md:w-auto justify-between md:justify-end">
                    <div className="hidden lg:flex items-center gap-4 mr-4 text-[10px] font-mono text-slate-500">
                        <span className="flex items-center gap-1"><Box className="w-3 h-3" /> NODE_01: ACTIVE</span>
                    </div>
                    <a
                        href={monitorTool === 'grafana' ? `${publicGrafanaUrl}${grafanaPath}` : PUBLIC_JAEGER_URL}
                        target="_blank"
                        rel="noreferrer"
                        className="flex items-center gap-2 px-4 py-2 rounded-xl text-[10px] md:text-xs font-bold bg-slate-800 text-slate-300 hover:bg-slate-700 transition-all"
                    >
                        <ExternalLink className="w-4 h-4" /> OPEN EXTERNAL
                    </a>
                </div>
            </div>
            <div className="flex-1 bg-white rounded-3xl overflow-hidden shadow-2xl border border-slate-200 relative">
                <iframe
                    key={monitorTool}
                    src={monitorTool === 'grafana'
                        ? `${publicGrafanaUrl}${grafanaPath}?refresh=10s&theme=light&kiosk&from=now-1h&to=now`
                        : `${PUBLIC_JAEGER_URL}/search?service=phoenix-service`
                    }
                    className="w-full h-full border-none rounded-2xl"
                    title="observability-suite"
                    allow="autoplay; fullscreen"
                />
            </div>
        </div>
    );
};

export default MonitoringPanel;
