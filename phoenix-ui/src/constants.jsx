import { Database, Zap, Brain, ShieldCheck } from 'lucide-react';

export const STAGES = [
    { id: 1, label: 'Postgres WAL', icon: <Database className="w-4 h-4" /> },
    { id: 2, label: 'Kafka Stream', icon: <Zap className="w-4 h-4" /> },
    { id: 3, label: 'AI Summary', icon: <Brain className="w-4 h-4" /> },
    { id: 4, label: 'Agentic RAG Reasoning', icon: <ShieldCheck className="w-4 h-4" /> },
];
