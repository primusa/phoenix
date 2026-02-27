import React from 'react';
import { Brain, LayoutDashboard, Activity } from 'lucide-react';
import { Toaster } from 'react-hot-toast';

const DashboardLayout = ({ children, view, setView, isLocal }) => {
    return (
        <div className="min-h-screen bg-slate-50 text-slate-900 font-sans flex flex-col md:flex-row">
            <Toaster position="top-right" />

            {/* Responsive Navigation */}
            <nav className="fixed bottom-0 left-0 w-full h-16 bg-slate-900 flex flex-row items-center justify-around px-4 z-[100] md:fixed md:left-0 md:top-0 md:h-full md:w-20 md:flex-col md:py-8 md:gap-8 md:justify-start">
                <div className="hidden md:block p-3 bg-indigo-600 rounded-xl shadow-lg mt-0 md:mt-0">
                    <Brain className="w-8 h-8 text-white" />
                </div>
                <button
                    onClick={() => setView('app')}
                    className={`p-3 rounded-xl transition-all ${view === 'app' ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'text-slate-500 hover:text-white'}`}
                >
                    <LayoutDashboard className="w-6 h-6 md:w-6 md:h-6" />
                </button>
                {isLocal && (
                    <button
                        onClick={() => setView('monitoring')}
                        className={`p-3 rounded-xl transition-all ${view === 'monitoring' ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'text-slate-500 hover:text-white'}`}
                    >
                        <Activity className="w-6 h-6 md:w-6 md:h-6" />
                    </button>
                )}
            </nav>

            {/* Main Content Area */}
            <main className="flex-1 p-4 pb-20 md:pl-28 md:p-8 flex flex-col min-h-screen">
                <div className="max-w-7xl mx-auto w-full flex-1 flex flex-col space-y-6">
                    {children}
                </div>
            </main>
        </div>
    );
};

export default DashboardLayout;
