import React from 'react';
import { ShieldCheck, Lock, Link2, AlertTriangle, Sparkles, Brain, Info, ShieldAlert } from 'lucide-react';

const ClaimCard = ({ claim, idx, onTraceClick, isLocal }) => {
    return (
        <div className="p-4 md:p-5 bg-white border border-slate-100 rounded-2xl border-l-4 border-l-indigo-500 shadow-sm relative group/claim">
            <div className="flex justify-between items-start mb-3 gap-2 flex-wrap">
                <span className="text-[9px] md:text-[10px] font-mono font-bold text-slate-400 uppercase">#CLAIM_ID_{claim.id}</span>
                <div className="flex gap-2 relative flex-wrap justify-end">
                    {isLocal && (
                        <button onClick={() => onTraceClick(claim.traceId)} className="flex items-center gap-1 px-2 py-0.5 rounded bg-slate-100 text-slate-600 text-[8px] font-black border border-slate-200 hover:bg-indigo-600 hover:text-white transition-all">
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
            <p className="text-slate-500 text-[11px] md:text-xs mb-3 italic">"{claim.description}"</p>

            {claim.summary && (
                <div className="bg-slate-50 p-3 md:p-4 rounded-xl border border-slate-200 text-xs md:text-sm font-semibold text-slate-800 animate-in fade-in duration-300">
                    {claim.summary}
                </div>
            )}

            {claim.summary && claim.fraudScore === -1 && (
                <div className="mt-3 p-3 rounded-xl border border-blue-100 bg-blue-50/50 flex flex-col gap-2 animate-pulse">
                    <div className="flex justify-between items-center">
                        <span className="text-[9px] font-black uppercase flex items-center gap-1.5 text-blue-600">
                            <Sparkles className="w-3.5 h-3.5 animate-spin" /> Cross-Referencing Knowledge Base...
                        </span>
                    </div>
                    <div className="h-2 w-full bg-blue-200/50 rounded-full"></div>
                </div>
            )}

            {claim.fraudScore >= 0 && (
                <div className={`mt-3 overflow-hidden rounded-xl border shadow-sm transition-all animate-in slide-in-from-bottom-2 duration-500 ${claim.fraudScore > 70
                    ? 'bg-red-50/50 border-red-100'
                    : claim.fraudScore > 30
                        ? 'bg-orange-50/50 border-orange-100'
                        : 'bg-emerald-50/50 border-emerald-100'
                    }`}>
                    <div className={`px-3 py-2 border-b flex justify-between items-center ${claim.fraudScore > 70 ? 'border-red-100 bg-red-100/30' :
                        claim.fraudScore > 30 ? 'border-orange-100 bg-orange-100/30' :
                            'border-emerald-100 bg-emerald-100/30'
                        }`}>
                        <span className={`text-[9px] font-black uppercase flex items-center gap-1.5 ${claim.fraudScore > 70 ? 'text-red-700' :
                            claim.fraudScore > 30 ? 'text-orange-700' :
                                'text-emerald-700'
                            }`}>
                            <AlertTriangle className="w-3.5 h-3.5" /> Intelligence Assessment
                        </span>
                        <div className="flex items-center gap-2">
                            <span className="text-[8px] font-bold text-slate-400 uppercase tracking-tighter">Fraud Risk Score</span>
                            <span className={`px-2 py-0.5 rounded-lg text-[10px] font-black ${claim.fraudScore > 70 ? 'bg-red-600 text-white shadow-sm' :
                                claim.fraudScore > 30 ? 'bg-orange-600 text-white shadow-sm' :
                                    'bg-emerald-600 text-white shadow-sm'
                                }`}>
                                {claim.fraudScore}%
                            </span>
                        </div>
                    </div>
                    <div className="p-3 space-y-3">
                        <div>
                            <h4 className="text-[8px] font-black uppercase text-slate-400 mb-1 flex items-center gap-1"><Sparkles className="w-2.5 h-2.5" /> Analysis Summary</h4>
                            <p className="text-[11px] font-medium text-slate-700 leading-normal">{claim.fraudAnalysis}</p>
                        </div>
                        {claim.fraudThought && (
                            <div className="pt-2 border-t border-slate-100/50">
                                <h4 className="text-[8px] font-black uppercase text-indigo-400 mb-1 flex items-center gap-1"><Brain className="w-2.5 h-2.5" /> Agent Chain of Thought</h4>
                                <p className="text-[10px] text-indigo-900/70 leading-relaxed italic">{claim.fraudThought}</p>
                            </div>
                        )}
                        {claim.fraudRationale && (
                            <div className="pt-2 border-t border-slate-100/50">
                                <h4 className="text-[8px] font-black uppercase text-slate-400 mb-1 flex items-center gap-1"><Info className="w-2.5 h-2.5" /> Calculation Rationale</h4>
                                <p className="text-[10px] text-slate-500 leading-relaxed italic">{claim.fraudRationale}</p>
                            </div>
                        )}
                        <div className="flex items-center gap-2 pt-1">
                            <div className="flex -space-x-1">
                                <div className="w-4 h-4 rounded-full bg-indigo-500 border border-white flex items-center justify-center text-[6px] text-white font-bold">V</div>
                                <div className="w-4 h-4 rounded-full bg-slate-800 border border-white flex items-center justify-center text-[6px] text-white font-bold">R</div>
                            </div>
                            <span className="text-[8px] font-bold text-slate-400">Agentic RAG: Autonomous tool usage & historical search</span>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ClaimCard;
