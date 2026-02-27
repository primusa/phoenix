import React from 'react';
import { Terminal } from 'lucide-react';

const TelemetryLog = ({ logs }) => {
    return (
        <div className="bg-slate-900 rounded-2xl p-4 font-mono text-[10px] text-slate-300 shadow-inner max-h-[300px] overflow-y-auto">
            <div className="w-full">
                <div className="border-b border-slate-800 pb-2 mb-3 text-slate-500 flex items-center gap-2 sticky top-0 bg-slate-900 border-t-0 uppercase tracking-tighter font-black text-[9px]">
                    <Terminal className="w-3 h-3 text-emerald-500" /> Realtime Telemetry // Cluster Logs
                </div>
                <div className="space-y-1">
                    {logs.map(l => (
                        <div key={l.id}>
                            <span className="opacity-30">[{l.time}]</span>{' '}
                            <span className={l.type === 'kafka' ? 'text-orange-400' : l.type === 'system' ? 'text-indigo-400' : ''}>
                                {l.msg}
                            </span>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default TelemetryLog;
