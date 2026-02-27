import React, { useState } from 'react';
import { Send, RefreshCw, Sliders, ShieldAlert, X } from 'lucide-react';

const CreateClaimForm = ({ onSubmit, isLocal }) => {
    const [description, setDescription] = useState('');
    const [provider, setProvider] = useState('ollama');
    const [temperature, setTemperature] = useState(0.3);
    const [showWarning, setShowWarning] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleSubmit = async () => {
        if (!description) return;
        setIsSubmitting(true);
        await onSubmit({ description, aiProvider: provider, aiTemperature: temperature });
        setDescription('');
        setIsSubmitting(false);
    };

    return (
        <div className="flex flex-col gap-6">
            <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                <div className="flex justify-between items-center mb-3">
                    <h2 className="text-[10px] font-black uppercase text-slate-400 flex items-center gap-2">
                        <Sliders className="w-3 h-3" /> Engine Config
                    </h2>
                    <span className="text-[10px] font-bold bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded">
                        Temp: {temperature}
                    </span>
                </div>
                <div className="flex bg-slate-100 p-1 rounded-xl mb-4">
                    {['openai', 'ollama', 'gemini'].map(p => {
                        const isDisabled = !isLocal && p !== 'ollama';
                        return (
                            <button
                                key={p}
                                disabled={isDisabled}
                                onClick={() => setProvider(p)}
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
                <input
                    type="range" min="0" max="1" step="0.1"
                    value={temperature}
                    onChange={(e) => setTemperature(parseFloat(e.target.value))}
                    className="w-full h-1.5 accent-indigo-600 bg-slate-200 rounded-lg appearance-none cursor-pointer"
                />
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
                <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    className="w-full p-3 bg-slate-50 rounded-xl min-h-[100px] mb-3 text-sm border border-slate-100 outline-none focus:ring-2 ring-indigo-500/20 transition-all"
                    placeholder="Enter raw claim data..."
                />
                <button
                    onClick={handleSubmit}
                    disabled={isSubmitting || !description}
                    className="w-full bg-slate-900 text-white py-3 rounded-xl font-bold flex justify-center items-center gap-2 hover:bg-indigo-600 transition-all text-sm px-4"
                >
                    {isSubmitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />} Submit CDC Pipeline
                </button>
            </div>
        </div>
    );
};

export default CreateClaimForm;
