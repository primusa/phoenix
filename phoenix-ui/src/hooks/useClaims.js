import { useState, useEffect, useCallback, useRef } from 'react';
import toast from 'react-hot-toast';
import { claimsApi } from '../services/api';

export const useClaims = () => {
    const [claims, setClaims] = useState([]);
    const [loading, setLoading] = useState(false);
    const [pipelineStep, setPipelineStep] = useState(0);
    const [logs, setLogs] = useState([{
        id: 1,
        time: new Date().toLocaleTimeString(),
        msg: "SYSTEM: Bridge Protocol Online. Listening for Postgres WAL events...",
        type: "system"
    }]);

    const addLog = useCallback((msg, type = "info") => {
        setLogs(prev => [{ id: Date.now(), time: new Date().toLocaleTimeString(), msg, type }, ...prev].slice(0, 50));
    }, []);

    const fetchClaims = useCallback(async (isSilent = false) => {
        if (!isSilent) setLoading(true);
        try {
            const res = await claimsApi.getClaims();
            // Ensure we match backend CamelCase DTO
            const enriched = res.data.map(c => ({
                ...c,
                traceId: c.traceId || `tr-${Math.random().toString(36).substr(2, 9)}`
            }));
            setClaims(enriched);
            if (!isSilent) toast.success('Vector Core Synchronized');
            return enriched.length;
        } catch (err) {
            if (!isSilent) toast.error('Sync failed: DB Connection Refused');
            return 0;
        } finally {
            if (!isSilent) setLoading(false);
        }
    }, []);

    // Polling for updates
    useEffect(() => {
        const timer = setInterval(() => fetchClaims(true), 3000);
        return () => clearInterval(timer);
    }, [fetchClaims]);

    // Pipeline Orchestration
    useEffect(() => {
        if (!claims || claims.length === 0) return;
        const latest = claims[0];
        const isRecentlyCreated = (new Date().getTime() - new Date(latest.createdAt).getTime()) < 120000;

        if (isRecentlyCreated) {
            if (!latest.summary) {
                setPipelineStep(2);
            } else if (latest.fraudScore === -1 || latest.fraudScore === null) {
                // -1 mapping check: if fraudScore is null or hasn't been set yet
                setPipelineStep(4);
            } else if (latest.fraudScore >= 0) {
                setPipelineStep(5);
                const timer = setTimeout(() => setPipelineStep(0), 8000);
                return () => clearTimeout(timer);
            }
        } else if (pipelineStep !== 0) {
            setPipelineStep(0);
        }
    }, [claims, pipelineStep]);

    const createClaim = async (data) => {
        setPipelineStep(1);
        addLog(`PG_SOURCE: Transaction committed to public.claims`, "db");
        try {
            await claimsApi.createClaim(data);
            addLog(`SYSTEM: CDC Pipeline Triggered for new record.`, "system");
            fetchClaims(true);
        } catch (err) {
            setPipelineStep(0);
            toast.error('Pipeline Interrupted');
        }
    };

    return {
        claims,
        loading,
        pipelineStep,
        logs,
        addLog,
        fetchClaims,
        createClaim
    };
};
